-- ============================================================================
-- G3 lock-order concurrency probe (OPT-IN, disposable development only)
-- DO NOT RUN AGAINST PRODUCTION.
--
-- Prepare a fresh development group with user_a as owner/creator, user_b as a
-- current member and assignee, and a TODO task at version 0 using this exact
-- payload:
--
--   task_id: 00000000-0000-4000-8000-0000000003a1
--   title: Lock-order concurrency task
--   due_at: 2030-01-01T00:00:00Z
--   offsets: 300 and 60 seconds
--   description: lock-order probe
--
-- Run this same file in two psql sessions, starting Session A first and
-- Session B within one second.  Session A retries create while holding the
-- group and task locks for five seconds; Session B concurrently starts the
-- task and must wait on the same group-first lock order.  Both commands should
-- return APPLIED.  A typed CONFLICT is the expected retry envelope if the
-- server reports a lock acquisition failure; an unhandled deadlock is a test
-- failure.  The probe intentionally commits the start in the disposable DB.
--
-- Session A:
--   psql ... -v g3_dev_target=development -v g3_session=a \
--     -v user_a=<uuid> -v user_b=<uuid> -v group_id=<uuid> -v task_id=<uuid>
-- Session B:
--   psql ... -v g3_dev_target=development -v g3_session=b \
--     -v user_a=<uuid> -v user_b=<uuid> -v group_id=<uuid> -v task_id=<uuid>
--
-- No psql variable is embedded in a dollar-quoted body in this file.
-- ============================================================================

\set ON_ERROR_STOP on

\if :{?g3_dev_target}
\else
\echo 'G3 concurrency probe refused: pass -v g3_dev_target=development explicitly'
\quit 1
\endif

select :'g3_dev_target' = 'development' as g3_target_is_development
\gset concurrency_
\if :concurrency_g3_target_is_development
\else
\echo 'G3 concurrency probe refused: target must be exactly development'
\quit 1
\endif

\if :{?g3_session}
\else
\echo 'G3 concurrency probe refused: pass -v g3_session=a or -v g3_session=b'
\quit 1
\endif
\if :{?user_a}
\else
\echo 'G3 concurrency probe refused: pass -v user_a=<authenticated UUID>'
\quit 1
\endif
\if :{?user_b}
\else
\echo 'G3 concurrency probe refused: pass -v user_b=<authenticated UUID>'
\quit 1
\endif
\if :{?group_id}
\else
\echo 'G3 concurrency probe refused: pass -v group_id=<group UUID>'
\quit 1
\endif
\if :{?task_id}
\else
\echo 'G3 concurrency probe refused: pass -v task_id=<task UUID>'
\quit 1
\endif

select lower(:'g3_session') = 'a' as is_session_a
\gset concurrency_

select lower(:'g3_session') in ('a', 'b') as is_valid_session
\gset concurrency_
\if :concurrency_is_valid_session
\else
\echo 'G3 concurrency probe refused: session must be exactly a or b'
\quit 1
\endif

\if :concurrency_is_session_a
    -- Session A: exact create retry holds group -> task locks while B waits.
    begin;
    set local role authenticated;
    select set_config('request.jwt.claim.sub', :'user_a', false);

    with retried as (
        select public.create_group_task(
            :'task_id'::uuid,
            :'group_id'::uuid,
            'Lock-order concurrency task',
            :'user_b'::uuid,
            '2030-01-01T00:00:00Z'::timestamptz,
            array[300, 60]::bigint[],
            'lock-order probe'
        ) as envelope
    )
    select envelope ->> 'status' as session_a_retry_status
    from retried
\gset concurrency_

    select :'concurrency_session_a_retry_status' = 'APPLIED' as session_a_retry_ok
\gset concurrency_
\if :concurrency_session_a_retry_ok
\else
\echo 'Session A create retry did not return APPLIED'
    rollback;
\quit 1
\endif

    -- Keep both row locks held long enough for Session B to overlap.
    select pg_sleep(5);
    commit;
    reset role;
\echo 'Session A passed: create retry returned APPLIED'
\else
    -- Session B: existing-task mutation contends with Session A's retry.
    begin;
    set local role authenticated;
    select set_config('request.jwt.claim.sub', :'user_b', false);

    with started as (
        select public.start_group_task(
            :'task_id'::uuid,
            0
        ) as envelope
    )
    select envelope ->> 'status' as session_b_start_status
    from started
\gset concurrency_

    select :'concurrency_session_b_start_status' = 'APPLIED' as session_b_start_ok
\gset concurrency_
\if :concurrency_session_b_start_ok
\else
\echo 'Session B start did not return APPLIED; unhandled deadlock or stale fixture'
    rollback;
\quit 1
\endif

    commit;
    reset role;
\echo 'Session B passed: concurrent start returned APPLIED'
\endif
