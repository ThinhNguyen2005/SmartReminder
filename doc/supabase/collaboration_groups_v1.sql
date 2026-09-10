-- ============================================================================
-- Cue (SmartReminder) - Collaboration Groups V1 Schema & Read RLS Foundation
-- Tables: public.collaboration_groups and related collaboration tables
--
-- This migration defines the read-side foundation only. Business mutations and
-- their cross-record invariants belong to server-authoritative commands/RPCs.
-- ============================================================================

begin;

-- Parent aggregate. A group is soft-deleted so scheduled/event infrastructure
-- can retain an auditable row while hard deletion still cascades its children.
create table if not exists public.collaboration_groups (
    id uuid primary key default gen_random_uuid(),
    name text not null
        check (btrim(name) <> ''),
    description text,
    created_by uuid not null
        references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Membership is the source of truth for group ownership. Server commands
-- maintain exactly one owner while a group exists; the partial unique index
-- below prevents two owners even if a command is retried incorrectly.
create table if not exists public.group_members (
    group_id uuid not null
        references public.collaboration_groups(id) on delete cascade,
    user_id uuid not null
        references auth.users(id) on delete restrict,
    role text not null default 'MEMBER'
        check (role in ('OWNER', 'ADMIN', 'MEMBER')),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create unique index if not exists group_members_one_owner_per_group_idx
    on public.group_members (group_id)
    where role = 'OWNER';

create index if not exists group_members_user_id_idx
    on public.group_members (user_id);

-- Invitations remain addressable by both the invitee and the group managers.
-- Restricting user deletion preserves invitation/audit history; deleting a
-- group removes its invitations through the parent FK.
create table if not exists public.group_invites (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null
        references public.collaboration_groups(id) on delete cascade,
    inviter_id uuid not null
        references auth.users(id) on delete restrict,
    invitee_user_id uuid not null
        references auth.users(id) on delete restrict,
    status text not null default 'PENDING'
        check (status in ('PENDING', 'ACCEPTED', 'DECLINED')),
    created_at timestamptz not null default now(),
    responded_at timestamptz
);

create unique index if not exists group_invites_one_pending_per_invitee_idx
    on public.group_invites (group_id, invitee_user_id)
    where status = 'PENDING';

create index if not exists group_invites_invitee_status_idx
    on public.group_invites (invitee_user_id, status);

create index if not exists group_invites_group_status_idx
    on public.group_invites (group_id, status);

-- Tasks are versioned for optimistic concurrency. Their workflow transitions
-- are intentionally not exposed through direct table mutation policies.
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

create index if not exists group_tasks_group_id_idx
    on public.group_tasks (group_id);

create index if not exists group_tasks_assignee_due_at_idx
    on public.group_tasks (assignee_id, due_at);

-- Relative offsets are unique by the composite primary key and must be
-- strictly positive so a reminder can only precede the task deadline.
create table if not exists public.group_task_reminders (
    task_id uuid not null
        references public.group_tasks(id) on delete cascade,
    offset_seconds bigint not null
        check (offset_seconds > 0),
    primary key (task_id, offset_seconds)
);

-- Group reminders are independent of tasks. The audience check keeps the
-- MEMBER/EVERYONE representation unambiguous for recipient resolution.
create table if not exists public.group_reminders (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null
        references public.collaboration_groups(id) on delete cascade,
    title text not null
        check (btrim(title) <> ''),
    description text,
    created_by uuid not null
        references auth.users(id) on delete restrict,
    audience_type text not null
        check (audience_type in ('MEMBER', 'EVERYONE')),
    audience_user_id uuid
        references auth.users(id) on delete restrict,
    remind_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (
        (audience_type = 'MEMBER' and audience_user_id is not null)
        or (audience_type = 'EVERYONE' and audience_user_id is null)
    )
);

create index if not exists group_reminders_group_remind_at_idx
    on public.group_reminders (group_id, remind_at);

-- Keep RLS helper predicates outside the exposed public API schema. These are
-- stable SECURITY DEFINER reads solely to avoid recursive group_members RLS
-- evaluation; no privileged mutation command is defined here.
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
        where gm.group_id = p_group_id
          and gm.user_id = (select auth.uid())
          and gm.role in ('OWNER', 'ADMIN')
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
        join public.group_members as gm
          on gm.group_id = gt.group_id
        where gt.id = p_task_id
          and gm.user_id = (select auth.uid())
    );
$function$;

-- The private schema is not an RPC surface. Authenticated sessions need only
-- enough privilege for policy evaluation; anonymous sessions receive none.
revoke all on schema private from public;
grant usage on schema private to authenticated;

revoke all on function private.is_collaboration_group_member(uuid) from public;
grant execute on function private.is_collaboration_group_member(uuid) to authenticated;

revoke all on function private.is_collaboration_group_manager(uuid) from public;
grant execute on function private.is_collaboration_group_manager(uuid) to authenticated;

revoke all on function private.is_collaboration_task_member(uuid) from public;
grant execute on function private.is_collaboration_task_member(uuid) to authenticated;

-- RLS is enabled on every collaboration table. No INSERT, UPDATE, or DELETE
-- policies are created: G2/G3 server commands own all collaboration writes.
alter table public.collaboration_groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_invites enable row level security;
alter table public.group_tasks enable row level security;
alter table public.group_task_reminders enable row level security;
alter table public.group_reminders enable row level security;

drop policy if exists collaboration_groups_select_member on public.collaboration_groups;
create policy collaboration_groups_select_member
on public.collaboration_groups
for select
to authenticated
using (private.is_collaboration_group_member(id));

drop policy if exists group_members_select_member on public.group_members;
create policy group_members_select_member
on public.group_members
for select
to authenticated
using (private.is_collaboration_group_member(group_id));

drop policy if exists group_invites_select_recipient_or_manager on public.group_invites;
create policy group_invites_select_recipient_or_manager
on public.group_invites
for select
to authenticated
using (
    invitee_user_id = (select auth.uid())
    or (
        status = 'PENDING'
        and private.is_collaboration_group_manager(group_id)
    )
);

drop policy if exists group_tasks_select_member on public.group_tasks;
create policy group_tasks_select_member
on public.group_tasks
for select
to authenticated
using (private.is_collaboration_group_member(group_id));

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
using (private.is_collaboration_group_member(group_id));

commit;
