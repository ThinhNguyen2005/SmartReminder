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

-- Authenticated clients have no direct task or task-reminder table writes.
do $$
begin
    if has_table_privilege(current_user, 'public.group_tasks', 'INSERT')
       or has_table_privilege(current_user, 'public.group_tasks', 'UPDATE')
       or has_table_privilege(current_user, 'public.group_tasks', 'DELETE')
       or has_table_privilege(current_user, 'public.group_task_reminders', 'INSERT')
       or has_table_privilege(current_user, 'public.group_task_reminders', 'UPDATE')
       or has_table_privilege(current_user, 'public.group_task_reminders', 'DELETE') then
        raise exception 'authenticated has a direct task-table write grant';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_group_status' <> 'APPLIED' then
        raise exception 'group creation did not apply: %', :'smoke_group_status';
    end if;
end
$$;

with invited as (
    select public.invite_group_member(
        :'smoke_group_id'::uuid,
        :'user_b_email'
    ) as envelope
)
select envelope ->> 'status' as invite_status
from invited
\gset smoke_

do $$
begin
    if :'smoke_invite_status' <> 'APPLIED' then
        raise exception 'member invite did not apply: %', :'smoke_invite_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_accept_status' <> 'APPLIED' then
        raise exception 'invite acceptance did not apply: %', :'smoke_accept_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_invalid_create_status' <> 'VALIDATION' then
        raise exception 'invalid create was not rejected by validation: %', :'smoke_invalid_create_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_create_task_status' <> 'APPLIED' then
        raise exception 'task creation did not apply: %', :'smoke_create_task_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_retry_task_status' <> 'APPLIED' then
        raise exception 'identical task retry was not idempotent: %', :'smoke_retry_task_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_conflicting_retry_status' <> 'CONFLICT' then
        raise exception 'client task ID reuse with a different payload was not rejected: %', :'smoke_conflicting_retry_status';
    end if;
end
$$;

select count(*) as task_count
from public.group_tasks
where id = '00000000-0000-4000-8000-000000000301'::uuid
  and group_id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as reminder_count
from public.group_task_reminders
where task_id = '00000000-0000-4000-8000-000000000301'::uuid
\gset smoke_

do $$
begin
    if :'smoke_task_count'::integer <> 1 or :'smoke_reminder_count'::integer <> 2 then
        raise exception 'idempotent create duplicated task/reminders: tasks %, reminders %',
            :'smoke_task_count', :'smoke_reminder_count';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_task_rows_as_a'::integer <> 1
       or :'smoke_reminder_rows_as_a'::integer <> 2
       or :'smoke_task_rows_as_b'::integer <> 1
       or :'smoke_reminder_rows_as_b'::integer <> 2 then
        raise exception 'current-member task reads were not visible to both actors';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_start_status' <> 'APPLIED' then
        raise exception 'assignee start did not apply: %', :'smoke_start_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_owner_complete_status' <> 'NOT_AUTHORIZED' then
        raise exception 'owner completed by proxy: %', :'smoke_owner_complete_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_stale_complete_status' <> 'CONFLICT' then
        raise exception 'stale completion did not conflict: %', :'smoke_stale_complete_status';
    end if;
end
$$;

with completed as (
    select public.complete_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        1
    ) as envelope
)
select envelope ->> 'status' as complete_status
from completed
\gset smoke_

do $$
begin
    if :'smoke_complete_status' <> 'APPLIED' then
        raise exception 'assignee completion did not apply: %', :'smoke_complete_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_reassign_status' <> 'APPLIED' then
        raise exception 'creator reassign did not apply: %', :'smoke_reassign_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_no_op_reassign_status' <> 'APPLIED' then
        raise exception 'same-assignee reassign was not an applied no-op: %', :'smoke_no_op_reassign_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_unauthorized_reassign_status' <> 'NOT_AUTHORIZED' then
        raise exception 'member reassigned creator task: %', :'smoke_unauthorized_reassign_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_edit_status' <> 'APPLIED' then
        raise exception 'full task edit did not apply: %', :'smoke_edit_status';
    end if;
end
$$;

select count(*) as edited_reminder_count
from public.group_task_reminders
where task_id = '00000000-0000-4000-8000-000000000301'::uuid
\gset smoke_
select count(*) as stale_offset_rows
from public.group_task_reminders
where task_id = '00000000-0000-4000-8000-000000000301'::uuid
  and offset_seconds = 60
\gset smoke_

do $$
begin
    if :'smoke_edited_reminder_count'::integer <> 2
       or :'smoke_stale_offset_rows'::integer <> 0 then
        raise exception 'full edit did not replace reminder offsets';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_reopen_status' <> 'APPLIED' then
        raise exception 'completed task reopen did not apply: %', :'smoke_reopen_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_start_again_status' <> 'APPLIED' then
        raise exception 'reopened task start did not apply: %', :'smoke_start_again_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_cancel_status' <> 'APPLIED' then
        raise exception 'creator cancellation did not apply: %', :'smoke_cancel_status';
    end if;
end
$$;

with reopened_cancelled as (
    select public.reopen_group_task(
        '00000000-0000-4000-8000-000000000301'::uuid,
        7
    ) as envelope
)
select envelope ->> 'status' as reopen_cancelled_status
from reopened_cancelled
\gset smoke_

do $$
begin
    if :'smoke_reopen_cancelled_status' <> 'APPLIED' then
        raise exception 'cancelled task reopen did not apply: %', :'smoke_reopen_cancelled_status';
    end if;
end
$$;

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

do $$
begin
    if :'smoke_missing_status' <> 'NOT_FOUND' then
        raise exception 'missing task did not map to NOT_FOUND: %', :'smoke_missing_status';
    end if;
end
$$;

rollback;
reset role;

\echo 'G3 task RPC/RLS smoke passed (development transaction rolled back)'
