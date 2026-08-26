# Cue — Collaboration Groups v1 Architecture Design

> **Status:** Approved design, pending implementation planning.
> **Scope:** Collaborative Groups subsystem only. This document does not implement Calendar, personal Task, Routine, or Liquid Glass polish.

## 1. Product intent

Cue Groups is a collaborative workspace for shared task assignment and group reminders. It is a separate subsystem from the existing `ScheduleGroup` concept.

- `ScheduleGroup` = local organizational grouping for routines.
- `CollaborationGroup` = multi-user workspace with members, roles, tasks, invitations, reminders, and notifications.
- Personal `Task` remains a separate future domain concept.
- Top-level product navigation becomes `Today | Calendar | Schedules | Groups | Profile`.
- Removing `Tasks` from the top-level navigation does not remove personal Task from the domain; Today/Calendar may surface personal tasks later.

## 2. V1 roles and permissions

Roles are explicit:

```text
OWNER
ADMIN
MEMBER
```

Role controls group administration. Task-specific ownership and assignment control task workflow permissions.

| Operation | Owner | Admin | Member |
|---|:---:|:---:|:---:|
| View group | Yes | Yes | Yes |
| Edit group metadata | Yes | Yes | No |
| Invite user | Yes | Yes | No |
| Change roles | Yes | No | No |
| Transfer ownership | Yes | No | No |
| Delete group | Yes | No | No |
| Create GroupTask | Yes | Yes | Yes |
| Assign task to any current member | Yes | Yes | Yes |
| Create GroupReminder | Yes | Yes | Yes |
| Create reminder for Everyone | Yes | Yes | Yes |
| Reassign own-created task | Yes | Yes | Yes |
| Reassign another creator's task | Yes | Yes | No |
| Cancel own-created task | Yes | Yes | Yes |
| Cancel another creator's task | Yes | Yes | No |
| Reopen own-created task | Yes | Yes | Yes |
| Reopen another creator's task | Yes | Yes | No |
| Complete task | Only when assignee | Only when assignee | Only when assignee |

Additional membership rules:

- Admin may remove `MEMBER` only.
- Owner may remove `ADMIN` or `MEMBER`.
- Owner cannot leave while still Owner.
- Owner must transfer ownership to an accepted member first.
- After transfer, old Owner becomes `MEMBER`.
- A group must have exactly one Owner while it exists.
- If Owner is the last member, the group must be deleted rather than left ownerless.

## 3. Domain model

### 3.1 CollaborationGroup

```text
CollaborationGroup
- id
- name
- description?
- createdBy
- createdAt
- updatedAt
```

Do not persist `ownerId` separately. Ownership is represented by the unique `GroupMember(role = OWNER)` relation to avoid two sources of truth.

### 3.2 GroupMember

```text
GroupMember
- groupId
- userId
- role: OWNER | ADMIN | MEMBER
- joinedAt
```

Membership identity is always `UserId`, never email.

### 3.3 GroupInvite

V1 invites use the email address of an existing Cue account only as an account-resolution input.

```text
GroupInvite
- id
- groupId
- inviterId
- inviteeUserId
- status: PENDING | ACCEPTED | DECLINED
- createdAt
- respondedAt?
```

Rules:

- New invites always target role `MEMBER` in v1.
- `PENDING` invitee is not yet a member and cannot see private group content.
- A `(groupId, inviteeUserId)` pair may have at most one pending invite.
- An existing member cannot be invited again.
- Accept must atomically create membership and mark the invite accepted.
- Declined users may be invited again later.

### 3.4 GroupTask

Each v1 GroupTask has exactly one assignee and always has a deadline.

```text
GroupTask
- id
- groupId
- title
- description?
- createdBy
- assigneeId
- dueAt: Instant
- status: TODO | IN_PROGRESS | COMPLETED | CANCELLED
- version
- createdAt
- updatedAt
```

State rules:

```text
TODO -> IN_PROGRESS       assignee only
TODO -> COMPLETED         assignee only
IN_PROGRESS -> COMPLETED  assignee only
TODO -> CANCELLED         creator / Owner / Admin
IN_PROGRESS -> CANCELLED  creator / Owner / Admin
COMPLETED -> TODO         creator / Owner / Admin (reopen)
CANCELLED -> TODO         creator / Owner / Admin (reopen)
```

Completion is always an assignee action. Being creator, Admin, or Owner never grants completion-by-proxy.

Reassign is allowed when the requester is either the task creator, Owner, or Admin. Reassigning to the current assignee is a no-op.

Reassign notification semantics:

- Notify previous assignee that the task is no longer assigned to them.
- Notify new assignee that the task is now assigned to them.
- Do not notify the whole group.

`Overdue` is derived, not a fifth status:

```text
isOverdue = now >= dueAt
    && status != COMPLETED
    && status != CANCELLED
```

### 3.5 GroupTaskReminder

Task reminders are relative offsets before the absolute deadline.

```text
GroupTaskReminder
- taskId
- offsetSeconds
```

Rules:

- 1 to 5 pre-deadline reminder offsets per task in v1.
- Every offset must be positive and unique.
- Offsets are recomputed relative to `dueAt`; editing deadline shifts future reminder times.
- Completed or cancelled tasks stop future reminders.
- Reassign transfers future task reminders to the new assignee.

### 3.6 GroupReminder

Group reminders are independent of GroupTask.

```text
GroupReminder
- id
- groupId
- title
- description?
- createdBy
- audience: Member(userId) | Everyone
- remindAt: Instant
- createdAt
- updatedAt
```

For `Everyone`, recipients are resolved from current active group members at reminder time, not snapshotted when the reminder is created.

## 4. Aggregate boundaries

Do not model `CollaborationGroup` as one giant in-memory aggregate containing every member, task, invite, and reminder.

Use these aggregate boundaries:

```text
CollaborationGroup
GroupInvite
GroupTask
GroupReminder
```

Membership is changed through server-side group commands such as `acceptInvite`, `removeMember`, `changeMemberRole`, `transferOwnership`, and `leaveGroup`.

Cross-record invariants must be enforced transactionally on the server. Android must never implement a multi-record domain command by issuing unrelated direct updates.

## 5. Cloud architecture

The approved architecture is **Supabase-first, server-authoritative**.

```text
Compose
  -> ViewModel
  -> CollaborationRepository
       -> Room cache / pending queue
       -> Supabase reads + RPC commands
            -> Postgres + RLS
            -> transactional outbox
            -> notification worker / scheduler
            -> FCM
```

Core rules:

- Cloud data is authoritative for collaboration.
- Room is cache and local pending-command state only.
- RLS protects reads.
- Mutations with business invariants go through server commands/RPC.
- Server authorization never trusts role or requester IDs supplied by the Android client.
- Requester identity comes from the authenticated Supabase session (`auth.uid()`).

## 6. Supabase logical schema

### collaboration_groups

```text
id
name
description?
created_by
created_at
updated_at
deleted_at?
```

Use soft deletion in v1 so scheduled/event infrastructure can safely observe deletion and audit history.

### group_members

```text
group_id
user_id
role
joined_at
PK(group_id, user_id)
```

Database guarantees at most one `OWNER` row per group, while transactional commands guarantee the group never transitions to zero Owners.

### group_invites

```text
id
group_id
inviter_id
invitee_user_id
status
created_at
responded_at?
```

### group_tasks

```text
id
group_id
title
description?
created_by
assignee_id
due_at timestamptz NOT NULL
status
version bigint
created_at
updated_at
```

### group_task_reminders

```text
task_id
offset_seconds
PK(task_id, offset_seconds)
CHECK offset_seconds > 0
```

### group_reminders

```text
id
group_id
title
description?
created_by
audience_type: MEMBER | EVERYONE
audience_user_id?
remind_at timestamptz
created_at
updated_at
```

Constraint:

```text
MEMBER   -> audience_user_id != null
EVERYONE -> audience_user_id == null
```

## 7. RLS and server command boundaries

Read rules:

- Group/member/task/reminder data is readable only by current members of that group.
- Invitees may read invitations addressed to themselves even before joining.
- Owner/Admin may read invitations belonging to groups they manage.

Android must not directly mutate privileged fields such as membership role or task workflow status.

Server commands include:

```text
Group:
createGroup
updateGroup
inviteMember
respondToInvite
changeMemberRole
removeMember
transferOwnership
leaveGroup
deleteGroup

Task:
createTask
editTask
reassignTask
startTask
completeTask
cancelTask
reopenTask

Reminder:
createGroupReminder
editGroupReminder
cancelGroupReminder
```

Multi-record commands run inside a database transaction.

`transferOwnership(groupId, newOwnerId)` must validate caller ownership, validate new owner membership, lock affected membership rows, change current Owner to MEMBER and new owner to OWNER atomically, then commit.

## 8. Optimistic concurrency

`GroupTask.version` is an optimistic concurrency token.

Commands that mutate an existing task pass `expectedVersion`. If it differs from the current server version, the server returns `CONFLICT` instead of silently overwriting newer state.

Conflict-prone operations include edit, reassign, cancel, reopen, start, and complete.

V1 does not auto-merge conflicting task edits.

## 9. Transactional outbox

Domain mutation and notification side effects must never be two independent Android requests.

```text
RPC transaction
  -> mutate authoritative domain data
  -> insert outbox event
  -> COMMIT

worker
  -> claim event
  -> materialize / send notifications
  -> retry transient delivery failures
```

Logical outbox fields:

```text
id
event_type
aggregate_id
group_id
payload
created_at
processed_at?
```

Relevant event types include:

```text
TASK_ASSIGNED
TASK_REASSIGNED
TASK_COMPLETED
TASK_CANCELLED
TASK_REOPENED
GROUP_INVITED
GROUP_REMINDER_DUE
```

This is not event sourcing. Postgres rows remain the source of truth.

## 10. Notification scheduling and delivery

Group notification scheduling is server-authoritative. Android WorkManager is never the authority for collaborative reminder delivery.

Pipeline:

```text
Domain mutation / reminder policy
  -> Outbox event
  -> Logical notification occurrence
  -> Delivery scheduler
  -> recipient resolution
  -> quiet-hours adjustment
  -> collapse + dedupe
  -> FCM
  -> Android notification
  -> deep link to authoritative cloud data
```

### 10.1 Task reminder cadence

Pre-deadline reminders come from the task's 1..5 relative offsets.

Overdue cadence is fixed for v1:

```text
T+0      deadline notification
T+1h     overdue
T+6h     overdue
T+24h    overdue
T+48h    overdue
...
T+7d     final overdue push
```

After seven days the task remains visually overdue but automatic overdue push stops.

Do not replay all missed historical reminders when a device or offline-created task reconnects late. Materialize only still-relevant future overdue occurrences.

### 10.2 Quiet hours

Quiet hours are per recipient, not per group.

```text
NotificationPreferences
- quietHoursEnabled
- quietStart: LocalTime
- quietEnd: LocalTime
- timeZoneId: IANA ZoneId
```

When a logical reminder falls inside quiet hours, delivery is delayed until quiet hours end. Deadline semantics are unchanged; only the push delivery time is delayed.

Store/operate on IANA zones such as `Asia/Ho_Chi_Minh`, not fixed UTC offsets.

### 10.3 Collapse and dedupe

If multiple logical notifications for the same task and recipient are delayed to the same effective delivery window, collapse them into one notification.

Priority:

```text
OVERDUE > DUE > PRE_DUE
```

Do not collapse notifications for different tasks.

Dedupe must use stable logical identity, for example task + recipient + kind + logical occurrence, not notification text.

### 10.4 Absolute deadline semantics

`GroupTask.dueAt` is an absolute `Instant`.

The creator chooses local date/time; the client converts it to an Instant before sending. Every recipient displays that same instant in their current device timezone. Quiet-hours evaluation uses the recipient's timezone/preference.

### 10.5 Device tokens

FCM tokens belong to user device installations, never to GroupMember.

```text
user_devices
- id
- user_id
- fcm_token
- platform
- last_seen_at
- enabled
```

A user may have multiple active devices. Permanent invalid-token responses disable the affected device token rather than retrying forever.

### 10.6 Payload and privacy

Push payload contains navigation identity, not authoritative task state:

```text
type
groupId
taskId?
notificationKind
```

On tap, Android navigates to the target and fetches authoritative data. If membership or task access no longer exists, the app handles `NOT_AUTHORIZED`/`NOT_FOUND` safely.

Lock-screen content should remain minimal by default; detailed group/task data is fetched only after the app opens under authenticated access.

## 11. Android architecture

Groups is an isolated feature subsystem and must not reuse `ScheduleGroup`.

Target package direction, adjusted to actual repository conventions during implementation:

```text
groups/
  domain/
    model/
    permission/
    repository/
  data/
    local/
    remote/
    mapper/
    repository/
  sync/
  ui/
    list/
    detail/
    task/
    members/
    invite/
    settings/
```

Compose never talks to Room or Supabase directly.

### Repository behavior

Repository observations combine authoritative Room cache with pending local commands so optimistic state survives process death.

Mutation result vocabulary:

```text
Applied
Queued
Conflict
NotAuthorized
InvalidState
NetworkRequired
Failure
```

Infrastructure exceptions must be mapped to typed collaboration errors; UI must never parse Postgres/Supabase error strings.

## 12. Offline strategy

V1 supports offline read plus a bounded write queue.

### Queueable commands

```text
CreateTask
StartTask
CompleteTask
EditOwnTaskContent(title, description)
```

### Online-only commands

```text
Reassign
Edit dueAt
Edit reminder offsets
Cancel
Reopen
Invite / accept / decline
Role changes
Remove member
Transfer ownership
Leave / delete group
Create / edit GroupReminder
```

An offline `CreateTask` may contain assignee, absolute deadline, and pre-reminder offsets because it is one creation intent that does not yet exist server-side.

If its deadline has passed by the time it reaches the server, the server still creates it with the original deadline and it is immediately overdue. The server never silently shifts the user's deadline.

### 12.1 Pending command persistence

Room stores both authoritative cache and pending command state.

```text
PendingGroupCommand
- id
- commandType
- aggregateId
- versioned payload
- expectedVersion?
- createdAt
- attemptCount
- state: PENDING | SYNCING | FAILED
- lastError?
```

Do not serialize arbitrary Kotlin implementation classes into the queue. Persist explicit, versioned payload schemas.

### 12.2 Optimistic projection

UI-visible optimistic state is derived from:

```text
Cached authoritative entity
  + ordered pending commands
  -> observed entity + SyncState
```

Sync state is presentation/infrastructure state, separate from task status:

```text
SYNCED
PENDING
FAILED
```

For example, `COMPLETED + PENDING` means the local user requested completion but the server has not accepted it yet.

### 12.3 Ordering and replay

Commands for the same aggregate replay in order. Independent task aggregates may sync independently.

Example:

```text
Create X
Edit X
Start X
```

must replay in that order.

Offline create uses a stable client-generated UUID/idempotency identity across retries. If the server processed the request but the response was lost, replay must not create a duplicate task.

### 12.4 Conflict handling

When queued commands replay, the server still validates current authorization, membership, version, and task state.

On conflict:

- mark the command failed;
- refresh authoritative data;
- remove/override stale optimistic projection;
- explain the conflict in business language;
- do not silently auto-merge in v1.

## 13. Android sync worker

`GroupSyncCoordinator` uses WorkManager to replay local mutations when network becomes available, when app resumes, or when a new queueable command is inserted.

Its responsibility is only:

```text
Pending command
  -> Supabase RPC
  -> handle result
  -> refresh authoritative cache
```

It never schedules collaborative push notifications. Notification scheduling belongs to the server.

## 14. Navigation and UI flows

Top-level navigation:

```text
Today | Calendar | Schedules | Groups | Profile
```

`Calendar` may initially be a placeholder/entry surface; Calendar implementation is outside Groups v1 scope.

Groups internal navigation:

```text
GroupsList
  -> NewGroup
  -> PendingInvites
  -> GroupDetail(groupId)
       -> Members
       -> InviteMember
       -> CreateTask
       -> TaskDetail(taskId)
            -> EditTask
       -> CreateReminder
       -> GroupSettings
```

Notification deep link:

```text
Push -> Groups -> GroupDetail(groupId) -> TaskDetail(taskId)
```

Bottom navigation remains visible on main destinations such as GroupsList and may remain visible on GroupDetail to match product design. Transactional/full-screen flows such as create/edit task, invite member, group settings, and create reminder hide the bottom bar.

### GroupsList UI model

```text
GroupCardUiModel
- id
- name
- subtitle?
- memberPreview
- memberCount
- pendingTaskCount
- overdueTaskCount
- currentUserRole
```

Prefer actionable overdue count over a generic task count when relevant.

### GroupDetail UI

Primary regions:

```text
Group header
Members
Task list
```

A "New Assignment" banner is derived presentation from relevant event/state, not a separate domain entity.

Task rows expose title, assignee, due display, status, derived overdue state, and sync state.

### UI permission handling

ViewModels consume pure `GroupPermissions` / `TaskPermissions` from domain evaluators. Compose uses them to show/disable actions for UX, while the server still re-authorizes every command.

All Compose work must follow `CODING_STANDARDS.md`: theme tokens, `CueSpacing`, Material typography tokens, EN/VI string resources, UDF/state hoisting, accessibility semantics, >=48dp touch targets, previews for major screens/components, TDD for business logic, and preflight compile/test verification.

## 15. Error model

Typed error vocabulary:

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

Recoverable failures include network unavailable, conflict, and temporary sync failure. Authorization loss, invalid state, removed membership, and missing entities terminate the attempted action and require authoritative refresh/navigation handling.

## 16. Testing strategy

### Pure unit tests

Required coverage includes:

- permission matrix;
- task state transitions;
- `isOverdue`;
- quiet-hours calculation including cross-midnight windows;
- timezone behavior including at least one DST zone;
- reminder occurrence generation;
- collapse/dedupe policy;
- optimistic projection;
- pending-command ordering.

### Database / RLS tests

Verify at minimum:

- member of group A cannot read private data of group B;
- member cannot promote self to Owner;
- Admin cannot remove another Admin;
- Owner transfer succeeds atomically;
- non-Owner transfer is rejected;
- pending invitee can read own invite but cannot read private group content;
- exactly-one-Owner invariant is preserved through membership commands.

### Offline sync tests

Verify at minimum:

```text
CreateTask offline -> process death -> reopen -> remains pending -> reconnect -> one server task only
Complete v5 offline -> server reassigns to v6 -> reconnect -> conflict -> optimistic completion rolls back
Create -> Edit -> Start -> replay order preserved
RPC success + lost response -> retry same idempotency key -> no duplicate
```

### Notification tests

Use deterministic `Clock`/time sources. Verify pre-reminders, deadline, all overdue occurrences through seven days, quiet-hours delay, collapse, complete/cancel cancellation, reopen rescheduling, reassign recipient changes, and Everyone current-members resolution.

### UI/ViewModel tests

Focus on behavior rather than pixel implementation: loading/content/empty/error, invites, role-gated actions, task validation, assignee/deadline/reminders, sync states, conflicts, and membership flows.

## 17. Delivery slices

Do not implement the entire subsystem in one PR/plan. Deliver in five vertical slices:

### G1 — Collaboration Foundation

- Domain models.
- Permission evaluators.
- Repository contracts.
- Room cache/queue foundation.
- Supabase schema and RLS foundation.
- App shell navigation updated to `Today | Calendar | Schedules | Groups | Profile`.
- Calendar remains outside Groups scope.

### G2 — Group Membership

- Groups list/detail.
- Create group.
- Email invite.
- Accept/decline.
- Members and roles.
- Transfer ownership and leave/delete rules.

### G3 — Group Tasks

- Create/view/edit task.
- Single assignee.
- Status state machine.
- Reassign/cancel/reopen.
- Absolute deadline.
- Pre-deadline reminder policy.

### G4 — Offline Task Sync

- Queueable commands.
- Optimistic projection from Room.
- WorkManager replay.
- Conflict and idempotency handling.

### G5 — Notifications

- Transactional outbox.
- Server delivery scheduler.
- Quiet hours.
- FCM delivery.
- Deep links.
- GroupReminder with Member/Everyone audience.

Required sequence:

```text
G1 -> G2 -> G3 -> G4 -> G5
```

Each slice must be independently buildable/testable and follow TDD for pure/business logic.

## 18. V1 end-to-end acceptance

Groups v1 is complete when this flow works end-to-end:

```text
Minh creates group
-> invites Lan by Cue account email
-> Lan accepts
-> both see the group

Lan creates task
-> assigns Minh
-> sets deadline + multiple pre-reminders
-> Minh receives assignment notification

Minh goes offline
-> can Start/Complete or create/edit allowed task content
-> UI shows pending sync
-> reconnect reconciles correctly

Admin reassigns task
-> old + new assignee are notified

Task becomes overdue
-> balanced cadence runs through day 7
-> recipient quiet hours delay delivery
-> completion/cancellation stops future reminders

Member creates Everyone reminder
-> current group members at reminder time receive it
-> each recipient's quiet hours are applied independently
```

## 19. Explicit non-goals for v1

- Realtime collaborative document editing.
- Chat.
- Attachments/file sharing.
- Comments.
- Multi-assignee tasks.
- Custom roles.
- Full offline-first collaboration.
- Auto-merge of task conflicts.
- Full Calendar implementation.
- Liquid Glass polish.
- Merging CollaborationGroup with ScheduleGroup.
- Merging GroupTask with personal Task before personal-task semantics are separately defined.
