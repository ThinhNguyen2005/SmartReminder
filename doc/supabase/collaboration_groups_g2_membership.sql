-- ============================================================================
-- Cue (SmartReminder) - Collaboration Groups G2 Membership Commands
--
-- Additive migration on top of collaboration_groups_v1.sql.
--
-- All collaboration writes are server-authoritative RPCs. Authenticated
-- clients receive read-only table grants and may update only their own
-- profile row. Every command derives its actor from auth.uid(), uses a
-- transaction supplied by PostgreSQL, and returns a JSON mutation envelope:
-- { status, error: { code, detail } | null, data }.
-- ============================================================================

begin;

-- --------------------------------------------------------------------------
-- Minimum user profile projection
-- --------------------------------------------------------------------------

create table if not exists public.user_profiles (
    user_id uuid primary key
        references auth.users(id) on delete cascade,
    display_name text,
    avatar_url text,
    updated_at timestamptz not null default now()
);

create or replace function public.handle_new_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_display_name text;
    v_avatar_url text;
begin
    v_display_name := coalesce(
        nullif(pg_catalog.btrim(new.raw_user_meta_data ->> 'full_name'), ''),
        nullif(pg_catalog.btrim(new.raw_user_meta_data ->> 'name'), ''),
        nullif(pg_catalog.btrim(pg_catalog.split_part(new.email, '@', 1)), ''),
        'Cue user'
    );

    v_avatar_url := nullif(
        pg_catalog.btrim(
            coalesce(
                new.raw_user_meta_data ->> 'avatar_url',
                new.raw_user_meta_data ->> 'picture'
            )
        ),
        ''
    );

    insert into public.user_profiles (user_id, display_name, avatar_url)
    values (new.id, v_display_name, v_avatar_url)
    on conflict (user_id) do nothing;

    return new;
end;
$function$;

create or replace function public.touch_user_profile_updated_at()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
    new.updated_at := now();
    return new;
end;
$function$;

drop trigger if exists collaboration_user_profile_created on auth.users;
create trigger collaboration_user_profile_created
after insert on auth.users
for each row
execute function public.handle_new_user_profile();

drop trigger if exists user_profiles_updated_at on public.user_profiles;
create trigger user_profiles_updated_at
before update on public.user_profiles
for each row
execute function public.touch_user_profile_updated_at();

insert into public.user_profiles (user_id, display_name, avatar_url)
select
    u.id,
    coalesce(
        nullif(pg_catalog.btrim(u.raw_user_meta_data ->> 'full_name'), ''),
        nullif(pg_catalog.btrim(u.raw_user_meta_data ->> 'name'), ''),
        nullif(pg_catalog.btrim(pg_catalog.split_part(u.email, '@', 1)), ''),
        'Cue user'
    ),
    nullif(
        pg_catalog.btrim(
            coalesce(
                u.raw_user_meta_data ->> 'avatar_url',
                u.raw_user_meta_data ->> 'picture'
            )
        ),
        ''
    )
from auth.users as u
on conflict (user_id) do update
set display_name = coalesce(public.user_profiles.display_name, excluded.display_name),
    avatar_url = coalesce(public.user_profiles.avatar_url, excluded.avatar_url);

-- --------------------------------------------------------------------------
-- Private predicates and typed mutation envelopes
-- --------------------------------------------------------------------------

create schema if not exists private;

create or replace function private.is_collaboration_group_member(
    p_group_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select exists (
        select 1
        from public.group_members as gm
        join public.collaboration_groups as cg
          on cg.id = gm.group_id
         and cg.deleted_at is null
        where gm.group_id = p_group_id
          and gm.user_id = (select auth.uid())
    );
$function$;

create or replace function private.is_collaboration_group_manager(
    p_group_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select exists (
        select 1
        from public.group_members as gm
        join public.collaboration_groups as cg
          on cg.id = gm.group_id
         and cg.deleted_at is null
        where gm.group_id = p_group_id
          and gm.user_id = (select auth.uid())
          and gm.role in ('OWNER', 'ADMIN')
    );
$function$;

create or replace function private.is_active_collaboration_group(
    p_group_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select exists (
        select 1
        from public.collaboration_groups as cg
        where cg.id = p_group_id
          and cg.deleted_at is null
    );
$function$;

create or replace function private.is_collaboration_task_member(
    p_task_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select exists (
        select 1
        from public.group_tasks as gt
        join public.collaboration_groups as cg
          on cg.id = gt.group_id
         and cg.deleted_at is null
        join public.group_members as gm
          on gm.group_id = gt.group_id
        where gt.id = p_task_id
          and gm.user_id = (select auth.uid())
    );
$function$;

create or replace function private.is_same_active_group_profile(
    p_target_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $function$
    select exists (
        select 1
        from public.group_members as viewer_member
        join public.group_members as target_member
          on target_member.group_id = viewer_member.group_id
         and target_member.user_id = p_target_user_id
        join public.collaboration_groups as cg
          on cg.id = viewer_member.group_id
         and cg.deleted_at is null
        where viewer_member.user_id = (select auth.uid())
    );
$function$;

create or replace function private.collaboration_mutation_envelope(
    p_status text,
    p_error_code text default null,
    p_detail text default null,
    p_data jsonb default '{}'::jsonb
)
returns jsonb
language sql
immutable
security definer
set search_path = ''
as $function$
    select jsonb_build_object(
        'status', p_status,
        'error', case
            when p_error_code is null then null::jsonb
            else jsonb_build_object(
                'code', p_error_code,
                'detail', p_detail
            )
        end,
        'data', coalesce(p_data, '{}'::jsonb)
    );
$function$;

revoke all on schema private from public;
grant usage on schema private to authenticated;

revoke all on function private.is_collaboration_group_member(uuid) from public;
grant execute on function private.is_collaboration_group_member(uuid) to authenticated;

revoke all on function private.is_collaboration_group_manager(uuid) from public;
grant execute on function private.is_collaboration_group_manager(uuid) to authenticated;

revoke all on function private.is_active_collaboration_group(uuid) from public;
grant execute on function private.is_active_collaboration_group(uuid) to authenticated;

revoke all on function private.is_collaboration_task_member(uuid) from public;
grant execute on function private.is_collaboration_task_member(uuid) to authenticated;

revoke all on function private.is_same_active_group_profile(uuid) from public;
grant execute on function private.is_same_active_group_profile(uuid) to authenticated;

revoke all on function private.collaboration_mutation_envelope(text, text, text, jsonb) from public;

-- --------------------------------------------------------------------------
-- Read policies. Every group-backed path checks deleted_at through the
-- active helpers and the explicit invite predicate below.
-- --------------------------------------------------------------------------

alter table public.user_profiles enable row level security;
alter table public.collaboration_groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_invites enable row level security;
alter table public.group_tasks enable row level security;
alter table public.group_task_reminders enable row level security;
alter table public.group_reminders enable row level security;

create unique index if not exists group_members_one_owner_per_group_idx
    on public.group_members (group_id)
    where role = 'OWNER';

drop policy if exists user_profiles_select_self_or_active_group on public.user_profiles;
create policy user_profiles_select_self_or_active_group
on public.user_profiles
for select
to authenticated
using (
    user_id = (select auth.uid())
    or private.is_same_active_group_profile(user_id)
);

drop policy if exists user_profiles_update_self on public.user_profiles;
create policy user_profiles_update_self
on public.user_profiles
for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists collaboration_groups_select_member on public.collaboration_groups;
create policy collaboration_groups_select_member
on public.collaboration_groups
for select
to authenticated
using (
    deleted_at is null
    and private.is_collaboration_group_member(id)
);

drop policy if exists group_members_select_member on public.group_members;
create policy group_members_select_member
on public.group_members
for select
to authenticated
using (
    private.is_active_collaboration_group(group_id)
    and private.is_collaboration_group_member(group_id)
);

drop policy if exists group_invites_select_recipient_or_manager on public.group_invites;
create policy group_invites_select_recipient_or_manager
on public.group_invites
for select
to authenticated
using (
    private.is_active_collaboration_group(group_id)
    and (
        invitee_user_id = (select auth.uid())
        or (
            status = 'PENDING'
            and private.is_collaboration_group_manager(group_id)
        )
    )
);

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

drop policy if exists group_reminders_select_member on public.group_reminders;
create policy group_reminders_select_member
on public.group_reminders
for select
to authenticated
using (
    private.is_active_collaboration_group(group_id)
    and private.is_collaboration_group_member(group_id)
);

-- Authenticated clients can read active collaboration data, but cannot write
-- collaboration tables directly. Commands below are the only write surface.
revoke all on table public.user_profiles from public;
revoke all on table public.user_profiles from anon, authenticated;
grant select, update on table public.user_profiles to authenticated;

revoke all on table public.collaboration_groups from public;
revoke all on table public.collaboration_groups from anon, authenticated;
grant select on table public.collaboration_groups to authenticated;

revoke all on table public.group_members from public;
revoke all on table public.group_members from anon, authenticated;
grant select on table public.group_members to authenticated;

revoke all on table public.group_invites from public;
revoke all on table public.group_invites from anon, authenticated;
grant select on table public.group_invites to authenticated;

revoke all on table public.group_tasks from public;
revoke all on table public.group_tasks from anon, authenticated;
grant select on table public.group_tasks to authenticated;

revoke all on table public.group_task_reminders from public;
revoke all on table public.group_task_reminders from anon, authenticated;
grant select on table public.group_task_reminders to authenticated;

revoke all on table public.group_reminders from public;
revoke all on table public.group_reminders from anon, authenticated;
grant select on table public.group_reminders to authenticated;

-- --------------------------------------------------------------------------
-- Transaction-safe membership commands
-- --------------------------------------------------------------------------

create or replace function public.create_collaboration_group(
    p_name text,
    p_description text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_group_id uuid;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope(
            'NOT_AUTHORIZED',
            'NOT_AUTHORIZED',
            'An authenticated actor is required'
        );
    end if;

    if p_name is null or pg_catalog.btrim(p_name) = '' then
        return private.collaboration_mutation_envelope(
            'FAILURE',
            'VALIDATION',
            'Group name must not be blank'
        );
    end if;

    insert into public.collaboration_groups (name, description, created_by)
    values (pg_catalog.btrim(p_name), p_description, v_actor)
    returning id into v_group_id;

    insert into public.group_members (group_id, user_id, role)
    values (v_group_id, v_actor, 'OWNER');

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object('group_id', v_group_id)
    );
end;
$function$;

create or replace function public.update_collaboration_group(
    p_group_id uuid,
    p_name text,
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
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_name is null or pg_catalog.btrim(p_name) = '' then
        return private.collaboration_mutation_envelope(
            'FAILURE',
            'VALIDATION',
            'Group name must not be blank'
        );
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor
    for update;

    if v_actor_role is null or v_actor_role not in ('OWNER', 'ADMIN') then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    update public.collaboration_groups
    set name = pg_catalog.btrim(p_name),
        description = p_description,
        updated_at = now()
    where id = p_group_id;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object('group_id', p_group_id)
    );
end;
$function$;

create or replace function public.invite_group_member(
    p_group_id uuid,
    p_invitee_email text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_actor_role text;
    v_group public.collaboration_groups%rowtype;
    v_invitee_email text;
    v_invitee_id uuid;
    v_existing_role text;
    v_pending_invite_id uuid;
    v_invite_id uuid;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    v_invitee_email := pg_catalog.lower(pg_catalog.btrim(coalesce(p_invitee_email, '')));
    if v_invitee_email = '' then
        return private.collaboration_mutation_envelope(
            'FAILURE',
            'VALIDATION',
            'Invite email must not be blank'
        );
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor
    for update;

    if v_actor_role is null or v_actor_role not in ('OWNER', 'ADMIN') then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select u.id
    into v_invitee_id
    from auth.users as u
    where pg_catalog.lower(u.email) = v_invitee_email
    limit 1;

    if v_invitee_id is null then
        return private.collaboration_mutation_envelope('MEMBER_NOT_FOUND', 'MEMBER_NOT_FOUND');
    end if;

    select gm.role
    into v_existing_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_invitee_id
    for update;

    if v_existing_role is not null then
        return private.collaboration_mutation_envelope('ALREADY_MEMBER', 'ALREADY_MEMBER');
    end if;

    select gi.id
    into v_pending_invite_id
    from public.group_invites as gi
    where gi.group_id = p_group_id
      and gi.invitee_user_id = v_invitee_id
      and gi.status = 'PENDING'
    for update;

    if v_pending_invite_id is not null then
        return private.collaboration_mutation_envelope(
            'INVITE_ALREADY_PENDING',
            'INVITE_ALREADY_PENDING'
        );
    end if;

    insert into public.group_invites (
        group_id,
        inviter_id,
        invitee_user_id,
        status
    )
    values (
        p_group_id,
        v_actor,
        v_invitee_id,
        'PENDING'
    )
    returning id into v_invite_id;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'group_id', p_group_id,
            'invite_id', v_invite_id,
            'invitee_user_id', v_invitee_id
        )
    );
end;
$function$;

create or replace function public.respond_group_invite(
    p_invite_id uuid,
    p_accept boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_actor uuid := (select auth.uid());
    v_group_id uuid;
    v_group_deleted_at timestamptz;
    v_invite public.group_invites%rowtype;
    v_existing_role text;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_accept is null then
        return private.collaboration_mutation_envelope(
            'FAILURE',
            'VALIDATION',
            'Invite response must be accept or decline'
        );
    end if;

    select group_id
    into v_group_id
    from public.group_invites
    where id = p_invite_id;

    if not found then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select deleted_at
    into v_group_deleted_at
    from public.collaboration_groups
    where id = v_group_id
    for update;

    if not found or v_group_deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select *
    into v_invite
    from public.group_invites
    where id = p_invite_id
    for update;

    if not found or v_invite.invitee_user_id <> v_actor then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if v_invite.status <> 'PENDING' then
        return private.collaboration_mutation_envelope('INVALID_STATE', 'INVALID_STATE');
    end if;

    select gm.role
    into v_existing_role
    from public.group_members as gm
    where gm.group_id = v_invite.group_id
      and gm.user_id = v_actor
    for update;

    if v_existing_role is not null then
        update public.group_invites
        set status = 'ACCEPTED',
            responded_at = now()
        where id = p_invite_id;

        return private.collaboration_mutation_envelope(
            'ALREADY_MEMBER',
            'ALREADY_MEMBER',
            'Invitee is already a member'
        );
    end if;

    if p_accept then
        insert into public.group_members (group_id, user_id, role)
        values (v_invite.group_id, v_actor, 'MEMBER');

        update public.group_invites
        set status = 'ACCEPTED',
            responded_at = now()
        where id = p_invite_id;
    else
        update public.group_invites
        set status = 'DECLINED',
            responded_at = now()
        where id = p_invite_id;
    end if;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'group_id', v_invite.group_id,
            'invite_id', p_invite_id,
            'accepted', p_accept
        )
    );
end;
$function$;

create or replace function public.change_group_member_role(
    p_group_id uuid,
    p_member_id uuid,
    p_role text
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
    v_member_role text;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if p_role is null or p_role not in ('ADMIN', 'MEMBER') then
        return private.collaboration_mutation_envelope('INVALID_STATE', 'INVALID_STATE');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    perform 1
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id in (v_actor, p_member_id)
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor;

    select gm.role
    into v_member_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = p_member_id;

    if v_actor_role is null or v_actor_role <> 'OWNER' then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if v_member_role is null then
        return private.collaboration_mutation_envelope('MEMBER_NOT_FOUND', 'MEMBER_NOT_FOUND');
    end if;

    if p_member_id = v_actor or v_member_role = 'OWNER' then
        return private.collaboration_mutation_envelope('INVALID_STATE', 'INVALID_STATE');
    end if;

    update public.group_members
    set role = p_role
    where group_id = p_group_id
      and user_id = p_member_id;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'group_id', p_group_id,
            'member_id', p_member_id,
            'role', p_role
        )
    );
end;
$function$;

create or replace function public.remove_group_member(
    p_group_id uuid,
    p_member_id uuid
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
    v_member_role text;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    perform 1
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id in (v_actor, p_member_id)
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor;

    select gm.role
    into v_member_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = p_member_id;

    if v_actor_role is null or v_actor_role not in ('OWNER', 'ADMIN') then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if v_member_role is null then
        return private.collaboration_mutation_envelope('MEMBER_NOT_FOUND', 'MEMBER_NOT_FOUND');
    end if;

    if p_member_id = v_actor or v_member_role = 'OWNER' then
        return private.collaboration_mutation_envelope('INVALID_STATE', 'INVALID_STATE');
    end if;

    if v_actor_role = 'ADMIN' and v_member_role <> 'MEMBER' then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    delete from public.group_members
    where group_id = p_group_id
      and user_id = p_member_id;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'group_id', p_group_id,
            'member_id', p_member_id
        )
    );
end;
$function$;

create or replace function public.transfer_group_ownership(
    p_group_id uuid,
    p_new_owner_id uuid
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
    v_new_owner_role text;
    v_owner_count bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    perform 1
    from public.group_members as gm
    where gm.group_id = p_group_id
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor;

    select gm.role
    into v_new_owner_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = p_new_owner_id;

    if v_actor_role is null or v_actor_role <> 'OWNER' then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    if v_new_owner_role is null then
        return private.collaboration_mutation_envelope('MEMBER_NOT_FOUND', 'MEMBER_NOT_FOUND');
    end if;

    if p_new_owner_id = v_actor or v_new_owner_role = 'OWNER' then
        return private.collaboration_mutation_envelope('INVALID_STATE', 'INVALID_STATE');
    end if;

    select count(*)
    into v_owner_count
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.role = 'OWNER';

    if v_owner_count <> 1 then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Active group must have exactly one owner'
        );
    end if;

    update public.group_members
    set role = 'MEMBER'
    where group_id = p_group_id
      and user_id = v_actor
      and role = 'OWNER';

    update public.group_members
    set role = 'OWNER'
    where group_id = p_group_id
      and user_id = p_new_owner_id
      and role in ('ADMIN', 'MEMBER');

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object(
            'group_id', p_group_id,
            'previous_owner_id', v_actor,
            'owner_id', p_new_owner_id
        )
    );
end;
$function$;

create or replace function public.leave_collaboration_group(
    p_group_id uuid
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
    v_owner_count bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    perform 1
    from public.group_members as gm
    where gm.group_id = p_group_id
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor;

    if v_actor_role is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select count(*)
    into v_owner_count
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.role = 'OWNER';

    if v_owner_count <> 1 then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Active group must have exactly one owner'
        );
    end if;

    if v_actor_role = 'OWNER' then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Owner must transfer ownership or delete group before leaving'
        );
    end if;

    delete from public.group_members
    where group_id = p_group_id
      and user_id = v_actor;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object('group_id', p_group_id, 'deleted', false)
    );
end;
$function$;

create or replace function public.delete_collaboration_group(
    p_group_id uuid
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
    v_owner_count bigint;
begin
    if v_actor is null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select *
    into v_group
    from public.collaboration_groups
    where id = p_group_id
    for update;

    if not found or v_group.deleted_at is not null then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    perform 1
    from public.group_members as gm
    where gm.group_id = p_group_id
    order by gm.user_id
    for update;

    select gm.role
    into v_actor_role
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.user_id = v_actor;

    if v_actor_role is null or v_actor_role <> 'OWNER' then
        return private.collaboration_mutation_envelope('NOT_AUTHORIZED', 'NOT_AUTHORIZED');
    end if;

    select count(*)
    into v_owner_count
    from public.group_members as gm
    where gm.group_id = p_group_id
      and gm.role = 'OWNER';

    if v_owner_count <> 1 then
        return private.collaboration_mutation_envelope(
            'INVALID_STATE',
            'INVALID_STATE',
            'Active group must have exactly one owner'
        );
    end if;

    update public.collaboration_groups
    set deleted_at = now(),
        updated_at = now()
    where id = p_group_id
      and deleted_at is null;

    return private.collaboration_mutation_envelope(
        'APPLIED',
        null,
        null,
        jsonb_build_object('group_id', p_group_id, 'deleted', true)
    );
end;
$function$;

-- RPCs are callable by authenticated clients only; table writes remain
-- unavailable to authenticated and anonymous roles.
revoke all on function public.create_collaboration_group(text, text) from public;
grant execute on function public.create_collaboration_group(text, text) to authenticated;

revoke all on function public.update_collaboration_group(uuid, text, text) from public;
grant execute on function public.update_collaboration_group(uuid, text, text) to authenticated;

revoke all on function public.invite_group_member(uuid, text) from public;
grant execute on function public.invite_group_member(uuid, text) to authenticated;

revoke all on function public.respond_group_invite(uuid, boolean) from public;
grant execute on function public.respond_group_invite(uuid, boolean) to authenticated;

revoke all on function public.change_group_member_role(uuid, uuid, text) from public;
grant execute on function public.change_group_member_role(uuid, uuid, text) to authenticated;

revoke all on function public.remove_group_member(uuid, uuid) from public;
grant execute on function public.remove_group_member(uuid, uuid) to authenticated;

revoke all on function public.transfer_group_ownership(uuid, uuid) from public;
grant execute on function public.transfer_group_ownership(uuid, uuid) to authenticated;

revoke all on function public.leave_collaboration_group(uuid) from public;
grant execute on function public.leave_collaboration_group(uuid) to authenticated;

revoke all on function public.delete_collaboration_group(uuid) from public;
grant execute on function public.delete_collaboration_group(uuid) to authenticated;

revoke all on function public.handle_new_user_profile() from public;
revoke all on function public.touch_user_profile_updated_at() from public;

commit;
