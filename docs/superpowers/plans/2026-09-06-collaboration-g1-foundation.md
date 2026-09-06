# Collaboration G1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the Collaboration Groups v1 foundation: frozen domain vocabulary and permission rules, repository/error contracts, Room cache + durable pending-command schema, Supabase schema/RLS foundation, and the approved five-tab app shell with non-functional Calendar/Groups placeholders.

**Architecture:** Collaboration is cloud-authoritative and completely separate from existing `ScheduleGroup`. G1 creates stable boundaries only: domain types and policies are pure Kotlin, Room is cache/pending-command persistence rather than authority, Supabase SQL defines collaboration tables and read-security foundations, and UI wiring only exposes the approved top-level destinations. G1 deliberately does not implement group membership flows, task mutation RPCs, offline replay, FCM, or reminder scheduling; those belong to G2-G5.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose Material 3, Room 2.7.2 via KSP 2.2.10-2.0.2, Supabase Kotlin 3.1.2, kotlinx.serialization 1.8.0, Coroutines/Flow, JUnit 4, Android instrumentation tests, minSdk 29, `java.time`.

**Spec:** `doc/COLLABORATION_GROUPS_V1_DESIGN.md`

## Global Constraints

- Start implementation from the latest `origin/main`, not from the design branch's historical base. Re-read `CODING_STANDARDS.md` before changing code.
- Keep `ScheduleGroup` and `CollaborationGroup` separate in package names, models, persistence, UI labels, and repository contracts.
- Supabase is authoritative for collaboration; Room stores cache and pending-command state only.
- No destructive Room migration. Preserve existing schedules/routines data and exported schema history.
- Use typed IDs with non-blank validation, matching existing domain style such as `RoutineId`.
- Use `Instant` for collaboration timestamps/deadlines. Do not introduce local-time deadline storage.
- G1 defines permission policy but does not rely on client policy for security; server authorization remains authoritative.
- Pending command payloads must be explicit and versioned. Do not persist arbitrary Kotlin class names or Java serialization blobs.
- Do not add WorkManager replay, FCM, notification scheduling, membership UI, task editor UI, or collaboration RPC business commands in G1.
- UI strings must exist in default English and Vietnamese resources; no hardcoded Compose text.
- All spacing/radius uses `CueSpacing`; typography uses `MaterialTheme.typography`; touch targets remain at least 48dp; major new screens/components have previews wrapped in `SmartReminderTheme`.
- Pure business logic and important state transitions follow TDD: red -> green -> refactor.
- Preflight before completion: `./gradlew compileDebugSources` and `./gradlew test`; additionally run the Room instrumentation/migration tests on an emulator or managed device before merging.
- Keep changes surgical. Do not reformat or refactor unrelated Schedules/Profile/Auth code.

---

## File Structure Locked for G1

### Domain

Create under the existing `com.smartreminder.domain` hierarchy:

- `app/src/main/java/com/smartreminder/domain/model/collaboration/ids/CollaborationGroupId.kt` — typed group ID.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/ids/GroupTaskId.kt` — typed task ID.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/ids/GroupInviteId.kt` — typed invite ID.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/ids/GroupReminderId.kt` — typed reminder ID.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/ids/UserId.kt` — collaboration/auth user identity wrapper used by this subsystem.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupRole.kt` — `OWNER`, `ADMIN`, `MEMBER`.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupInviteStatus.kt` — `PENDING`, `ACCEPTED`, `DECLINED`.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupTaskStatus.kt` — `TODO`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/CollaborationGroup.kt` — group aggregate data.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupMember.kt` — membership relation.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupInvite.kt` — invitation aggregate.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupTask.kt` — one-assignee task with absolute deadline/version.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupTaskReminder.kt` — positive relative pre-deadline offset.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupReminder.kt` — standalone reminder.
- `app/src/main/java/com/smartreminder/domain/model/collaboration/GroupReminderAudience.kt` — `Member(UserId)` or `Everyone`.
- `app/src/main/java/com/smartreminder/domain/collaboration/GroupPermissionEvaluator.kt` — pure permission policy.
- `app/src/main/java/com/smartreminder/domain/collaboration/GroupTaskPolicy.kt` — task transition/reassign/reopen/cancel/overdue rules.
- `app/src/main/java/com/smartreminder/domain/repository/CollaborationRepository.kt` — stable client-facing collaboration repository contract.
- `app/src/main/java/com/smartreminder/domain/repository/CollaborationError.kt` — typed domain/data error vocabulary.
- `app/src/main/java/com/smartreminder/domain/repository/CollaborationMutationResult.kt` — `Applied`, `Queued`, `Conflict`, `NotAuthorized`, `InvalidState`, `NetworkRequired`, `Failure`.

### Room cache / queue

- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedCollaborationGroupEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupMemberEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupInviteEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupTaskEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupTaskReminderEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupReminderEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/PendingGroupCommandEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/dao/CollaborationCacheDao.kt`
- `app/src/main/java/com/smartreminder/data/local/room/dao/PendingGroupCommandDao.kt`
- `app/src/main/java/com/smartreminder/data/local/room/migration/Migration1To2.kt`
- Modify `app/src/main/java/com/smartreminder/data/local/room/CueDatabase.kt` to version 2 and register the migration/DAOs/entities.
- Export `app/schemas/com.smartreminder.data.local.room.CueDatabase/2.json` through KSP.

### Pending command serialization boundary

- `app/src/main/java/com/smartreminder/data/local/room/model/collaboration/PendingGroupCommandType.kt`
- `app/src/main/java/com/smartreminder/data/local/room/model/collaboration/PendingGroupCommandState.kt`
- `app/src/main/java/com/smartreminder/data/local/room/model/collaboration/PendingGroupCommandPayload.kt` — sealed, `@Serializable`, schema version 1 payloads for `CreateTask`, `StartTask`, `CompleteTask`, `EditOwnTaskContent` only.
- `app/src/main/java/com/smartreminder/data/local/room/model/collaboration/PendingGroupCommandCodec.kt` — JSON encode/decode with explicit payload version.

### Supabase SQL

- `doc/supabase/collaboration_groups_v1.sql` — tables, constraints, indexes, RLS enablement, read policies, and helper predicates needed by later RPC work.

### Navigation/UI shell

- Create `app/src/main/java/com/smartreminder/ui/calendar/CalendarPlaceholderScreen.kt`.
- Create `app/src/main/java/com/smartreminder/ui/groups/GroupsPlaceholderScreen.kt`.
- Modify `app/src/main/java/com/smartreminder/MainActivity.kt` only at the app-shell destination enum/import/switch wiring.
- Modify `app/src/main/res/values/strings.xml` and `app/src/main/res/values-vi/strings.xml` for `nav_calendar` and `nav_groups`.
- Do not delete personal Task domain files. The existing Tasks placeholder can remain unused for now to avoid unrelated churn.

### Tests

- `app/src/test/java/com/smartreminder/domain/model/collaboration/CollaborationDomainModelTest.kt`
- `app/src/test/java/com/smartreminder/domain/collaboration/GroupPermissionEvaluatorTest.kt`
- `app/src/test/java/com/smartreminder/domain/collaboration/GroupTaskPolicyTest.kt`
- `app/src/test/java/com/smartreminder/data/local/room/model/collaboration/PendingGroupCommandCodecTest.kt`
- `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationRoomMigrationTest.kt`
- `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationCacheDaoTest.kt`

---

### Task 1: Add Collaboration domain IDs and immutable models

**Files:**
- Create all files listed in **Domain** above from the five ID wrappers through `GroupReminderAudience.kt`.
- Test: `app/src/test/java/com/smartreminder/domain/model/collaboration/CollaborationDomainModelTest.kt`

**Interfaces:**
- Produces typed IDs: `CollaborationGroupId`, `GroupTaskId`, `GroupInviteId`, `GroupReminderId`, `UserId`, each exposing `val value: String` and rejecting blank values.
- Produces `GroupTask` with `dueAt: Instant`, `version: Long`, exactly one `assigneeId: UserId`, and `status: GroupTaskStatus`.
- Produces `GroupTaskReminder(taskId: GroupTaskId, offsetSeconds: Long)` with `offsetSeconds > 0`.
- Produces `GroupReminderAudience.Member(userId: UserId)` and `GroupReminderAudience.Everyone`.

- [ ] **Step 1: Write failing model invariant tests**

Cover at minimum: blank IDs rejected; blank group/task/reminder titles rejected; negative task version rejected; non-positive reminder offset rejected; valid model values preserved; `GroupReminderAudience.Member` preserves its user ID.

```kotlin
@Test(expected = IllegalArgumentException::class)
fun `given blank collaboration group id when created then throws`() {
    CollaborationGroupId("   ")
}

@Test(expected = IllegalArgumentException::class)
fun `given zero reminder offset when created then throws`() {
    GroupTaskReminder(GroupTaskId("task_1"), 0)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests "com.smartreminder.domain.model.collaboration.CollaborationDomainModelTest"
```

Expected: compilation/test failure because collaboration types do not exist.

- [ ] **Step 3: Implement minimal domain types**

Follow the existing `@JvmInline value class ... { init { require(...) } }` ID style. Use `Instant` for timestamps. Keep validation structural only; do not embed permission or server-authorization logic inside data classes.

Use these exact enum values:

```kotlin
enum class GroupRole { OWNER, ADMIN, MEMBER }
enum class GroupInviteStatus { PENDING, ACCEPTED, DECLINED }
enum class GroupTaskStatus { TODO, IN_PROGRESS, COMPLETED, CANCELLED }
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused test; expected PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smartreminder/domain/model/collaboration app/src/test/java/com/smartreminder/domain/model/collaboration/CollaborationDomainModelTest.kt
git commit -m "feat: add collaboration domain models"
```

---

### Task 2: Implement pure permission and task workflow policies

**Files:**
- Create: `app/src/main/java/com/smartreminder/domain/collaboration/GroupPermissionEvaluator.kt`
- Create: `app/src/main/java/com/smartreminder/domain/collaboration/GroupTaskPolicy.kt`
- Test: `app/src/test/java/com/smartreminder/domain/collaboration/GroupPermissionEvaluatorTest.kt`
- Test: `app/src/test/java/com/smartreminder/domain/collaboration/GroupTaskPolicyTest.kt`

**Interfaces:**
- `GroupPermissionEvaluator.permissionsFor(currentUserId: UserId, role: GroupRole): GroupPermissions`
- `GroupPermissionEvaluator.canRemoveMember(actorRole: GroupRole, targetRole: GroupRole): Boolean`
- `GroupTaskPolicy.canReassign(actorId: UserId, actorRole: GroupRole, task: GroupTask): Boolean`
- `GroupTaskPolicy.canStart(actorId: UserId, task: GroupTask): Boolean`
- `GroupTaskPolicy.canComplete(actorId: UserId, task: GroupTask): Boolean`
- `GroupTaskPolicy.canCancel(actorId: UserId, actorRole: GroupRole, task: GroupTask): Boolean`
- `GroupTaskPolicy.canReopen(actorId: UserId, actorRole: GroupRole, task: GroupTask): Boolean`
- `GroupTaskPolicy.isOverdue(task: GroupTask, now: Instant): Boolean`

`GroupPermissions` contains booleans for group-admin capabilities only: `canEditGroup`, `canInviteMember`, `canChangeRoles`, `canTransferOwnership`, `canDeleteGroup`.

- [ ] **Step 1: Write table-driven failing permission tests**

Lock these approved rules exactly: Owner/Admin may edit group and invite; Member may not. Only Owner changes roles/transfers ownership/deletes group. Admin removes Member only. Owner removes Admin or Member. No actor removes Owner through the generic remove-member operation.

- [ ] **Step 2: Write failing task-policy tests**

Cover creator/Owner/Admin reassign, non-creator Member denied, only assignee starts/completes, creator/Owner/Admin cancel/reopen, and `isOverdue` false for COMPLETED/CANCELLED even after due time.

- [ ] **Step 3: Run focused tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests "com.smartreminder.domain.collaboration.*"
```

- [ ] **Step 4: Implement the minimal pure policies**

Do not read repositories, clocks, Android classes, or Supabase from these policies. They must be deterministic pure Kotlin.

- [ ] **Step 5: Run focused tests and verify GREEN**

Use the same command; expected PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smartreminder/domain/collaboration app/src/test/java/com/smartreminder/domain/collaboration
git commit -m "feat: add collaboration permission policies"
```

---

### Task 3: Define repository, error, and mutation-result contracts

**Files:**
- Create: `app/src/main/java/com/smartreminder/domain/repository/CollaborationError.kt`
- Create: `app/src/main/java/com/smartreminder/domain/repository/CollaborationMutationResult.kt`
- Create: `app/src/main/java/com/smartreminder/domain/repository/CollaborationRepository.kt`

**Interfaces:**

The repository is a contract only in G1. Use domain commands/data rather than leaking Supabase DTOs. At minimum expose:

```kotlin
interface CollaborationRepository {
    fun observeGroups(): Flow<List<CollaborationGroup>>
    fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?>
    fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>>
    fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>>
    fun observeInvites(): Flow<List<GroupInvite>>

    suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationResult
    suspend fun startTask(taskId: GroupTaskId): CollaborationMutationResult
    suspend fun completeTask(taskId: GroupTaskId): CollaborationMutationResult
    suspend fun editOwnTaskContent(command: EditOwnGroupTaskContentCommand): CollaborationMutationResult
}
```

Create command value objects in the same repository file or a focused `domain/repository/collaboration` package if that better matches current repository style. `CreateGroupTaskCommand` must include client-generated `taskId`, `groupId`, `title`, optional description, `assigneeId`, `dueAt`, and 1..5 unique positive `reminderOffsetsSeconds`. `EditOwnGroupTaskContentCommand` includes `taskId`, `title`, optional description, and `expectedVersion`.

`CollaborationError` exact vocabulary:

```text
NetworkUnavailable
NotAuthorized
NotFound
Conflict
InvalidState
Validation
MemberNotFound
InviteAlreadyPending
AlreadyMember
SyncRejected
Unknown
```

- [ ] **Step 1: Add compile-level contract tests or invariant tests for command validation**

Test `CreateGroupTaskCommand` rejects blank title, zero reminders, more than 5 reminders, duplicate offsets, and non-positive offsets. Test `EditOwnGroupTaskContentCommand` rejects blank title and negative expectedVersion.

- [ ] **Step 2: Verify RED**

Run the focused repository-contract test class.

- [ ] **Step 3: Implement contracts only**

Do not create a fake production repository or wire `AppContainer` yet. G2/G3 will supply working remote/cache composition.

- [ ] **Step 4: Verify GREEN and compile**

```bash
./gradlew testDebugUnitTest --tests "com.smartreminder.domain.repository.*"
./gradlew compileDebugSources
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smartreminder/domain/repository app/src/test/java/com/smartreminder/domain/repository
git commit -m "feat: define collaboration repository contracts"
```

---

### Task 4: Define durable pending-command payload schemas and codec

**Files:**
- Create the four files listed under **Pending command serialization boundary**.
- Test: `app/src/test/java/com/smartreminder/data/local/room/model/collaboration/PendingGroupCommandCodecTest.kt`

**Interfaces:**
- `PendingGroupCommandType`: `CREATE_TASK`, `START_TASK`, `COMPLETE_TASK`, `EDIT_OWN_TASK_CONTENT`.
- `PendingGroupCommandState`: `PENDING`, `SYNCING`, `FAILED`.
- `PendingGroupCommandPayload` is a sealed `@Serializable` hierarchy with `schemaVersion: Int = 1` in every payload.
- `PendingGroupCommandCodec.encode(payload): String` and `decode(type, json): PendingGroupCommandPayload`.

Persist primitive storage shapes only: IDs as strings, `dueAt` as ISO-8601 string or epoch milliseconds consistently, and reminder offsets as `List<Long>`. Do not serialize domain inline/value classes directly into the durable schema.

- [ ] **Step 1: Write failing round-trip tests for all four payload types**

Also test unknown/unsupported payload version fails explicitly rather than silently mis-decoding.

- [ ] **Step 2: Verify RED**

```bash
./gradlew testDebugUnitTest --tests "com.smartreminder.data.local.room.model.collaboration.PendingGroupCommandCodecTest"
```

- [ ] **Step 3: Implement minimal versioned payloads and codec**

Reuse the existing project `kotlinx.serialization.json` dependency; do not add a new JSON library.

- [ ] **Step 4: Verify GREEN**

Run the focused test again.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smartreminder/data/local/room/model/collaboration app/src/test/java/com/smartreminder/data/local/room/model/collaboration
git commit -m "feat: add collaboration pending command codec"
```

---

### Task 5: Add Room collaboration cache and pending-command schema with migration 1 -> 2

**Files:**
- Create all Room entity/DAO/migration files listed above.
- Modify: `app/src/main/java/com/smartreminder/data/local/room/CueDatabase.kt`
- Generated/exported: `app/schemas/com.smartreminder.data.local.room.CueDatabase/2.json`
- Test: `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationRoomMigrationTest.kt`
- Test: `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationCacheDaoTest.kt`

**Interfaces:**
- Cache tables use names prefixed with `cached_` to make non-authoritative semantics obvious.
- `PendingGroupCommandEntity` persists `id`, `command_type`, `aggregate_id`, `payload_json`, `payload_version`, nullable `expected_version`, `created_at`, `attempt_count`, `state`, nullable `last_error`.
- `CollaborationCacheDao` supports deterministic `Flow` reads and replace/upsert operations needed by later repository work.
- `PendingGroupCommandDao` supports ordered pending reads by `created_at`, state updates, deletion after server acceptance, and per-aggregate ordering queries.

Use integer/long primitive storage for timestamps consistently with existing Room conventions. Add foreign keys only where cache lifecycle semantics are unambiguous; pending commands must not be cascade-deleted merely because a cached entity is refreshed/removed.

- [ ] **Step 1: Write the migration test against schema version 1**

Create a v1 database, insert representative existing `ScheduleGroup`/Routine data, close it, migrate with `MIGRATION_1_2`, and assert existing data remains plus new collaboration tables exist.

- [ ] **Step 2: Run the migration test and verify RED**

Run on an emulator/device:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationRoomMigrationTest
```

Expected: failure because migration/tables are not implemented.

- [ ] **Step 3: Implement entities, DAOs, and `MIGRATION_1_2`**

Update `CueDatabase` from version 1 to 2, add abstract DAO accessors, and register `.addMigrations(MIGRATION_1_2)` in `buildDatabase`. Do not use `fallbackToDestructiveMigration`.

- [ ] **Step 4: Add DAO instrumentation tests**

Verify deterministic group/task observation ordering, cache replacement does not remove unrelated pending commands, and pending commands for one aggregate are returned oldest-first.

- [ ] **Step 5: Run Android tests and export schema 2**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationRoomMigrationTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationCacheDaoTest
./gradlew compileDebugSources
```

Confirm `app/schemas/com.smartreminder.data.local.room.CueDatabase/2.json` exists and version 1 schema remains committed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smartreminder/data/local/room app/src/androidTest/java/com/smartreminder/data/local/room app/schemas/com.smartreminder.data.local.room.CueDatabase
git commit -m "feat: add collaboration room foundation"
```

---

### Task 6: Add Supabase collaboration schema and RLS foundation

**Files:**
- Create: `doc/supabase/collaboration_groups_v1.sql`

**Interfaces:**

The SQL file must define these public tables exactly at foundation level:

```text
collaboration_groups
group_members
group_invites
group_tasks
group_task_reminders
group_reminders
```

Also define the database constraints approved in the spec:

- soft-delete timestamp on groups;
- `(group_id, user_id)` primary key for membership;
- at most one Owner per group via a partial unique index;
- at most one pending invite per `(group_id, invitee_user_id)` via a partial unique index;
- task `due_at timestamptz not null` and `version bigint not null`;
- positive unique task reminder offsets;
- reminder audience consistency: MEMBER requires `audience_user_id`, EVERYONE requires null;
- foreign keys to `auth.users(id)` and collaboration parent rows with explicit delete behavior.

RLS foundation must enable RLS on every collaboration table and support read visibility only:

- current members can read group/member/task/reminder data for their groups;
- invitee can read invitations addressed to them;
- Owner/Admin can read pending invitations for groups they manage.

Create small SQL helper predicates/functions only if they reduce repeated policy logic, and make them `stable` with explicit `search_path` where appropriate. Do not add privileged mutation RPCs in G1; G2/G3 own those commands.

- [ ] **Step 1: Write the SQL file in transaction-safe order**

Create parent tables before children, indexes/constraints before policies, then enable RLS and create read policies. Make the script re-runnable where practical using `if not exists`/`drop policy if exists` conventions already used in `user_preferences_v1.sql`.

- [ ] **Step 2: Perform a static self-check**

Search the SQL file and confirm there is no policy granting unrestricted `insert`, `update`, or `delete` to authenticated clients. G1 read policies must not accidentally bypass later RPC authorization.

- [ ] **Step 3: Apply in a Supabase development project and execute smoke queries**

Verify: member A can read group A; non-member B cannot read group A; pending invitee can read their own invite but cannot read private group/task rows; member cannot directly update `group_members.role` or `group_tasks.status` through normal table access.

Record the exact SQL errors/results in the implementation review notes; do not commit secrets or service-role keys.

- [ ] **Step 4: Commit**

```bash
git add doc/supabase/collaboration_groups_v1.sql
git commit -m "feat: add collaboration supabase schema"
```

---

### Task 7: Update the app shell to Today | Calendar | Schedules | Groups | Profile

**Files:**
- Create: `app/src/main/java/com/smartreminder/ui/calendar/CalendarPlaceholderScreen.kt`
- Create: `app/src/main/java/com/smartreminder/ui/groups/GroupsPlaceholderScreen.kt`
- Modify: `app/src/main/java/com/smartreminder/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`

**Interfaces:**
- `AppDestination` exact order: `TODAY`, `CALENDAR`, `SCHEDULES`, `GROUPS`, `PROFILE`.
- Calendar and Groups render simple placeholder screens only in G1.
- Existing Schedules and Profile wiring remains intact.
- Personal Task code is not deleted; only `TASKS` leaves the top-level destination enum.

- [ ] **Step 1: Add localized nav strings**

English:

```xml
<string name="nav_calendar">Calendar</string>
<string name="nav_groups">Groups</string>
```

Vietnamese:

```xml
<string name="nav_calendar">Lịch</string>
<string name="nav_groups">Nhóm</string>
```

- [ ] **Step 2: Add Calendar/Groups placeholder screens with previews**

Mirror the existing placeholder screen standards: `CueSpacing`, Material typography/color tokens, string resources, and `SmartReminderTheme` preview. Do not copy Stitch visual styling into G1.

- [ ] **Step 3: Surgically update `MainActivity.kt` navigation wiring**

Replace only Tasks-specific app-shell import/icon/destination/switch lines. Use an appropriate Material icon for Calendar and Groups from the already-present extended icon dependency. Do not restructure onboarding, Schedules VM creation, or Profile wiring.

- [ ] **Step 4: Compile**

```bash
./gradlew compileDebugSources
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke check**

Open the app and verify the five destinations appear in exact approved order; Today/Schedules/Profile still open; Calendar/Groups show placeholders; switching tabs does not crash.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smartreminder/MainActivity.kt app/src/main/java/com/smartreminder/ui/calendar app/src/main/java/com/smartreminder/ui/groups app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml
git commit -m "feat: add calendar and groups app destinations"
```

---

### Task 8: G1 integration verification and zero-churn review

**Files:**
- No new production files expected.
- Modify only files required to fix defects discovered by verification.

**Interfaces:**
- G1 is complete only if domain tests, serialization tests, compile, full unit tests, and Room migration/cache instrumentation tests pass.
- No G2-G5 behavior is allowed to sneak into G1.

- [ ] **Step 1: Run focused pure tests**

```bash
./gradlew testDebugUnitTest --tests "com.smartreminder.domain.model.collaboration.*" --tests "com.smartreminder.domain.collaboration.*" --tests "com.smartreminder.domain.repository.*" --tests "com.smartreminder.data.local.room.model.collaboration.*"
```

Expected: all PASS.

- [ ] **Step 2: Run full JVM suite**

```bash
./gradlew test
```

Expected: all tests PASS; do not use an old absolute test count as an acceptance criterion.

- [ ] **Step 3: Run compile preflight**

```bash
./gradlew compileDebugSources
```

Expected: BUILD SUCCESSFUL with zero compile errors.

- [ ] **Step 4: Run Room instrumentation tests**

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationRoomMigrationTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationCacheDaoTest
```

Expected: both PASS.

- [ ] **Step 5: Review git diff for scope and churn**

Confirm there is no destructive migration, no deleted personal Task domain, no `ScheduleGroup` reuse for collaboration, no hardcoded UI strings/colors/spacing, no arbitrary serialized Kotlin class names in pending payloads, no WorkManager/FCM code, and no unrelated refactors.

- [ ] **Step 6: Review G1 against the approved spec**

Spec coverage expected in G1: domain vocabulary, client permission policy, repository/error boundaries, durable cache/queue schema, Supabase table/RLS foundation, and top-level navigation. Explicitly defer membership command implementation to G2, task command implementation to G3, queue replay/conflict orchestration to G4, and outbox/scheduler/FCM to G5.

- [ ] **Step 7: Final verification commit only if fixes were required**

If verification produced code fixes:

```bash
git add <only-fixed-files>
git commit -m "fix: complete collaboration G1 verification"
```

If no fixes were required, do not create an empty commit.

---

## Self-Review Result

- **Spec coverage:** G1 covers the foundation slice only and leaves G2-G5 responsibilities explicit. No membership/task RPC, replay worker, or notification implementation is hidden in this plan.
- **Placeholder scan:** No `TBD`, `TODO`, “implement later”, or unspecified test step remains. Calendar/Groups are intentionally named product placeholders, not implementation placeholders.
- **Type consistency:** `GroupTask` uses typed IDs and `Instant`; pending durable payloads use primitive storage forms; `expectedVersion` is used only for mutations of existing tasks; offline create uses stable client-generated task ID and no server version.
- **Migration safety:** Room version changes 1 -> 2 through an explicit migration and retains schema 1 export.
- **Security boundary:** RLS read policies are foundational only; privileged writes remain unavailable until server RPC commands are implemented in later slices.
