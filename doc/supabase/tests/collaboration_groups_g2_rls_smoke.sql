-- G2 membership/RLS smoke test for a local or confirmed development project.
--
-- This is a psql script. It never selects a project, URL, key, or service
-- credential. Supply UUIDs/emails for two existing auth.users rows:
--
--   psql ... \
--     -v user_a='00000000-0000-0000-0000-000000000001' \
--     -v user_b='00000000-0000-0000-0000-000000000002' \
--     -v invitee_email='user-b@example.test' \
--     -f collaboration_groups_g2_rls_smoke.sql
--
-- The connection must be able to SET ROLE authenticated and set the local
-- request.jwt.claim.sub value (for example, a local Supabase Postgres
-- connection). All smoke data is rolled back at the end.

\set ON_ERROR_STOP on

\if :{?user_a}
\else
    \echo 'missing required psql variable: user_a'
    \quit 2
\endif
\if :{?user_b}
\else
    \echo 'missing required psql variable: user_b'
    \quit 2
\endif
\if :{?invitee_email}
\else
    \echo 'missing required psql variable: invitee_email'
    \quit 2
\endif

set role authenticated;
begin;

select set_config('request.jwt.claim.sub', :'user_a', false);

with created as (
    select public.create_collaboration_group(
        'G2 RLS smoke group',
        'rolled back by smoke script'
    ) as envelope
)
select
    envelope ->> 'status' as create_status,
    envelope -> 'data' ->> 'group_id' as group_id
from created
\gset smoke_

do $$
begin
    if :'smoke_create_status' <> 'APPLIED' then
        raise exception 'create group did not apply: %', :'smoke_create_status';
    end if;
    if :'smoke_group_id' is null or :'smoke_group_id' = '' then
        raise exception 'create group did not return group_id';
    end if;
end
$$;

with invited as (
    select public.invite_group_member(
        :'smoke_group_id'::uuid,
        :'invitee_email'
    ) as envelope
)
select
    envelope ->> 'status' as invite_status,
    envelope -> 'data' ->> 'invite_id' as invite_id
from invited
\gset smoke_

do $$
begin
    if :'smoke_invite_status' <> 'APPLIED' then
        raise exception 'invite did not apply: %', :'smoke_invite_status';
    end if;
end
$$;

-- A sole OWNER cannot leave implicitly; deletion is an explicit command.
with owner_leave as (
    select public.leave_collaboration_group(:'smoke_group_id'::uuid) as envelope
)
select envelope ->> 'status' as sole_owner_leave_status
from owner_leave
\gset smoke_

do $$
begin
    if :'smoke_sole_owner_leave_status' <> 'INVALID_STATE' then
        raise exception 'sole owner leave was not rejected';
    end if;
end
$$;

-- Pending invitee sees the addressed invite, but no private group data or
-- another member profile before accepting.
select set_config('request.jwt.claim.sub', :'user_b', false);

select count(*) as pending_invite_rows
from public.group_invites
where id = :'smoke_invite_id'::uuid
\gset smoke_

select count(*) as pending_group_rows
from public.collaboration_groups
where id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as pending_other_profile_rows
from public.user_profiles
where user_id = :'user_a'::uuid
\gset smoke_

do $$
begin
    if :'smoke_pending_invite_rows'::integer <> 1 then
        raise exception 'pending invite is not visible to invitee';
    end if;
    if :'smoke_pending_group_rows'::integer <> 0 then
        raise exception 'pending invitee can read private group data';
    end if;
    if :'smoke_pending_other_profile_rows'::integer <> 0 then
        raise exception 'pending invitee can read unrelated profile';
    end if;
end
$$;

-- Accepting is atomic: the invite is closed and membership is created.
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

select count(*) as accepted_group_rows
from public.collaboration_groups
where id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as accepted_other_profile_rows
from public.user_profiles
where user_id = :'user_a'::uuid
\gset smoke_

do $$
begin
    if :'smoke_accepted_group_rows'::integer <> 1 then
        raise exception 'accepted member cannot read active group';
    end if;
    if :'smoke_accepted_other_profile_rows'::integer <> 1 then
        raise exception 'same active group profile is not visible';
    end if;
end
$$;

-- B is now an accepted MEMBER: it still cannot promote itself or transfer
-- ownership. These checks deliberately run after acceptance.
with self_role_change as (
    select public.change_group_member_role(
        :'smoke_group_id'::uuid,
        :'user_b'::uuid,
        'ADMIN'
    ) as envelope
), self_transfer as (
    select public.transfer_group_ownership(
        :'smoke_group_id'::uuid,
        :'user_b'::uuid
    ) as envelope
)
select
    (select envelope ->> 'status' from self_role_change) as self_role_status,
    (select envelope ->> 'status' from self_transfer) as self_transfer_status
\gset smoke_

do $$
begin
    if :'smoke_self_role_status' <> 'NOT_AUTHORIZED' then
        raise exception 'accepted member self role change was not rejected';
    end if;
    if :'smoke_self_transfer_status' <> 'NOT_AUTHORIZED' then
        raise exception 'accepted member self ownership transfer was not rejected';
    end if;
end
$$;

-- Authenticated clients have no direct collaboration-table write grants.
do $$
declare
    v_table text;
    v_update_column text;
    v_tables text[] := array[
        'collaboration_groups',
        'group_members',
        'group_invites',
        'group_tasks',
        'group_task_reminders',
        'group_reminders'
    ];
begin
    foreach v_table in array v_tables loop
        if has_table_privilege(current_user, format('public.%s', v_table), 'INSERT')
           or has_table_privilege(current_user, format('public.%s', v_table), 'UPDATE')
           or has_table_privilege(current_user, format('public.%s', v_table), 'DELETE') then
            raise exception 'authenticated has a direct write grant on %', v_table;
        end if;

        v_update_column := case v_table
            when 'collaboration_groups' then 'updated_at'
            when 'group_members' then 'role'
            when 'group_invites' then 'status'
            when 'group_tasks' then 'title'
            when 'group_task_reminders' then 'offset_seconds'
            when 'group_reminders' then 'title'
        end;

        begin
            execute format('insert into public.%I default values', v_table);
            raise exception 'direct INSERT unexpectedly succeeded on %', v_table;
        exception
            when insufficient_privilege then
                null;
        end;

        begin
            execute format(
                'update public.%I set %I = %I where false',
                v_table,
                v_update_column,
                v_update_column
            );
            raise exception 'direct UPDATE unexpectedly succeeded on %', v_table;
        exception
            when insufficient_privilege then
                null;
        end;

        begin
            execute format('delete from public.%I where false', v_table);
            raise exception 'direct DELETE unexpectedly succeeded on %', v_table;
        exception
            when insufficient_privilege then
                null;
        end;
    end loop;
end
$$;

-- Owner can change the member role and transfer ownership atomically.
select set_config('request.jwt.claim.sub', :'user_a', false);

with changed as (
    select public.change_group_member_role(
        :'smoke_group_id'::uuid,
        :'user_b'::uuid,
        'ADMIN'
    ) as envelope
)
select envelope ->> 'status' as change_role_status
from changed
\gset smoke_

do $$
begin
    if :'smoke_change_role_status' <> 'APPLIED' then
        raise exception 'owner role change did not apply: %', :'smoke_change_role_status';
    end if;
end
$$;

with transferred as (
    select public.transfer_group_ownership(
        :'smoke_group_id'::uuid,
        :'user_b'::uuid
    ) as envelope
)
select envelope ->> 'status' as transfer_status
from transferred
\gset smoke_

do $$
begin
    if :'smoke_transfer_status' <> 'APPLIED' then
        raise exception 'ownership transfer did not apply: %', :'smoke_transfer_status';
    end if;
end
$$;

select count(*) as owner_count
from public.group_members
where group_id = :'smoke_group_id'::uuid
  and role = 'OWNER'
\gset smoke_

select count(*) as old_owner_member_rows
from public.group_members
where group_id = :'smoke_group_id'::uuid
  and user_id = :'user_a'::uuid
  and role = 'MEMBER'
\gset smoke_

do $$
begin
    if :'smoke_owner_count'::integer <> 1 then
        raise exception 'ownership transfer did not preserve exactly one owner';
    end if;
    if :'smoke_old_owner_member_rows'::integer <> 1 then
        raise exception 'previous owner was not demoted atomically';
    end if;
end
$$;

-- The new owner can soft-delete. Group-backed reads disappear for both
-- accounts while the transaction is still open.
select set_config('request.jwt.claim.sub', :'user_b', false);

with deleted as (
    select public.delete_collaboration_group(:'smoke_group_id'::uuid) as envelope
)
select envelope ->> 'status' as delete_status
from deleted
\gset smoke_

do $$
begin
    if :'smoke_delete_status' <> 'APPLIED' then
        raise exception 'group delete did not apply: %', :'smoke_delete_status';
    end if;
end
$$;

select count(*) as deleted_group_rows
from public.collaboration_groups
where id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as deleted_member_rows
from public.group_members
where group_id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as deleted_invite_rows
from public.group_invites
where group_id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as deleted_task_rows
from public.group_tasks
where group_id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as deleted_task_reminder_rows
from public.group_task_reminders as gtr
join public.group_tasks as gt
  on gt.id = gtr.task_id
where gt.group_id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as deleted_reminder_rows
from public.group_reminders
where group_id = :'smoke_group_id'::uuid
\gset smoke_

select count(*) as deleted_profile_rows_as_b
from public.user_profiles
where user_id = :'user_a'::uuid
\gset smoke_

select set_config('request.jwt.claim.sub', :'user_a', false);

select count(*) as deleted_group_rows_as_a
from public.collaboration_groups
where id = :'smoke_group_id'::uuid
\gset smoke_

do $$
begin
    if :'smoke_deleted_group_rows'::integer <> 0
       or :'smoke_deleted_member_rows'::integer <> 0
       or :'smoke_deleted_invite_rows'::integer <> 0
       or :'smoke_deleted_task_rows'::integer <> 0
       or :'smoke_deleted_task_reminder_rows'::integer <> 0
       or :'smoke_deleted_reminder_rows'::integer <> 0
       or :'smoke_deleted_profile_rows_as_b'::integer <> 0
       or :'smoke_deleted_group_rows_as_a'::integer <> 0 then
        raise exception 'soft-deleted group remained in a read path';
    end if;
end
$$;

rollback;
reset role;

\echo 'G2 membership/RLS smoke passed (all writes rolled back)'
