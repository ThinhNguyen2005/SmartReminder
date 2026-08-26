# Routine Editor D2 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: use `subagent-driven-development` or `executing-plans`. Every task uses a checkbox and follows Red -> Green -> Refactor. Do not commit or push as part of this plan.

## 1. Goal

Add a full-screen Create / Edit Routine flow inside Schedules. It saves a complete Room snapshot, preserves every field outside D2's editable scope, and prevents unsaved work from being lost. Bottom navigation and all non-Schedules features remain unchanged.

## 2. Architecture decisions verified against source

- `RoutineRepository` already exposes `getRoutineDetails(RoutineId)` and `upsertRoutine(Routine, List<RoutineItem>)`. `RoutineDao.upsertRoutineWithDetails` atomically replaces weekly days and items, so D2 must always submit the complete final list. No repository, DAO, entity, or migration change is required.
- `ScheduleGroupRepository.observeGroups()` returns active groups. An edited routine whose group is absent from that first active-group emission is normalized to Ungrouped (`null`) in both the draft and its initial snapshot.
- There is no ID-generation mechanism. Add a minimal injected `RoutineEditorIdGenerator`: production `UuidRoutineEditorIdGenerator` uses `UUID.randomUUID()`; deterministic fake IDs exist only in tests.
- Navigation Compose is intentionally not added. `SmartReminderApp` creates one `rememberSaveableStateHolder()` and wraps each bottom-tab body in `SaveableStateProvider(currentDestination)`. This retains the Schedules subtree's destination and entry token when the user switches tabs. `SchedulesHost` owns the internal `List | Create | Edit(RoutineId)` destination and passes its private entry token to one editor ViewModel: a new token resets the draft after returning to List, while the same token avoids a reset on recreation or a tab round-trip.
- `RoutineEditorViewModel` owns the current editable draft, private initial editable snapshot, hidden original metadata, validation, save state, and value-based dirty comparison. Composables receive semantic UI models, actions, and typed errors only.
- The timeline editor is one Material 3 `AlertDialog` with separate `RoutineItemEditorUiState`. It uses the already available Compose Material 3 `TimePicker`; the parent draft changes only after Confirm.
- On Edit, construct the save aggregate from the original `Routine`: retain `id`, `createdAt`, `description`, `iconKey`, `colorKey`, and `sortOrder`; set `updatedAt = clock.instant()`. `RoutineEditorViewModel` and its factory receive a `java.time.Clock`; production supplies `Clock.systemUTC()` and tests use `Clock.fixed(...)`. Existing items retain ID, duration, and enabled state. Save positions as `sortOrder = index`.
- `SchedulesRoute`, `SchedulesScreen`, `SchedulesViewModel`, `SchedulesViewModelFactory`, `AppContainer`, Room, existing repositories, and Gradle dependencies are sufficient and remain unchanged.

## 3. Tech stack and constraints

- Kotlin, Compose Material 3, Lifecycle ViewModel, Room, Coroutines/Flow, JUnit4, and `kotlinx-coroutines-test`; no dependency is added.
- Follow `CODING_STANDARDS.md`: UDF; stateless Screen/Route/ViewModel/Factory files; `Scaffold`; vertical scroll, IME and navigation-bar safety; `CueSpacing`; Material/Cue tokens; >=48dp targets; accessible labels; EN/VI resources; and previews.
- D2 may edit only name, active/ungrouped group, weekly days, routine enabled, and timeline title/time. It must preserve non-exposed routine/item metadata.
- No autosave, AI conflict logic, task/completion/progress/calendar/reminder UI, group management, deep navigation graph, schema change, commit, or push.

## 4. Exact files

### Create

- `app/src/main/java/com/smartreminder/ui/schedules/SchedulesHost.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorIdGenerator.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorUiState.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorAction.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorEffect.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorViewModel.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorViewModelFactory.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorRoute.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/RoutineEditorScreen.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/components/WeekdaySelector.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/components/GroupSelector.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/components/RoutineTimelineEditor.kt`
- `app/src/main/java/com/smartreminder/ui/schedules/editor/components/RoutineItemEditorDialog.kt`
- `app/src/test/java/com/smartreminder/ui/schedules/editor/RoutineEditorViewModelTest.kt`
- `app/src/test/java/com/smartreminder/ui/schedules/editor/RoutineEditorViewModelFactoryTest.kt`

### Modify

- `app/src/main/java/com/smartreminder/MainActivity.kt`: add one app-shell `rememberSaveableStateHolder()` / per-tab `SaveableStateProvider`, then replace only the production Schedules branch's no-op list callbacks with `SchedulesHost`, passing the group/routine repositories, `UuidRoutineEditorIdGenerator`, and `Clock.systemUTC()`.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-vi/strings.xml`: matched `routine_editor_*` strings for labels, typed errors, item editor, discard confirmation, and accessibility.

### Deliberately unchanged

`Routine`, `RoutineItem`, `RecurrenceRule`, `RoutineDetails`, repository interfaces, Room DAO/repositories, `AppContainer`, list-feature classes, Gradle, and non-Schedules UI.

## 5. Interfaces consumed and produced

| Boundary | Contract |
|---|---|
| `RoutineRepository` | Read with `getRoutineDetails(id)`; valid save calls `upsertRoutine(updatedRoutine, completeItems)` once. |
| `ScheduleGroupRepository` | Read active groups from `observeGroups()`; do not introduce group management. |
| `RoutineEditorIdGenerator` | Produce nonblank `RoutineId` / `RoutineItemId`; inject through the factory and fake in tests. |
| `RoutineEditorViewModel` | Consume mode plus entry token and an injected `Clock`; expose `StateFlow<RoutineEditorUiState>` and one-shot `Flow<RoutineEditorEffect>`. |
| `RoutineEditorAction` | Change name/group/day/enabled; item dialog actions; save/back/discard/error; host-only initialize/exit actions. |
| `RoutineEditorEffect` | `Saved` and `NavigateBack` only. Effects are not state. |
| `SchedulesHost` | Wire existing list Create/Open callbacks to destinations and map editor effects back to List without knowing the bottom navigation shell; its saveable state is retained by the app shell per bottom tab. |

## 6. TDD tasks

Every named behavior is written first. Run its focused test and confirm Red, add only the minimum production code for Green, rerun, then refactor while keeping that test green. Do not batch all production code before Red.

- [ ] **Preflight — synchronize before any D2 test or code.** Run `git status`, `git branch --show-current`, `git fetch origin`, and `git log -1 --oneline origin/main`. If working from `feature/schedules`, integrate `origin/main` using the team's agreed merge/rebase workflow. Run a fresh baseline `.\gradlew.bat test --rerun-tasks`; proceed only with zero failures. Record the baseline total as information, never as an acceptance quota.
- [ ] **State, errors, and ID boundary.** Red: create the first editor test with deterministic fake IDs and run `.\gradlew.bat :app:testDebugUnitTest --tests "com.smartreminder.ui.schedules.editor.RoutineEditorViewModelTest"`; it must fail because the contract is absent. Green: add semantic UI state, mode, action/effect, typed errors, item-dialog state, and UUID generator. Refactor: keep original aggregates and initial snapshot private to the ViewModel.
- [ ] **Initialize Create/Edit and group normalization.** Red: add tests for routine/item hydration, groups load, missing/archived group -> Ungrouped, and missing routine -> `LOAD_FAILED`. Green: implement idempotent `initialize(mode, entryToken)`, one active-group collection, and detail load. Refactor: only normalize after the first group emission and map failures to typed errors.
- [ ] **Create validation and single snapshot save.** Red: add blank/whitespace name, no-day, valid save, ID, child ownership, sort order, Saved, SAVE_FAILED, and double-save tests. Green: validate before creating `Routine` / `Weekly`; set `isSaving` synchronously; use injected IDs; build a complete list; call repository once. Refactor: clear only the relevant field error and always clear saving state.
- [ ] **Edit preservation and item draft operations.** Red: add all hidden routine/item preservation, fixed-clock `updatedAt`, add/remove/edit/untouched item, and item-dialog tests. Green: inject `Clock` through the factory, map drafts back through the original aggregate, and set `updatedAt = clock.instant()`; Confirm is the only action that changes the parent item list. Refactor: centralize draft-to-domain mapping and always save the complete list.
- [ ] **Dirty comparison and discard.** Red: add restore-to-initial and every Back/discard behavior test. Green: compare initial and current editable snapshots, not a one-way boolean. Refactor: centralize clean/dirty Back handling; transient item-dialog changes do not dirty the parent until Confirm.
- [ ] **Factory and Schedules internal host.** Red: add supported/unsupported factory tests. Green: inject repositories, generator, and clock via factory; create `SchedulesHost` with a saveable destination/entry token and map Saved/NavigateBack to List. Refactor: leave Manage Groups as the existing boundary and add no navigation dependency.
- [ ] **Stateless full-screen Compose editor.** Red: compile after introducing UI calls/resources. Green: add route collection, `Scaffold` top bar, Back/Save, scroll and IME safety, typed-error mapping, group/day/timeline components, item dialog/TimePicker, discard dialog, and resources. Refactor: add Create, populated Edit, validation-error, and dark previews; check labels and 48dp targets.
- [ ] **Root integration and final verification.** Red: build after only the Schedules branch uses the host. Green: add the app-shell `SaveableStateHolder`, wire existing app-container repositories plus production ID/clock dependencies, and add resource parity. Refactor: inspect diff for non-Schedules churn, then run the complete verification section last.

## 7. Required test matrix (coverage, not a quota)

`RoutineEditorViewModelTest.kt` covers the following 46 behavior conditions. Closely related preservation assertions may share one strongly arranged test (for example, `editSave_preservesAllNonEditableRoutineMetadata`); each row remains mandatory coverage, not a required number of test functions.

| Area | Behaviors | Count |
|---|---|---:|
| Create | blank name; whitespace name; no day; valid upsert; nonblank routine ID; child routine IDs; item orders; Saved; SAVE_FAILED; double Save writes once | 10 |
| Edit | routine/items hydrate; preserve routine ID, createdAt, description, iconKey, colorKey, sortOrder; fixed-clock updatedAt; preserve item ID, duration, enabled; remove; add; edit keeps ID; untouched metadata; missing routine LOAD_FAILED | 17 |
| Groups | active load; null Ungrouped; missing/archived Ungrouped | 3 |
| Dirty/discard | initial clean; name dirty/restored; day dirty/restored; add/remove restored; item edit/restored; clean Back; dirty Back; cancel; confirm | 11 |
| Item dialog | blank title; cancel add; confirm add; edit preserves hidden metadata; remove only changes draft before Save | 5 |

`RoutineEditorViewModelFactoryTest.kt` covers supported/unsupported factory classes. A ViewModel edit-save test with `Clock.fixed(...)` proves deterministic timestamp behavior. The number of new test functions and total suite count are deliberately non-binding: the acceptance criterion is a freshly run full suite with zero failures after synchronization with latest `origin/main`.

## 8. Automated verification

Run this preflight before TDD, and repeat the full suite after the last refactor:

```powershell
git status
git branch --show-current
git fetch origin
git log -1 --oneline origin/main
.\gradlew.bat test --rerun-tasks
.\gradlew.bat :app:testDebugUnitTest --tests "com.smartreminder.ui.schedules.editor.RoutineEditorViewModelTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.smartreminder.ui.schedules.editor.RoutineEditorViewModelFactoryTest"
.\gradlew.bat compileDebugSources
.\gradlew.bat test --rerun-tasks
git diff --check
git status --short
```

Expected: focused suites, full test suite, and compilation pass; `git diff --check` is empty. The existing AGP experimental-option warning is pre-existing and must not be attributed to D2.

## 9. Manual verification checklist

- [ ] **Create:** Schedules -> FAB -> editor; enter name, days, and timeline item; Save; return to List and see the reactive card.
- [ ] **Edit:** tap a routine; verify hydration; change name/time/day; Save; see list update without leaving the Schedules tab.
- [ ] **Discard and tabs:** alter a routine; Back -> confirmation; Keep editing closes only the dialog; Discard returns List; clean Back returns immediately. While editing, switch Schedules -> Tasks -> Schedules and verify the editor, destination, and unsaved draft remain intact; changing a tab never shows the discard dialog.
- [ ] **Validation/accessibility:** blank routine/item titles show localized errors; validate EN/VI, dark mode, font scale, keyboard scrolling, TalkBack labels, and 48dp targets.
- [ ] **Persistence:** save, kill/reopen app, and verify complete routine timeline remains.
- [ ] **Preservation:** edit a seeded routine with description/icon/color and item duration/enabled; verify those hidden values remain after save.

## 10. Scope exclusions

No Liquid Glass, AI conflict logic, tasks, completion, week progress, reminder rules, calendar, drag ordering, exposed description/icon/color/duration/item-enabled fields, group-management UI, deep navigation graph, new dependency, migration, commit, or push.

## 11. Required deviation from the supplied spec

The source has no ID generator, and a direct `viewModel(key = destination)` would retain a stale draft after return-to-List because Navigation Compose is excluded. The injected two-method generator, injected standard-library `Clock`, app-shell `SaveableStateHolder`, and private saveable entry token are therefore minimal D2 additions required for deterministic tests, tab-switch retention, and correct repeat Create/Edit behavior.
