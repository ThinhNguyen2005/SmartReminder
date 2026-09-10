# Collaboration G2 — Group Membership dùng thật

## Objective

Chuyển preview Groups thành luồng production `Groups list → Group detail → membership actions`, dùng Supabase làm nguồn dữ liệu chính, Room làm cache đọc offline, và Compose chỉ giao tiếp qua `GroupsViewModel → CollaborationRepository`.

## Global constraints

- Chỉ G2 membership; task mutation/reminders thuộc G3, queue replay thuộc G4, FCM thuộc G5.
- Không có sample/fake data trong production; sample models chỉ được phép trong `@Preview`.
- Membership commands online-only và không dùng pending-command queue G1.
- UI/ViewModel không truy cập `AppContainer` hoặc Supabase trực tiếp.
- Supabase migration/live smoke chỉ chạy trên development project riêng; tuyệt đối không apply vào project hiện tại/production.
- Supabase credentials lấy từ local/environment properties, không commit; thiếu cấu hình phải fail rõ ràng.
- Bảo toàn thay đổi chưa commit trong `app/src/main/AndroidManifest.xml` và không đưa nó vào commit G2.
- Thực hiện TDD cho code có hành vi: test đỏ trước, code tối thiểu, test xanh, rồi refactor.
- Mỗi task implementation có commit riêng và phải qua task review trước khi sang task phụ thuộc.

## Task 1: Domain membership contract and policy

Extend `CollaborationRepository` with `refreshGroups`, `refreshGroup(groupId)`, `refreshInvites`, `createGroup`, `updateGroup`, `inviteMember`, `acceptInvite`, `declineInvite`, `changeMemberRole`, `removeMember`, `transferOwnership`, `leaveGroup`, and `deleteGroup`.

Add validated command/value objects and typed `CollaborationMutationResult` / `CollaborationError`:

- group name is trimmed and non-empty;
- invite email is trimmed, normalized lowercase, and valid;
- commands never accept actor id or caller-selected create/invite role;
- target role is only `ADMIN` or `MEMBER`; ownership changes only through `transferOwnership`;
- UI never parses PostgreSQL error strings.

Add table-driven pure JVM tests for validation, permission matrix, ownership/leave/delete rules, and error-envelope mapping. Do not implement Android data/UI in this task.

Verification: focused JVM tests and full unit-test suite.

## Task 2: Supabase G2 schema, RPCs, and SQL tests

Add an additive G2 migration building on `doc/supabase/collaboration_groups_v1.sql`:

- `public.user_profiles(user_id, display_name, avatar_url, updated_at)`;
- backfill from `auth.users` and minimum-profile trigger for new accounts;
- self read/update and same-active-group profile reads only;
- authenticated clients have no direct write grants on collaboration tables;
- soft-deleted groups are excluded from all read paths.

Create transaction-safe SECURITY DEFINER RPCs named exactly:

- `create_collaboration_group`
- `update_collaboration_group`
- `invite_group_member`
- `respond_group_invite`
- `change_group_member_role`
- `remove_group_member`
- `transfer_group_ownership`
- `leave_collaboration_group`
- `delete_collaboration_group`

Every RPC derives actor from `auth.uid()`, locks relevant membership rows for role/ownership changes, preserves exactly one owner for an active group, and returns typed envelopes including `APPLIED`, `NOT_AUTHORIZED`, `MEMBER_NOT_FOUND`, `ALREADY_MEMBER`, `INVITE_ALREADY_PENDING`, and `INVALID_STATE`.

Add repeatable SQL/RLS smoke scripts for a two-account dev project, but do not apply remotely without a confirmed dev target.

Verification: local SQL structural checks; live integration remains explicitly blocked until dev credentials are supplied.

## Task 3: Room cache v3

Upgrade Room 2→3 without destructive fallback:

- cache the member profile fields required for display name/avatar;
- add replace/upsert/delete queries scoped by group and invite;
- preserve the G1 pending-command schema unchanged;
- provide deterministic member/profile/invite ordering.

Add migration instrumentation tests proving schedules, routines, and collaboration v2 data survive 2→3.

Verification: focused JVM compile/tests and Room migration/DAO instrumentation tests when a device is available.

## Task 4: Supabase data source and default repository

Add Supabase DTOs for group/member/profile/invite, mappers between remote/domain/cache, PostgREST reads, RPC writes, and `DefaultCollaborationRepository`.

Repository behavior:

- observations are cache-first Room `Flow`s;
- refresh success atomically updates cache;
- refresh/network failure never clears valid cache;
- membership mutations return `NetworkRequired` while offline;
- successful mutations refresh affected group/list/invites;
- server envelopes map to typed domain results, never raw database strings;
- no fake production repository and no Supabase SDK above data layer.

Replace hard-coded collaboration dev configuration with BuildConfig/local/environment properties and a clear missing-config failure. Do not silently fall back to another project.

Verification: repository unit tests for cache-first, refresh success/failure, cache retention, mutation mapping, and offline behavior; integration tests remain opt-in for the dev project.

## Task 5: GroupsViewModel and manual DI

Create one `GroupsViewModel`, factory, UI state, and action model. The ViewModel receives only `CollaborationRepository` and owns list/detail selection, refresh, dialog state, pending mutation state, and membership commands.

- restore selected group id through saved state;
- when a restored group no longer exists, return to list;
- back from detail returns to list;
- bottom navigation remains owned by `NavigationSuiteScaffold`;
- states cover loading/content/empty/error/cached-offline/offline-refreshing and mutation in progress.

Wire the repository and factory only at the composition root. Add ViewModel JVM tests before implementation.

Verification: focused ViewModel tests and compile.

## Task 6: Production Groups list/detail UI

Refactor the HTML-inspired preview into small Compose components:

- `GroupsListScreen`: Cue header, real groups, pending invites, New Group;
- `GroupDetailScreen`: group information, members, role badges, allowed membership actions, and a locked “coming in G3” task section;
- dialogs for create/update/invite/role/remove/transfer/leave/delete and invite accept/decline;
- avatar uses `avatarUrl` first and initials fallback; do not use sample HTML image URLs;
- role-gated actions are hidden client-side while server authorization remains authoritative;
- touch targets ≥48dp, English/Vietnamese strings, Material/Cue tokens, dark theme, TalkBack semantics.

Production receives state from `GroupsViewModel`; sample lists exist only inside `@Preview` functions.

Verification: Compose/unit tests for core states and actions, compile, screenshot/manual inspection.

## Task 7: Dev security matrix and two-account end-to-end

Against the confirmed Supabase development project only, run the SQL/RLS matrix and app flow with Cue accounts A and B:

- A creates group and is sole Owner;
- nonexistent email returns `MemberNotFound`;
- B sees invite but cannot read group before acceptance;
- after accept both users read group/members;
- Admin cannot modify Owner and Member cannot change roles;
- transfer is atomic and old Owner becomes Member;
- last Owner cannot leave;
- authenticated clients cannot directly write tables.

Record exact evidence. If dev project/account credentials are unavailable, mark this task blocked without substituting current/production credentials.

## Task 8: Zero-churn final review and device preflight

Remove remaining production preview data and unrelated churn. Run broad code/security review, full JVM suite, compile, Room instrumentation, install Debug on SM-G990E, and manually exercise list/detail/membership flows where environment credentials allow.

Do not commit `app/src/main/AndroidManifest.xml`. Distinguish compile/install evidence from actual device/E2E acceptance. Commit any reviewed final fixes separately.
