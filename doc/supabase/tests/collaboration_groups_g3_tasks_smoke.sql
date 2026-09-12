-- ============================================================================
-- G3 task RPC/RLS smoke matrix (OPT-IN, development only)
--
-- Run only after the G1 + G2 + G3 SQL has been applied to a disposable
-- Supabase development project.  DO NOT RUN AGAINST PRODUCTION.  The
-- script refuses to start unless the operator passes:
--
--   psql ... -v g3_dev_target=development -v user_a=<uuid> \
--       -v user_b=<uuid> -v user_b_email=<account email>
--
-- The transaction is rolled back at the end.  No credentials belong in this
-- file or in source control.
-- ============================================================================

\set ON_ERROR_STOP on

\if :{?g3_dev_target}
\else
    \echo 'G3 smoke refused: pass -v g3_dev_target=development explicitly'
    \quit 1
\endif

select :'g3_dev_target' = 'development' as g3_target_is_development
\gset smoke_
\if :smoke_g3_target_is_development
\else
    \echo 'G3 smoke refused: target must be exactly development'
    \quit 1
\endif

\if :{?user_a}
\else
    \echo 'G3 smoke refused: pass -v user_a=<authenticated UUID>'
    \quit 1
\endif
\if :{?user_b}
\else
    \echo 'G3 smoke refused: pass -v user_b=<authenticated UUID>'
    \quit 1
\endif
\if :{?user_b_email}
\else
    \echo 'G3 smoke refused: pass -v user_b_email=<account email>'
    \quit 1
\endif

begin;
set local role authenticated;

-- Assertions use a temporary function so psql variables stay outside every
-- dollar-quoted function body.  psql expands named variables in normal SQL.
create or replace function pg_temp.g3_assert(
    p_condition boolean,
    p_message text
)
returns void
language plpgsql
as $assert$
begin
    if not coalesce(p_condition, false) then
        raise exception '%', p_message;
    end if;
end;
$assert$;

-- Authenticated clients have no direct task or task-reminder table writes.
select pg_temp.g3_assert(
    not has_table_privilege(current_user, 'public.group_tasks', 'INSERT')
    and not has_table_privilege(current_user, 'public.group_tasks', 'UPDATE')
    and not has_table_privilege(current_user, 'public.group_tasks', 'DELETE')
    and not has_table_privilege(current_user, 'public.group_task_reminders', 'INSERT')
    and not has_table_privilege(current_user, 'public.group_task_reminders', 'UPDATE')
    and not has_table_privilege(current_user, 'public.group_task_reminders', 'DELETE'),
    'authenticated has a direct task-table write grant'
);

-- A creates a group and invites B through the already-authorized G2 command.
select set_config('request.jwt.claim.sub', :'user_a', false);

with created as (
    select public.create_collaboration_group(
        'G3 smoke group',
        'rolled back task command matrix'
    ) as envelope
)
select
    envelope ->> 'status' as group_status,
    envelope -> 'data' ->> 'group_id' as group_id
from created
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_group_status' = 'APPLIED',
    'group creation did not apply: ' || :'smoke_group_status'
);

with invited as (
    select public.invite_group_member(
        :'smoke_group_id'::uuid,
        :'user_b_email'
    ) as envelope
)
select envelope ->> 'status' as invite_status
from invited
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_invite_status' = 'APPLIED',
    'member invite did not apply: ' || :'smoke_invite_status'
);

-- B accepts and becomes a current member.
select set_config('request.jwt.claim.sub', :'user_b', false);

select id as invite_id
from public.group_invites
where group_id = :'smoke_group_id'::uuid
  and invitee_user_id = :'user_b'::uuid
  and status = 'PENDING'
\gset smoke_

with accepted as (
    select public.respond_group_invite(
        :'smoke_invite_id'::uuid,
        true
    ) as envelope
)
select envelope ->> 'status' as accept_status
from accepted
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_accept_status' = 'APPLIED',
    'invite acceptance did not apply: ' || :'smoke_accept_status'
);

-- Invalid reminder cardinality is mapped to the typed validation envelope.
select set_config('request.jwt.claim.sub', :'user_a', false);

with invalid_create as (
    select public.create_group_task(
        '00000000-0000-4000-8000-000000000302'::uuid,
        :'smoke_group_id'::uuid,
        'Invalid offsets',
        :'user_b'::uuid,
        '2026-10-01T12:00:00Z'::timestamptz,
        '{}'::bigint[]
    ) as envelope
)
select envelope ->> 'status' as invalid_create_status
from invalid_create
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_invalid_create_status' = 'VALIDATION',
    'invalid create was not rejected by validation: ' || :'smoke_invalid_create_status'
);

-- A creates with a stable client task ID.  A second identical request is
-- idempotent and leaves exactly one task and two reminders.
with created_task as (
    select public.create_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        :'smoke_group_id'::uuid,
        'Prepare release notes',
        :'user_b'::uuid,
        '2026-10-01T12:00:00Z'::timestamptz,
        array[300, 60]::bigint[],
        'G3 task'
    ) as envelope
)
select envelope ->> 'status' as create_task_status
from created_task
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_create_task_status' = 'APPLIED',
    'task creation did not apply: ' || :'smoke_create_task_status'
);

with retried_task as (
    select public.create_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        :'smoke_group_id'::uuid,
        'Prepare release notes',
        :'user_b'::uuid,
        '2026-10-01T12:00:00Z'::timestamptz,
        array[60, 300]::bigint[],
        'G3 task'
    ) as envelope
)
select envelope ->> 'status' as retry_task_status
from retried_task
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_retry_task_status' = 'APPLIED',
    'identical task retry was not idempotent: ' || :'smoke_retry_task_status'
);

with conflicting_retry as (
    select public.create_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        :'smoke_group_id'::uuid,
        'Different payload',
        :'user_b'::uuid,
        '2026-10-01T12:00:00Z'::timestamptz,
        array[60, 300]::bigint[],
        'G3 task'
    ) as envelope
)
select envelope ->> 'status' as conflicting_retry_status
from conflicting_retry
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_conflicting_retry_status' = 'CONFLICT',
    'client task ID reuse with a different payload was not rejected: ' || :'smoke_conflicting_retry_status'
);

select count(*) as task_count
from public.group_tasks
where id = '00000000-0000-4000-8000-000000000301'::uuid
  and group_id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as reminder_count
from public.group_task_reminders
where task_id = '00000000-0000-4000-8000-000000000301'::uuid
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_task_count'::integer = 1
    and :'smoke_reminder_count'::integer = 2,
    'idempotent create duplicated task/reminders: tasks '
        || :'smoke_task_count' || ', reminders ' || :'smoke_reminder_count'
);

-- Both current members can read the task and its offsets.
select count(*) as task_rows_as_a
from public.group_tasks
where group_id = :'smoke_group_id'::uuid
\gset smoke_
select count(*) as reminder_rows_as_a
from public.group_task_reminders as gtr
join public.group_tasks as gt on gt.id = gtr.task_id
where gt.group_id = :'smoke_group_id'::uuid
\gset smoke_

select set_config('request.jwt.claim.sub', :'user_b', false);

select count(*) as task_rows_as_b
from public.group_tasks
where group_id = :'smoke_group_id'::uuid
\gset smoke_
select count(*) as reminder_rows_as_b
from public.group_task_reminders as gtr
join public.group_tasks as gt on gt.id = gtr.task_id
where gt.group_id = :'smoke_group_id'::uuid
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_task_rows_as_a'::integer = 1
    and :'smoke_reminder_rows_as_a'::integer = 2
    and :'smoke_task_rows_as_b'::integer = 1
    and :'smoke_reminder_rows_as_b'::integer = 2,
    'current-member task reads were not visible to both actors'
);

-- B is the assignee and may start.  A, despite being Owner, cannot complete
-- by proxy.  B's stale expected_version completion is a CONFLICT; the current
-- version applies.
with started as (
    select public.start_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        0
    ) as envelope
)
select envelope ->> 'status' as start_status
from started
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_start_status' = 'APPLIED',
    'assignee start did not apply: ' || :'smoke_start_status'
);

with invalid_start as (
    select public.start_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        1
    ) as envelope
)
select envelope ->> 'status' as invalid_start_status
from invalid_start
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_invalid_start_status' = 'INVALID_STATE',
    'starting an IN_PROGRESS task was not rejected: ' || :'smoke_invalid_start_status'
);

select set_config('request.jwt.claim.sub', :'user_a', false);
with owner_complete as (
    select public.complete_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        1
    ) as envelope
)
select envelope ->> 'status' as owner_complete_status
from owner_complete
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_owner_complete_status' = 'NOT_AUTHORIZED',
    'owner completed by proxy: ' || :'smoke_owner_complete_status'
);

select set_config('request.jwt.claim.sub', :'user_b', false);
with stale_complete as (
    select public.complete_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        0
    ) as envelope
)
select envelope ->> 'status' as stale_complete_status
from stale_complete
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_stale_complete_status' = 'CONFLICT',
    'stale completion did not conflict: ' || :'smoke_stale_complete_status'
);

with completed as (
    select public.complete_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        1
    ) as envelope
)
select envelope ->> 'status' as complete_status
from completed
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_complete_status' = 'APPLIED',
    'assignee completion did not apply: ' || :'smoke_complete_status'
);

with invalid_complete as (
    select public.complete_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        2
    ) as envelope
)
select envelope ->> 'status' as invalid_complete_status
from invalid_complete
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_invalid_complete_status' = 'INVALID_STATE',
    'completing a COMPLETED task was not rejected: ' || :'smoke_invalid_complete_status'
);

-- A can reassign as creator.  B cannot reassign the creator's task.  The
-- reassign command bumps version and a same-assignee retry is a no-op.
select set_config('request.jwt.claim.sub', :'user_a', false);
with reassigned as (
    select public.reassign_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        :'user_a'::uuid,
        2
    ) as envelope
)
select envelope ->> 'status' as reassign_status
from reassigned
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_reassign_status' = 'APPLIED',
    'creator reassign did not apply: ' || :'smoke_reassign_status'
);

with no_op_reassign as (
    select public.reassign_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        :'user_a'::uuid,
        3
    ) as envelope
)
select envelope ->> 'status' as no_op_reassign_status
from no_op_reassign
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_no_op_reassign_status' = 'APPLIED',
    'same-assignee reassign was not an applied no-op: ' || :'smoke_no_op_reassign_status'
);

select set_config('request.jwt.claim.sub', :'user_b', false);
with unauthorized_reassign as (
    select public.reassign_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        :'user_b'::uuid,
        3
    ) as envelope
)
select envelope ->> 'status' as unauthorized_reassign_status
from unauthorized_reassign
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_unauthorized_reassign_status' = 'NOT_AUTHORIZED',
    'member reassigned creator task: ' || :'smoke_unauthorized_reassign_status'
);

-- Full edit replaces all offsets in the same transaction and increments v3->v4.
select set_config('request.jwt.claim.sub', :'user_a', false);
with edited as (
    select public.edit_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        'Prepare final release notes',
        :'user_b'::uuid,
        '2026-10-02T12:00:00Z'::timestamptz,
        array[900, 120]::bigint[],
        3,
        'Updated task details'
    ) as envelope
)
select envelope ->> 'status' as edit_status
from edited
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_edit_status' = 'APPLIED',
    'full task edit did not apply: ' || :'smoke_edit_status'
);

select count(*) as edited_reminder_count
from public.group_task_reminders
where task_id = '00000000-0000-4000-8000-000000000301'::uuid
\gset smoke_
select count(*) as stale_offset_rows
from public.group_task_reminders
where task_id = '00000000-0000-4000-8000-000000000301'::uuid
  and offset_seconds = 60
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_edited_reminder_count'::integer = 2
    and :'smoke_stale_offset_rows'::integer = 0,
    'full edit did not replace reminder offsets'
);

-- Reopen, start, cancel, and reopen again cover every remaining state edge.
with reopened as (
    select public.reopen_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        4
    ) as envelope
)
select envelope ->> 'status' as reopen_status
from reopened
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_reopen_status' = 'APPLIED',
    'completed task reopen did not apply: ' || :'smoke_reopen_status'
);

select set_config('request.jwt.claim.sub', :'user_b', false);
with started_again as (
    select public.start_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        5
    ) as envelope
)
select envelope ->> 'status' as start_again_status
from started_again
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_start_again_status' = 'APPLIED',
    'reopened task start did not apply: ' || :'smoke_start_again_status'
);

select set_config('request.jwt.claim.sub', :'user_a', false);
with cancelled as (
    select public.cancel_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        6
    ) as envelope
)
select envelope ->> 'status' as cancel_status
from cancelled
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_cancel_status' = 'APPLIED',
    'creator cancellation did not apply: ' || :'smoke_cancel_status'
);

with reopened_cancelled as (
    select public.reopen_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        7
    ) as envelope
)
select envelope ->> 'status' as reopen_cancelled_status
from reopened_cancelled
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_reopen_cancelled_status' = 'APPLIED',
    'cancelled task reopen did not apply: ' || :'smoke_reopen_cancelled_status'
);

-- A removes B.  The removed member can no longer read the task or reminders,
-- and the task command maps the missing membership to NOT_AUTHORIZED.
select set_config('request.jwt.claim.sub', :'user_a', false);
with removed as (
    select public.remove_group_member(
        :'smoke_group_id'::uuid,
        :'user_b'::uuid
    ) as envelope
)
select envelope ->> 'status' as remove_member_status
from removed
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_remove_member_status' = 'APPLIED',
    'member removal did not apply: ' || :'smoke_remove_member_status'
);

select set_config('request.jwt.claim.sub', :'user_b', false);
select count(*) as removed_task_rows_as_b
from public.group_tasks
where id = '00000000-0000-4000-8000-000000000301'::uuid
\gset smoke_
select count(*) as removed_reminder_rows_as_b
from public.group_task_reminders
where task_id = '00000000-0000-4000-8000-000000000301'::uuid
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_removed_task_rows_as_b'::integer = 0
    and :'smoke_removed_reminder_rows_as_b'::integer = 0,
    'removed member retained task visibility'
);

with removed_member_start as (
    select public.start_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        8
    ) as envelope
)
select envelope ->> 'status' as removed_member_start_status
from removed_member_start
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_removed_member_start_status' = 'NOT_AUTHORIZED',
    'removed member could still mutate the task: '
        || :'smoke_removed_member_start_status'
);

-- Missing tasks map to NOT_FOUND without touching any row.
with missing as (
    select public.start_group_task(
        '00000000-0000-4000-8000-000000000399'::uuid,
        0
    ) as envelope
)
select envelope ->> 'status' as missing_status
from missing
\gset smoke_

select pg_temp.g3_assert(
    :'smoke_missing_status' = 'NOT_FOUND',
    'missing task did not map to NOT_FOUND: ' || :'smoke_missing_status'
);

rollback;
reset role;

\echo 'G3 task RPC/RLS smoke passed (development transaction rolled back)'
