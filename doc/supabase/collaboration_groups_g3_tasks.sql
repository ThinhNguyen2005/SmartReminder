-- ============================================================================
-- Cue (SmartReminder) - Collaboration Groups G3 Task Commands
--
-- Apply after collaboration_groups_v1.sql and
-- collaboration_groups_g2_membership.sql.  The client-generated task UUID is
-- used as group_tasks.id, so retrying create_group_task is idempotent without a
-- second idempotency table.  All task writes below run in the caller's
-- transaction and are exposed only through SECURITY DEFINER RPCs.
--
-- Lock order is part of the command contract: every command acquires the
-- collaboration group row first, then the task row, then member rows.  The
-- create retry path follows the same order before it re-reads the task.
--
-- This migration deliberately does not create an outbox, replay queue,
-- delivery scheduler, standalone GroupReminder, or FCM integration.
-- ============================================================================

begin;

-- G1 already creates these tables.  Keeping the guards makes this additive
-- migration safe to re-run after a partially applied development reset.
create table if not exists public.group_tasks (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null
        references public.collaboration_groups(id) on delete cascade,
    title text not null
        check (btrim(title) <> ''),
    description text,
    created_by uuid not null
        references auth.users(id) on delete restrict,
    assignee_id uuid not null
        references auth.users(id) on delete restrict,
    due_at timestamptz not null,
    status text not null default 'TODO'
        check (status in ('TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    version bigint not null default 0
        check (version >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.group_task_reminders (
    task_id uuid not null
        references public.group_tasks(id) on delete cascade,
    offset_seconds bigint not null
        check (offset_seconds > 0),
    primary key (task_id, offset_seconds)
);

create index if not exists group_tasks_group_id_idx
    on public.group_tasks (group_id);

create index if not exists group_tasks_assignee_due_at_idx
    on public.group_tasks (assignee_id, due_at);

-- Return a validation detail rather than raising so every RPC can preserve the
-- established typed mutation envelope.  The database CHECK/PK constraints
-- remain the final invariant if a future command bypasses this helper.
create or replace function private.validate_group_task_reminder_offsets(
    p_offsets bigint[]
)
returns text
language plpgsql
immutable
security definer
set search_path = ''
as $function$
declare
    v_count integer;
begin
    if coalesce(array_ndims(p_offsets), 0) <> 1 then
        return 'Reminder offsets must contain between 1 and 5 values';
    end if;

    v_count := coalesce(array_length(p_offsets, 1), 0);
    if v_count not between 1 and 5 then
        return 'Reminder offsets must contain between 1 and 5 values';
    end if;

    if exists (
        select 1
        from unnest(p_offsets) as offsets(offset_seconds)
        where offset_seconds is null
           or offset_seconds <= 0
    ) then
        return 'Reminder offsets must be positive';
    end if;

    if (
        select count(*)
        from unnest(p_offsets) as offsets(offset_seconds)
    ) <> (
        select count(distinct offset_seconds)
        from unnest(p_offsets) as offsets(offset_seconds)
    ) then
        return 'Reminder offsets must be unique';
    end if;

    return null;
end;
$function$;

revoke all on function private.validate_group_task_reminder_offsets(bigint[]) from public;

-- Read access remains current-member-only.  There are intentionally no write
-- policies: authenticated clients use only the RPCs below.
alter table public.group_tasks enable row level security;
alter table public.group_task_reminders enable row level security;

drop policy if exists group_tasks_select_member on public.group_tasks;
create policy group_tasks_select_member
on public.group_tasks
for select
to authenticated
using (
    private.is_active_collaboration_group(group_id)
    and private.is_collaboration_group_member(group_id)
);

drop policy if exists group_task_reminders_select_member on public.group_task_reminders;
create policy group_task_reminders_select_member
on public.group_task_reminders
for select
to authenticated
using (private.is_collaboration_task_member(task_id));

revoke all on table public.group_tasks from public;
revoke all on table public.group_tasks from anon, authenticated;
grant select on table public.group_tasks to authenticated;

revoke all on table public.group_task_reminders from public;
revoke all on table public.group_task_reminders from anon, authenticated;
grant select on table public.group_task_reminders to authenticated;

-- --------------------------------------------------------------------------
-- Create: the caller supplies the stable client task ID (p_task_id).  A retry
-- with the same ID and the same payload returns APPLIED without another row;
-- reuse with a different payload returns CONFLICT.
-- --------------------------------------------------------------------------

create or replace function public.create_group_task(
    p_task_id uuid,
    p_group_id uuid,
    p_title text,
    p_assignee_id uuid,
    p_due_at timestamptz,
    p_reminder_offsets_seconds bigint[],
    p_description text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_group public.collaboration_groups%rowtype;
    v_actor_role text;
    v_assignee_role text;
    v_validation text;
    v_inserted_task_id uuid;
    v_existing public.group_tasks%rowtype;
    v_requested_offsets bigint[];
    v_existing_offsets bigint[];
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_task_id is null
       or p_group_id is null
       or p_title is null
       or pg_catalog.btrim(p_title) = ''
       or p_assignee_id is null
       or p_due_at is null then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            'Task ID, group, title, assignee, and deadline are required'
        );
    end if;

    v_validation := private.validate_group_task_reminder_offsets(
        p_reminder_offsets_seconds
    );
    if v_validation is not null then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            v_validation
        );
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', p_group_id)
        );
    end if;

    -- Retry paths lock the group before attempting to lock the existing task.
    -- A new task has no row to lock yet; the insert below remains idempotent.
    select *
    into v_existing
    from public.group_tasks
    where id = p_task_id
    for update;

    -- Lock actor and assignee in a stable order before checking either role.
    perform 1
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id in (v_actor, p_assignee_id)
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor;

    if v_actor_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', p_group_id)
        );
    end if;

    select gm.role
    into v_assignee_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = p_assignee_id;

    if v_assignee_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'MEMBER_NOT_FOUND',
            'The assignee is not a current group member',
            jsonb_build_object('group_id', p_group_id)
        );
    end if;

    select array_agg(offset_seconds order by offset_seconds)
    into v_requested_offsets
    from unnest(p_reminder_offsets_seconds) as offsets(offset_seconds);

    insert into public.group_tasks (
        id,
        group_id,
        title,
        description,
        created_by,
        assignee_id,
        due_at,
        status,
        version
    )
    values (
        p_task_id,
        p_group_id,
        pg_catalog.btrim(p_title),
        p_description,
        v_actor,
        p_assignee_id,
        p_due_at,
        'TODO',
        0
    )
    on conflict (id) do nothing
    returning id into v_inserted_task_id;

    if v_inserted_task_id is null then
        select *
        into v_existing
        from public.group_tasks
        where id = p_task_id
        for update;

        if not found then
            return private.collaboration_mutation_envelope(
                'NOT_FOUND',
                'NOT_FOUND',
                null,
                jsonb_build_object('group_id', p_group_id)
            );
        end if;

        if v_existing.group_id <> p_group_id
           or v_existing.created_by <> v_actor then
            return private.collaboration_mutation_envelope(
                'NOT_AUTHORIZED',
                'NOT_AUTHORIZED',
                'The client task ID is not owned by this actor and group',
                jsonb_build_object('group_id', p_group_id)
            );
        end if;

        select coalesce(
            array_agg(gtr.offset_seconds order by gtr.offset_seconds),
            '{}'::bigint[]
        )
        into v_existing_offsets
        from public.group_task_reminders as gtr
        where gtr.task_id = p_task_id;

        if v_existing.title = pg_catalog.btrim(p_title)
           and v_existing.description is not distinct from p_description
           and v_existing.assignee_id = p_assignee_id
           and v_existing.due_at = p_due_at
           and v_existing_offsets = v_requested_offsets then
            return private.collaboration_mutation_envelope(
                'APPLIED',
                null,
                null,
                jsonb_build_object(
                    'task_id', p_task_id,
                    'group_id', p_group_id,
                    'version', v_existing.version,
                    'idempotent', true
                )
            );
        end if;

        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The client task ID was already used with a different payload',
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', p_group_id,
                'version', v_existing.version
            )
        );
    end if;

    insert into public.group_task_reminders (task_id, offset_seconds)
    select p_task_id, offsets.offset_seconds
    from unnest(p_reminder_offsets_seconds) as offsets(offset_seconds);

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'task_id', p_task_id,
            'group_id', p_group_id,
            'version', 0,
            'idempotent', false
        )
    );
exception
    when deadlock_detected then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task command could not acquire locks; retry',
            jsonb_build_object('group_id', p_group_id)
        );
end;
$function$;

-- --------------------------------------------------------------------------
-- Full edit: content, assignee, absolute deadline, and reminder offsets.
-- --------------------------------------------------------------------------

create or replace function public.edit_group_task(
    p_task_id uuid,
    p_title text,
    p_assignee_id uuid,
    p_due_at timestamptz,
    p_reminder_offsets_seconds bigint[],
    p_expected_version bigint,
    p_description text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_task public.group_tasks%rowtype;
    v_group public.collaboration_groups%rowtype;
    v_task_group_id uuid;
    v_actor_role text;
    v_assignee_role text;
    v_validation text;
    v_new_version bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_task_id is null
       or p_title is null
       or pg_catalog.btrim(p_title) = ''
       or p_assignee_id is null
       or p_due_at is null
       or p_expected_version is null
       or p_expected_version < 0 then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            'Task ID, title, assignee, deadline, and expected version are required'
        );
    end if;

    v_validation := private.validate_group_task_reminder_offsets(
        p_reminder_offsets_seconds
    );
    if v_validation is not null then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            v_validation
        );
    end if;

    select gt.group_id
    into v_task_group_id
    from public.group_tasks as gt
    where gt.id = p_task_id;

    if not found then
        return private.collaboration_mutation_envelope('NOT_FOUND', 'NOT_FOUND');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = v_task_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select *
    into v_task
    from public.group_tasks
    where id = p_task_id
      and group_id = v_task_group_id
    for update;

    if not found then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    perform 1
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id in (v_actor, p_assignee_id)
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = v_actor;

    if v_actor_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_actor <> v_task.created_by
       and v_actor_role not in ('OWNER', 'ADMIN') then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_task.version <> p_expected_version then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', v_task.group_id,
                'current_version', v_task.version
            )
        );
    end if;

    select gm.role
    into v_assignee_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = p_assignee_id;

    if v_assignee_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'MEMBER_NOT_FOUND',
            'The assignee is not a current group member',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    v_new_version := v_task.version + 1;

    update public.group_tasks
    set title = pg_catalog.btrim(p_title),
        description = p_description,
        assignee_id = p_assignee_id,
        due_at = p_due_at,
        version = version + 1,
        updated_at = now()
    where id = p_task_id
      and version = p_expected_version;

    if not found then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    -- Replacement is in this same transaction as the task/version update.
    delete from public.group_task_reminders
    where task_id = p_task_id;

    insert into public.group_task_reminders (task_id, offset_seconds)
    select p_task_id, offsets.offset_seconds
    from unnest(p_reminder_offsets_seconds) as offsets(offset_seconds);

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'task_id', p_task_id,
            'group_id', v_task.group_id,
            'version', v_new_version
        )
    );
exception
    when deadlock_detected then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task command could not acquire locks; retry',
            jsonb_build_object('group_id', v_task_group_id)
        );
end;
$function$;

-- --------------------------------------------------------------------------
-- Reassign: creator/Owner/Admin may change the sole current-member assignee.
-- Reassigning to the existing assignee is an APPLIED no-op and does not bump
-- the optimistic version because no authoritative state changed.
-- --------------------------------------------------------------------------

create or replace function public.reassign_group_task(
    p_task_id uuid,
    p_assignee_id uuid,
    p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_task public.group_tasks%rowtype;
    v_group public.collaboration_groups%rowtype;
    v_task_group_id uuid;
    v_actor_role text;
    v_assignee_role text;
    v_new_version bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_task_id is null
       or p_assignee_id is null
       or p_expected_version is null
       or p_expected_version < 0 then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            'Task ID, assignee, and expected version are required'
        );
    end if;

    select gt.group_id
    into v_task_group_id
    from public.group_tasks as gt
    where gt.id = p_task_id;

    if not found then
        return private.collaboration_mutation_envelope('NOT_FOUND', 'NOT_FOUND');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = v_task_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select *
    into v_task
    from public.group_tasks
    where id = p_task_id
      and group_id = v_task_group_id
    for update;

    if not found then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    perform 1
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id in (v_actor, p_assignee_id)
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = v_actor;

    if v_actor_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_actor <> v_task.created_by
       and v_actor_role not in ('OWNER', 'ADMIN') then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_task.version <> p_expected_version then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', v_task.group_id,
                'current_version', v_task.version
            )
        );
    end if;

    select gm.role
    into v_assignee_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = p_assignee_id;

    if v_assignee_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'MEMBER_NOT_FOUND',
            'The assignee is not a current group member',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_task.assignee_id = p_assignee_id then
        return private.collaboration_mutation_envelope(
            'APPLIED',
            null,
            null,
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', v_task.group_id,
                'version', v_task.version,
                'no_op', true
            )
        );
    end if;

    v_new_version := v_task.version + 1;

    update public.group_tasks
    set assignee_id = p_assignee_id,
        version = version + 1,
        updated_at = now()
    where id = p_task_id
      and version = p_expected_version;

    if not found then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'task_id', p_task_id,
            'group_id', v_task.group_id,
            'assignee_id', p_assignee_id,
            'version', v_new_version
        )
    );
exception
    when deadlock_detected then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task command could not acquire locks; retry',
            jsonb_build_object('group_id', v_task_group_id)
        );
end;
$function$;

-- --------------------------------------------------------------------------
-- Start: only the current assignee may move TODO -> IN_PROGRESS.
-- --------------------------------------------------------------------------

create or replace function public.start_group_task(
    p_task_id uuid,
    p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_task public.group_tasks%rowtype;
    v_group public.collaboration_groups%rowtype;
    v_task_group_id uuid;
    v_actor_role text;
    v_new_version bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_task_id is null
       or p_expected_version is null
       or p_expected_version < 0 then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            'Task ID and expected version are required'
        );
    end if;

    select gt.group_id
    into v_task_group_id
    from public.group_tasks as gt
    where gt.id = p_task_id;

    if not found then
        return private.collaboration_mutation_envelope('NOT_FOUND', 'NOT_FOUND');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = v_task_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select *
    into v_task
    from public.group_tasks
    where id = p_task_id
      and group_id = v_task_group_id
    for update;

    if not found then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = v_actor
    for update;

    if v_actor_role is null or v_actor <> v_task.assignee_id then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_task.version <> p_expected_version then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', v_task.group_id,
                'current_version', v_task.version
            )
        );
    end if;

    if v_task.status <> 'TODO' then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Only TODO tasks can be started',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    v_new_version := v_task.version + 1;

    update public.group_tasks
    set status = 'IN_PROGRESS',
        version = version + 1,
        updated_at = now()
    where id = p_task_id
      and version = p_expected_version;

    if not found then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'task_id', p_task_id,
            'group_id', v_task.group_id,
            'status', 'IN_PROGRESS',
            'version', v_new_version
        )
    );
exception
    when deadlock_detected then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task command could not acquire locks; retry',
            jsonb_build_object('group_id', v_task_group_id)
        );
end;
$function$;

-- --------------------------------------------------------------------------
-- Complete: only the current assignee may move TODO/IN_PROGRESS -> COMPLETED.
-- --------------------------------------------------------------------------

create or replace function public.complete_group_task(
    p_task_id uuid,
    p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_task public.group_tasks%rowtype;
    v_group public.collaboration_groups%rowtype;
    v_task_group_id uuid;
    v_actor_role text;
    v_new_version bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_task_id is null
       or p_expected_version is null
       or p_expected_version < 0 then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            'Task ID and expected version are required'
        );
    end if;

    select gt.group_id
    into v_task_group_id
    from public.group_tasks as gt
    where gt.id = p_task_id;

    if not found then
        return private.collaboration_mutation_envelope('NOT_FOUND', 'NOT_FOUND');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = v_task_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select *
    into v_task
    from public.group_tasks
    where id = p_task_id
      and group_id = v_task_group_id
    for update;

    if not found then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = v_actor
    for update;

    if v_actor_role is null or v_actor <> v_task.assignee_id then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_task.version <> p_expected_version then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', v_task.group_id,
                'current_version', v_task.version
            )
        );
    end if;

    if v_task.status not in ('TODO', 'IN_PROGRESS') then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Only TODO or IN_PROGRESS tasks can be completed',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    v_new_version := v_task.version + 1;

    update public.group_tasks
    set status = 'COMPLETED',
        version = version + 1,
        updated_at = now()
    where id = p_task_id
      and version = p_expected_version;

    if not found then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'task_id', p_task_id,
            'group_id', v_task.group_id,
            'status', 'COMPLETED',
            'version', v_new_version
        )
    );
exception
    when deadlock_detected then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task command could not acquire locks; retry',
            jsonb_build_object('group_id', v_task_group_id)
        );
end;
$function$;

-- --------------------------------------------------------------------------
-- Cancel: creator/Owner/Admin may move TODO/IN_PROGRESS -> CANCELLED.
-- --------------------------------------------------------------------------

create or replace function public.cancel_group_task(
    p_task_id uuid,
    p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_task public.group_tasks%rowtype;
    v_group public.collaboration_groups%rowtype;
    v_task_group_id uuid;
    v_actor_role text;
    v_new_version bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_task_id is null
       or p_expected_version is null
       or p_expected_version < 0 then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            'Task ID and expected version are required'
        );
    end if;

    select gt.group_id
    into v_task_group_id
    from public.group_tasks as gt
    where gt.id = p_task_id;

    if not found then
        return private.collaboration_mutation_envelope('NOT_FOUND', 'NOT_FOUND');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = v_task_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select *
    into v_task
    from public.group_tasks
    where id = p_task_id
      and group_id = v_task_group_id
    for update;

    if not found then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = v_actor
    for update;

    if v_actor_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_actor <> v_task.created_by
       and v_actor_role not in ('OWNER', 'ADMIN') then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_task.version <> p_expected_version then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', v_task.group_id,
                'current_version', v_task.version
            )
        );
    end if;

    if v_task.status not in ('TODO', 'IN_PROGRESS') then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Only TODO or IN_PROGRESS tasks can be cancelled',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    v_new_version := v_task.version + 1;

    update public.group_tasks
    set status = 'CANCELLED',
        version = version + 1,
        updated_at = now()
    where id = p_task_id
      and version = p_expected_version;

    if not found then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'task_id', p_task_id,
            'group_id', v_task.group_id,
            'status', 'CANCELLED',
            'version', v_new_version
        )
    );
exception
    when deadlock_detected then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task command could not acquire locks; retry',
            jsonb_build_object('group_id', v_task_group_id)
        );
end;
$function$;

-- --------------------------------------------------------------------------
-- Reopen: creator/Owner/Admin may move COMPLETED/CANCELLED -> TODO.
-- --------------------------------------------------------------------------

create or replace function public.reopen_group_task(
    p_task_id uuid,
    p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_task public.group_tasks%rowtype;
    v_group public.collaboration_groups%rowtype;
    v_task_group_id uuid;
    v_actor_role text;
    v_new_version bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_task_id is null
       or p_expected_version is null
       or p_expected_version < 0 then
        return private.collaboration_mutation_envelope(
            'VALIDATION',
            'VALIDATION',
            'Task ID and expected version are required'
        );
    end if;

    select gt.group_id
    into v_task_group_id
    from public.group_tasks as gt
    where gt.id = p_task_id;

    if not found then
        return private.collaboration_mutation_envelope('NOT_FOUND', 'NOT_FOUND');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = v_task_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select *
    into v_task
    from public.group_tasks
    where id = p_task_id
      and group_id = v_task_group_id
    for update;

    if not found then
        return private.collaboration_mutation_envelope(
            'NOT_FOUND',
            'NOT_FOUND',
            null,
            jsonb_build_object('group_id', v_task_group_id)
        );
    end if;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = v_task.group_id
      and gm.user_id = v_actor
    for update;

    if v_actor_role is null then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_actor <> v_task.created_by
       and v_actor_role not in ('OWNER', 'ADMIN') then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    if v_task.version <> p_expected_version then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task version is stale',
            jsonb_build_object(
                'task_id', p_task_id,
                'group_id', v_task.group_id,
                'current_version', v_task.version
            )
        );
    end if;

    if v_task.status not in ('COMPLETED', 'CANCELLED') then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Only completed or cancelled tasks can be reopened',
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    v_new_version := v_task.version + 1;

    update public.group_tasks
    set status = 'TODO',
        version = version + 1,
        updated_at = now()
    where id = p_task_id
      and version = p_expected_version;

    if not found then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            null,
            jsonb_build_object('group_id', v_task.group_id)
        );
    end if;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'task_id', p_task_id,
            'group_id', v_task.group_id,
            'status', 'TODO',
            'version', v_new_version
        )
    );
exception
    when deadlock_detected then
        return private.collaboration_mutation_envelope(
            'CONFLICT',
            'CONFLICT',
            'The task command could not acquire locks; retry',
            jsonb_build_object('group_id', v_task_group_id)
        );
end;
$function$;

-- Authenticated clients can execute commands, but cannot write either task
-- table directly.  These signatures match the named JSON parameters used by
-- PostgREST; optional descriptions are last because PostgreSQL requires every
-- input after a defaulted parameter to have a default too.
revoke all on function public.create_group_task(uuid, uuid, text, uuid, timestamptz, bigint[], text) from public;
grant execute on function public.create_group_task(uuid, uuid, text, uuid, timestamptz, bigint[], text) to authenticated;

revoke all on function public.edit_group_task(uuid, text, uuid, timestamptz, bigint[], bigint, text) from public;
grant execute on function public.edit_group_task(uuid, text, uuid, timestamptz, bigint[], bigint, text) to authenticated;

revoke all on function public.reassign_group_task(uuid, uuid, bigint) from public;
grant execute on function public.reassign_group_task(uuid, uuid, bigint) to authenticated;

revoke all on function public.start_group_task(uuid, bigint) from public;
grant execute on function public.start_group_task(uuid, bigint) to authenticated;

revoke all on function public.complete_group_task(uuid, bigint) from public;
grant execute on function public.complete_group_task(uuid, bigint) to authenticated;

revoke all on function public.cancel_group_task(uuid, bigint) from public;
grant execute on function public.cancel_group_task(uuid, bigint) to authenticated;

revoke all on function public.reopen_group_task(uuid, bigint) from public;
grant execute on function public.reopen_group_task(uuid, bigint) to authenticated;

commit;
