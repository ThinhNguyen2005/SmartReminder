# Task 2 report - Supabase G2 membership schema, RPCs, and SQL checks

## Files changed

- `doc/supabase/collaboration_groups_g2_membership.sql`
  - Additive G2 migration on top of `collaboration_groups_v1.sql`.
  - Adds profiles, backfill/trigger, active-group predicates, RLS/grants, typed envelopes, and all nine membership RPCs.
- `doc/supabase/tests/collaboration_groups_g2_structural.ps1`
  - Repeatable local structural/security checker. It does not connect to Supabase or apply SQL.
- `doc/supabase/tests/collaboration_groups_g2_rls_smoke.sql`
  - Repeatable two-account psql smoke flow with caller-supplied UUID/email variables and a final rollback.
- `.superpowers/sdd/2026-09-10-collaboration-g2-membership/task-2-report.md`
  - This report.

The pre-existing `app/src/main/AndroidManifest.xml` modification and all unrelated `.superpowers` files were excluded from the commit. No Kotlin, Room, UI, manifest, or Supabase client/config file was changed.

## Schema and profile decisions

- `public.user_profiles(user_id, display_name, avatar_url, updated_at)` references `auth.users` with `on delete cascade`.
- Existing accounts are backfilled from `auth.users.raw_user_meta_data` and email local-part fallback. A security-definer `after insert on auth.users` trigger creates the minimum profile for new accounts; an update trigger owns `updated_at`.
- Profile SELECT permits self or a target sharing an active group with the authenticated viewer. Profile UPDATE permits only the viewer's own row; there is no authenticated INSERT/DELETE policy or grant.
- G1's owner partial unique index is retained idempotently. Active-group predicates join `collaboration_groups` and require `deleted_at is null`.
- All six G1 collaboration tables remain RLS-enabled. Authenticated/anonymous table privileges are revoked and only SELECT is granted to `authenticated`; collaboration INSERT/UPDATE/DELETE/TRUNCATE paths exist only through the RPCs.
- Invite reads use a security-definer active-group helper so a pending invitee can see the addressed invite without seeing private group rows. Soft-deleted groups are filtered from groups, members, invites, tasks, task reminders, group reminders, and same-group profile reads.

## RPC and envelope decisions

The nine public RPCs are `SECURITY DEFINER`, pin `search_path = ''`, are executable by `authenticated` only, derive the actor from `auth.uid()`, and return JSON envelopes shaped as `{ status, error: { code, detail } | null, data }`. Status/error names preserve Task 1's domain vocabulary, including `APPLIED`, `NOT_AUTHORIZED`, `MEMBER_NOT_FOUND`, `ALREADY_MEMBER`, `INVITE_ALREADY_PENDING`, and `INVALID_STATE`.

- `create_collaboration_group(name, description)` inserts the group and the caller's single OWNER row atomically.
- `update_collaboration_group(group_id, name, description)` locks the group and caller membership; OWNER/ADMIN only.
- `invite_group_member(group_id, invitee_email)` locks the group, caller/target membership rows, resolves an existing `auth.users` account, and distinguishes member/pending-invite conflicts.
- `respond_group_invite(invite_id, accept)` locks the group, invite, and invitee membership row; accept inserts MEMBER and closes the invite in one transaction, while decline closes it without membership.
- `change_group_member_role` permits only the OWNER to set a non-owner target to ADMIN or MEMBER.
- `remove_group_member` enforces OWNER/ADMIN removal rules and never deletes an OWNER row.
- `transfer_group_ownership` locks all group membership rows, checks exactly one current OWNER, demotes the old OWNER before promoting the accepted target, and returns one OWNER on success.
- `leave_collaboration_group` locks all membership rows; non-owners leave, while any OWNER receives `INVALID_STATE` and must transfer ownership or call the explicit delete RPC. This keeps `leave` from implicitly deleting a sole-owner group.
- `delete_collaboration_group` is OWNER-only, locks membership rows, validates exactly one OWNER, and soft-deletes the group.

All membership/ownership RPCs lock the aggregate group first and membership rows in deterministic `user_id` order where applicable, reducing cross-command deadlock risk.

## TDD and local verification evidence

### RED

Before creating the migration, the structural checker was run:

```text
& .\doc\supabase\tests\collaboration_groups_g2_structural.ps1
```

Expected feature-absent failure:

```text
[FAIL] migration exists: D:\SmartReminder\doc\supabase\collaboration_groups_g2_membership.sql
EXIT=1
```

### GREEN

After the migration and smoke script were added, the same checker passed every transaction, profile, RLS, grant, soft-delete, envelope, RPC, lock, and smoke-script check:

```text
& .\doc\supabase\tests\collaboration_groups_g2_structural.ps1
```

Result: `[OK] G2 structural checks passed`, exit code `0`.

The checker is static by design; it does not claim live PostgreSQL execution.

Formatting and repository regression checks:

```text
git diff --check
```

Result: exit code `0`.

```text
./gradlew.bat :app:testDebugUnitTest --rerun-tasks
```

Result: `BUILD SUCCESSFUL` in 54s; 26 tasks executed. This is a SQL/docs-only change, so no Android source was modified.

### Reviewer-required leave rule correction

The first implementation allowed a sole OWNER to soft-delete through `leave_collaboration_group`. A static assertion was added before the correction and correctly produced:

```text
[FAIL] sole owner leave requires explicit delete or transfer
EXIT=1
```

The RPC was then changed so every OWNER leave returns `INVALID_STATE` with an explicit transfer/delete detail. The rollback smoke script now calls the sole-owner leave path and asserts `INVALID_STATE`; the structural checker is GREEN again.

### Reviewer round 1 fix - smoke order and coverage

The first smoke flow attempted B's unauthorized role/ownership actions while B was still only a pending invitee. The checks now run after B accepts the invite, so they exercise a real MEMBER authorization boundary.

The smoke script also now:

- checks authenticated INSERT/UPDATE/DELETE privileges and attempts denied DML for every G1 collaboration table (`collaboration_groups`, `group_members`, `group_invites`, `group_tasks`, `group_task_reminders`, and `group_reminders`);
- checks post-soft-delete invisibility for every collaboration table read path plus the cross-group `user_profiles` path;
- rolls all setup/mutation data back at the end as before.

The structural checker was tightened from broad cross-file regexes to exact policy names/table targets and specific smoke aliases/table lists. Before the smoke changes it produced the expected RED failures for member-check ordering, direct-write table enumeration, and missing deleted invite/task/reminder/profile reads. After the changes:

```text
[OK] member authorization checks run after invite acceptance
[OK] smoke direct-write denial names collaboration_groups
[OK] smoke direct-write denial names group_members
[OK] smoke direct-write denial names group_invites
[OK] smoke direct-write denial names group_tasks
[OK] smoke direct-write denial names group_task_reminders
[OK] smoke direct-write denial names group_reminders
[OK] smoke soft-delete read assertion names collaboration_groups
[OK] smoke soft-delete read assertion names group_members
[OK] smoke soft-delete read assertion names group_invites
[OK] smoke soft-delete read assertion names group_tasks
[OK] smoke soft-delete read assertion names group_task_reminders
[OK] smoke soft-delete read assertion names group_reminders
[OK] smoke soft-delete read assertion names user_profiles
[OK] G2 structural checks passed
```

Final verification after the reviewer fix:

```text
& .\doc\supabase\tests\collaboration_groups_g2_structural.ps1
```

Result: exit code `0` (`[OK] G2 structural checks passed`). `git diff --check` also exited `0`.

```text
.\gradlew.bat test
```

Result: `BUILD SUCCESSFUL`; 26 actionable tasks were up-to-date. The first sandboxed invocation could not open the existing Gradle wrapper lock, so the same command was rerun with approved cache access and passed.

## Live smoke status and blocker

`doc/supabase/tests/collaboration_groups_g2_rls_smoke.sql` is intentionally not applied. It requires a confirmed local/dev Supabase target, two existing `auth.users` UUIDs, and caller-supplied psql variables (`user_a`, `user_b`, `invitee_email`). The workspace has no `psql`/`pg_isready`, and no dev credentials/target were supplied. The existing hard-coded project configuration was not used or modified.

## Commit

`feat: add Supabase G2 membership commands` (the final hash is returned in the task handoff; this report is included in that isolated commit).

## Self-review

- G1 remains untouched; the migration is additive and idempotent for the existing owner index, policies, helper functions, grants, and RPC definitions.
- No caller-supplied actor ID or caller-selected OWNER role is accepted by any command.
- Direct authenticated collaboration writes are denied at both table-grant and RLS-policy boundaries.
- Pending invite privacy, post-accept MEMBER authorization, six-table direct-write denial, sole-owner leave rejection, and deleted-group filtering across all read paths are checked explicitly in the smoke flow.
- Live SQL parser/database execution, concurrency reproduction, and two-account RLS execution remain unverified until a confirmed dev target is supplied; those are the only outstanding verification gaps.
