# Cue Reminder Architecture - Proposed Adjustments

> Draft for review. This document proposes architecture adjustments before implementation. It does not add runtime code.

## 1. Decision Summary

Keep the product flow:

```text
Natural language input
        -> Parsed preview
        -> User confirmation
        -> Persist task
        -> Schedule reminder
        -> Notification action
        -> Execution history
```

Adjust the implementation as follows:

- Keep Personal Task and Group Task as separate bounded contexts.
- Treat AI output as a draft, not as a persisted Task entity.
- Model reminder rules separately from tasks.
- Store execution history in Room with a structured schema.
- Use an Android scheduler abstraction; prefer AlarmManager for precise local reminders.
- Use WorkManager for retryable background work and synchronization, not as the only precise alarm mechanism.
- Keep email and calendar integrations outside the first local reminder MVP.

## 2. Current Repository Boundaries

### Personal tasks

Current path:

- Domain model: `domain/model/task/Task.kt`
- Repository contract: `domain/repository/TaskRepository.kt`
- Room implementation: `data/local/room/repository/RoomTaskRepository.kt`

Personal tasks are currently local Room data.

### Group tasks

Current path:

- Domain model: `domain/model/collaboration/GroupTask.kt`
- Repository contract: `domain/repository/CollaborationRepository.kt`
- Remote implementation: Supabase collaboration data source
- Local cache: `data/local/room/entity/collaboration/`

Group tasks are remote-authoritative data with a Room cache, versions, typed IDs, and collaboration-specific mutation rules.

### Architectural rule

Do not create one generic `Task` abstraction that merges both contexts. A reminder may reference either kind, but the ownership must remain explicit.

Possible reference shape:

```kotlin
enum class ReminderTargetType {
    PERSONAL_TASK,
    GROUP_TASK
}
```

The target type should be part of the reminder reference or event record, not hidden in a generic string convention.

## 3. Proposed Domain Models

### 3.1 Parsed task draft

AI parsing should return a draft that still requires user confirmation:

```kotlin
data class ParsedTaskDraft(
    val title: String,
    val description: String?,
    val scheduledAt: Instant?,
    val dueAt: Instant?,
    val durationMinutes: Int?,
    val priority: TaskPriority?,
    val reminderSuggestions: List<ReminderSuggestion>,
    val confidence: Float,
    val entities: List<String>
)
```

Rules:

- `scheduledAt` and `dueAt` may be null when the user did not provide enough information.
- The parser must not silently invent a date or time.
- The UI must show an editable preview before persistence.
- `confidence` and `entities` belong to the parsing result, not necessarily to the persisted task.
- The parser implementation belongs behind a domain interface; the API client belongs in `data/remote/`.

### 3.2 Reminder rule

Reminder configuration should be separate from the task:

```kotlin
data class ReminderRule(
    val id: String,
    val targetId: String,
    val targetType: ReminderTargetType,
    val remindAt: Instant?,
    val offsetSeconds: Long?,
    val recurrence: ReminderRecurrence?,
    val isEnabled: Boolean
)
```

Only one of `remindAt` or `offsetSeconds` should be used for a simple rule. More complex recurrence rules can be added later.

For group tasks, the existing reminder-offset concept should remain compatible with `GroupTaskReminder` and the Supabase contract. Do not duplicate group reminder data into the personal task tables.

### 3.3 Execution history

The architecture document already reserves Room for execution history. Use a structured event model instead of `Map<String, String>` in a Room entity:

```kotlin
data class ExecutionHistory(
    val id: String,
    val targetId: String,
    val targetType: ReminderTargetType,
    val eventType: ExecutionEventType,
    val occurredAt: Instant,
    val scheduledAt: Instant?,
    val snoozeSeconds: Long?
)
```

Initial event types:

```kotlin
enum class ExecutionEventType {
    TASK_CREATED,
    REMINDER_SCHEDULED,
    REMINDER_SENT,
    NOTIFICATION_OPENED,
    TASK_COMPLETED,
    TASK_SNOOZED,
    TASK_RESCHEDULED
}
```

If arbitrary metadata is needed later, store a serialized JSON string in a dedicated column with a documented schema. Do not use an unstructured `Map` as the primary Room contract.

## 4. Scheduler Boundary

Create a small interface in the domain or application boundary:

```kotlin
interface ReminderScheduler {
    suspend fun schedule(reminder: ReminderRule): ScheduleResult
    suspend fun cancel(reminderId: String)
}
```

The Android implementation belongs in the data/platform layer.

### Scheduler responsibilities

- Convert the domain `Instant` into an Android trigger time.
- Schedule, replace, and cancel reminders idempotently.
- Use stable request identifiers derived from the reminder ID.
- Avoid scheduling reminders in the past without an explicit policy.
- Record `REMINDER_SCHEDULED` only after the platform scheduling operation succeeds.

### AlarmManager vs WorkManager

Use `AlarmManager` for local reminders that should fire at a user-selected time. Consider exact alarm permission and Android policy requirements explicitly.

Use WorkManager for:

- Retryable network work.
- Email/calendar synchronization.
- Cleanup and reconciliation jobs.
- Non-precise background processing.

Do not make the domain layer depend directly on `WorkManager` or `AlarmManager`.

## 5. Notification Flow

Recommended flow:

```text
ReminderScheduler
        -> Android alarm trigger
        -> Internal BroadcastReceiver
        -> Load target from repository
        -> Show notification
        -> Log REMINDER_SENT
```

Notification actions:

```text
Done
  -> update target completion
  -> log TASK_COMPLETED
  -> cancel active reminder

Snooze
  -> calculate new trigger time
  -> update or replace reminder rule
  -> reschedule reminder
  -> log TASK_SNOOZED and REMINDER_SCHEDULED
```

Implementation constraints:

- Create a notification channel during application startup or first use.
- Request `POST_NOTIFICATIONS` at the appropriate UI moment on Android 13+.
- Declare internal receivers with `android:exported="false"`.
- Use immutable PendingIntents and stable request codes.
- Do not perform long Room or network operations directly inside `BroadcastReceiver.onReceive`.
- Preserve `CancellationException` in coroutine code.
- Make notification actions idempotent so repeated delivery does not complete or snooze a task twice.

## 6. AI Parser Boundary

Recommended dependency direction:

```text
UI
  -> ViewModel
    -> ParseTaskUseCase or parser interface
      -> NLPParser interface
        -> Remote/local parser implementation
```

The parser should return a typed result and a typed failure. It should not directly write Room data.

Minimum validation before showing a confirmation preview:

- Non-blank title.
- Valid date/time or an explicit missing-field state.
- Duration greater than zero when supplied.
- Priority within the known enum.
- Reminder time not after the task deadline unless the user explicitly chooses it.
- Time zone and `Clock` policy are defined.

The first parser can be remote, but the core app must still support manual task creation and local operation without the parser.

## 7. Email and Calendar Scope

### MVP

Exclude email and calendar side effects from the first reminder milestone. Implement local task, local reminder, notification actions, and execution history first.

### Later integration

Calendar and email should be separate integrations:

- Calendar event creation is triggered by task creation or an explicit user action.
- Reminder delivery must not create a new calendar event.
- External integrations need authorization, token lifecycle, retry, duplicate prevention, and clear error states.
- Email sending should preferably happen through a backend or an explicitly authorized provider flow, not through embedded secrets in the Android app.

## 8. Recommended Delivery Order

### Phase 0 - Contract and tests

- Confirm whether the first target is Personal Task only.
- Define `ReminderRule`, `ExecutionHistory`, and event semantics.
- Define timezone and `Clock` behavior.
- Add Given-When-Then unit tests for reminder calculations and snooze.

### Phase 1 - Local reminder MVP

- Add Room entities, DAOs, repositories, and migrations.
- Add `ReminderScheduler` abstraction.
- Implement Android alarm scheduling.
- Implement notification channel and internal receiver.
- Implement Done and Snooze actions.
- Log execution history.
- Wire dependencies through the existing manual `AppContainer`.

### Phase 2 - AI-assisted creation

- Add `NLPParser` interface and remote implementation.
- Add parsed preview UI with confirm/edit/cancel.
- Convert confirmed draft into a Personal Task and ReminderRule.
- Add parser validation and failure states.

### Phase 3 - Advanced planning

- Repeating reminders.
- Deadline reminders and multiple offsets.
- Conflict detection and free-slot suggestions.
- Reconciliation after reboot, time changes, and timezone changes.

### Phase 4 - External integrations

- Calendar integration.
- Email integration.
- Provider-specific authorization and retry policies.

## 9. Required Project Compliance

Every implementation should preserve the existing project rules:

- UI remains stateless and uses UDF.
- ViewModels expose immutable state.
- No Room or API calls from Composables.
- No hardcoded user-facing strings; add English and Vietnamese resources.
- Use existing design tokens and spacing tokens.
- Keep domain logic free from Android framework dependencies.
- Use the existing manual DI pattern in `AppContainer`.
- Inject `Clock` for time-dependent logic.
- Never use `!!`.
- Re-throw `CancellationException`.
- Add unit tests for logic that can be expressed as Given-When-Then.
- Run `.\gradlew.bat compileDebugSources` and `.\gradlew.bat test` before completion.

## 10. Open Decisions Before Coding

1. Is the first reminder milestone for Personal Task only?
2. Should a task support both a scheduled start and a deadline, or only one initially?
3. Are reminders local-device-only, or must they sync across devices?
4. Is exact-to-the-minute delivery required?
5. What timezone should be used when a user travels?
6. Should snooze change the existing rule or create a temporary execution override?
7. Is AI parsing allowed to call a remote provider in the MVP?

## 11. Recommendation

Approve the product flow, but implement only the local Personal Task reminder slice first. Keep Group Task, AI parsing, email, calendar, analytics, and cross-device synchronization behind explicit boundaries until the reminder contract and notification lifecycle are proven with tests.
