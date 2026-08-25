-- ============================================================================
-- Cue (SmartReminder) — User Preferences Cloud Sync V1 Schema & RLS Policies
-- Table: public.user_preferences
-- ============================================================================

create table if not exists public.user_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,

    onboarding_completed boolean not null default false,

    wake_up_minute integer not null
        check (wake_up_minute between 0 and 1439),

    sleep_minute integer not null
        check (sleep_minute between 0 and 1439),

    selected_goals text[] not null default '{}',

    updated_at timestamptz not null default now()
);

-- Enable Row Level Security (RLS)
alter table public.user_preferences enable row level security;

-- 1. SELECT Policy: Authenticated users can only read their own preferences
drop policy if exists "user_preferences_select_own" on public.user_preferences;
create policy "user_preferences_select_own"
on public.user_preferences
for select
using (auth.uid() = user_id);

-- 2. INSERT Policy: Authenticated users can only insert their own row
drop policy if exists "user_preferences_insert_own" on public.user_preferences;
create policy "user_preferences_insert_own"
on public.user_preferences
for insert
with check (auth.uid() = user_id);

-- 3. UPDATE Policy: Authenticated users can only update their own row
drop policy if exists "user_preferences_update_own" on public.user_preferences;
create policy "user_preferences_update_own"
on public.user_preferences
for update
using (auth.uid() = user_id)
with check (auth.uid() = user_id);
