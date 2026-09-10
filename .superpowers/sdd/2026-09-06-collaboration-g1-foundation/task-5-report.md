# Task 5 report: Room collaboration foundation

## Status

DONE_WITH_CONCERNS

Implementation commit: `a597a96` (`feat: add collaboration room foundation`)

The required Room foundation, migration, cache/pending DAOs, exported schema v2, and instrumentation tests are implemented. A connected SM-G990E device was available and the focused plus full instrumentation suites passed. The project-wide JVM unit-test task could not start its test executors because the Windows host ran out of paging-file/native memory; this is recorded under concerns.

## Files changed in implementation commit

- `app/build.gradle.kts` - package the versioned Room schemas as androidTest assets so MigrationTestHelper can load schema v1.
- `app/src/main/java/com/smartreminder/data/local/room/CueDatabase.kt` - register collaboration entities and DAOs, raise version 1 to 2, and register `MIGRATION_1_2`; no destructive fallback is configured.
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedCollaborationGroupEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupMemberEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupInviteEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupTaskEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupTaskReminderEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupReminderEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/PendingGroupCommandEntity.kt`
- `app/src/main/java/com/smartreminder/data/local/room/dao/CollaborationCacheDao.kt` - deterministic observation ordering and scoped replacement/upsert operations.
- `app/src/main/java/com/smartreminder/data/local/room/dao/PendingGroupCommandDao.kt` - ordered pending/state/aggregate reads with stable ID tie-breakers.
- `app/src/main/java/com/smartreminder/data/local/room/migration/Migration1To2.kt` - creates the seven v2 collaboration/pending tables and cache indexes without a foreign key from pending commands.
- `app/schemas/com.smartreminder.data.local.room.CueDatabase/2.json` - regenerated Room schema v2; tracked schema v1 remains unchanged.
- `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationRoomMigrationTest.kt`
- `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationCacheDaoTest.kt`

The pre-existing `app/src/main/AndroidManifest.xml` user change was not edited, staged, or committed.

## TDD evidence

### RED

1. Added `CollaborationRoomMigrationTest` and `CollaborationCacheDaoTest` before changing the collaboration DAO ordering.
2. First migration command:

   `.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationRoomMigrationTest'`

   The test reached the device but initially failed before migration setup because the v1 schema was not in test assets: `FileNotFoundException: .../CueDatabase/1.json`. The verified Task 5 integration fix was the `androidTest` schema asset source directory in `app/build.gradle.kts`.
3. After the asset wiring, migration ran and passed, as expected because the pre-existing migration implementation was already present.
4. DAO RED command (PowerShell requires quoting the `-P` argument):

   `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationCacheDaoTest'`

   Result: 4 tests ran; 3 failed for the intended reason and 1 passed. Reverse insertion with tied sort keys produced `[group_b, group_a]`, `[task_b, task_a]`, and `[command_b, command_a]` instead of the expected stable-ID order. The cache replacement test passed, showing the regression test itself was valid.

### GREEN

Added only the minimal `id ASC` (or composite-key equivalent) tie-breaker to each collaboration observation/replay query. The DAO rerun completed with 4/4 tests and `BUILD SUCCESSFUL`. The migration rerun completed with 1/1 test and `BUILD SUCCESSFUL`.

## Verification commands and results

- `adb devices` -> SM-G990E (`R5CW82C3ZZT`) was present and online.
- `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationCacheDaoTest'` -> 4/4 passed on SM-G990E; `BUILD SUCCESSFUL`.
- `./gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationRoomMigrationTest'` -> 1/1 passed on SM-G990E; `BUILD SUCCESSFUL`.
- `./gradlew.bat connectedDebugAndroidTest` -> 14/14 instrumentation tests passed on SM-G990E; result XML reported `tests="14" failures="0" errors="0" skipped="0"`; `BUILD SUCCESSFUL`.
- `./gradlew.bat compileDebugSources` -> `BUILD SUCCESSFUL`.
- `./gradlew.bat :app:kspDebugKotlin --rerun-tasks` -> `BUILD SUCCESSFUL`; schema v2 was regenerated from current entities.
- `git diff --cached --check` -> clean before the implementation commit.
- Schema checks: schema v1 reports database version 1 and identity hash `2079121daa7b5d3a950d1cbae7defc8a`; regenerated schema v2 reports database version 2 and identity hash `b12a5a877d15dbd2ffd9446316ffde84`. Migration instrumentation preserved representative schedule-group, routine, weekly-day, and routine-item rows and found all seven new tables.

## Unit-test concern

`./gradlew.bat test` could not complete because Windows could not start the Gradle test executor JVMs. The output repeatedly reported:

`There is insufficient memory for the Java Runtime Environment to continue.`

`Native memory allocation (mmap) failed to map 264241152 bytes.`

`error='The paging file is too small for this operation to complete' (DOS error/errno=1455)`

The bounded retries with `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx1024m` and with `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m` also failed to start the Gradle daemon for the same host-level paging-file condition. The 25 `app/hs_err_pid*.log` files created by those failed runs were inspected for this evidence and removed; no matching crash logs remain.

Instrumentation commands also print the device-environment line `appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow` / `No UID for androidx.test.services in user 0`; the tests nevertheless completed and reported the passing results above.

## Self-review

- Room entities and migration SQL match the regenerated schema v2 fields, nullability, primary keys, indexes, and cache foreign-key lifecycle rules.
- `pending_group_commands` has no foreign key to cached entities, so cache refresh/delete cannot cascade-delete pending work.
- Group, member, task, invite, reminder, task-reminder, and pending-command reads now have deterministic ordering under ties; per-aggregate commands remain oldest-first by `created_at`, then stable `id`.
- Existing schema v1 remains tracked and is not rewritten.
- `CueDatabase` uses an explicit 1 -> 2 migration and contains no `fallbackToDestructiveMigration` call.
- Only the Task 5 paths listed above were committed; the manifest remains an unstaged user modification.
