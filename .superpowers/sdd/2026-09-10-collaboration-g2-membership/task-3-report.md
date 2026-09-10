# Task 3 report - Room cache v3

## Status

Complete. Implementation commit: `d4ab803` (`feat: upgrade collaboration Room cache to v3`).

## Files changed

- `app/src/main/java/com/smartreminder/data/local/room/CueDatabase.kt`
  - Raises Room from version 2 to 3 and registers the explicit `MIGRATION_2_3` alongside `MIGRATION_1_2`.
  - No destructive fallback was added.
- `app/src/main/java/com/smartreminder/data/local/room/migration/Migration2To3.kt`
  - Adds nullable `display_name` and `avatar_url` columns to `cached_group_members` with additive `ALTER TABLE` statements.
- `app/src/main/java/com/smartreminder/data/local/room/entity/collaboration/CachedGroupMemberEntity.kt`
  - Maps the cached member profile projection (`displayName`, `avatarUrl`) while keeping existing v2 member rows valid.
- `app/src/main/java/com/smartreminder/data/local/room/dao/CollaborationCacheDao.kt`
  - Adds group-scoped member upsert/delete and invite observation/upsert/replace/delete operations.
  - Scoped wrappers reject rows belonging to another group; replacement remains transactional.
  - Member ordering remains `joined_at ASC, user_id ASC`; invite ordering remains `created_at DESC, id ASC` with stable ID tie-breakers.
- `app/schemas/com.smartreminder.data.local.room.CueDatabase/3.json`
  - Regenerated Room schema v3. The pending-command entity/table/indexes are unchanged from schema v2.
- `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationCacheDaoTest.kt`
  - Covers profile fields, deterministic member ordering, group-scoped member replacement/deletion, and group-scoped invite replacement/deletion.
- `app/src/androidTest/java/com/smartreminder/data/local/room/CollaborationRoomMigrationTest.kt`
  - Covers v2 to v3 retention for schedules, routines, weekly days, routine items, every collaboration v2 table, member profile defaults, and the exact pending-command column list.

The pre-existing `app/src/main/AndroidManifest.xml` change, the unrelated `.idea/misc.xml` JDK-name edit present after verification, and `.superpowers/groups-preview-*` artifacts were not staged or modified.

## Schema and migration decisions

- Profile fields are nullable text columns on `cached_group_members`, matching the nullable remote profile projection and allowing every existing v2 row to migrate without a fabricated value.
- Migration 2 to 3 is strictly additive: it only adds the two columns and does not rebuild, drop, or clear any table.
- Pending commands remain independent of cache rows and retain the v2 columns/indexes unchanged; cache replacement/deletion cannot remove pending work.
- Group-scoped DAO wrappers validate that every supplied member/invite belongs to the requested group before upserting. Group and invite delete queries include their scope keys explicitly.

## TDD evidence

### RED

After adding the migration/DAO instrumentation tests first, this command failed for the intended feature-absent reasons:

```text
.\gradlew.bat :app:compileDebugAndroidTestKotlin --rerun-tasks
```

The compiler reported missing `displayName`/`avatarUrl`, `MIGRATION_2_3`, `deleteMember`, `replaceInvites`, scoped invite observation, and `deleteInvite` APIs. No production implementation was present at that point.

### GREEN

The same focused compile passed after the minimal implementation:

```text
.\gradlew.bat :app:compileDebugAndroidTestKotlin --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

## Verification

- `adb devices` found `SM-G990E` (`R5CW82C3ZZT`) online.
- Focused DAO instrumentation:

  ```text
  .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationCacheDaoTest'
  ```

  Result: 10/10 tests passed on SM-G990E; `BUILD SUCCESSFUL`.
- Focused migration instrumentation:

  ```text
  .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.smartreminder.data.local.room.CollaborationRoomMigrationTest'
  ```

  Result: 2/2 tests passed on SM-G990E; `BUILD SUCCESSFUL`.
- Full instrumentation:

  ```text
  .\gradlew.bat connectedDebugAndroidTest
  ```

  Result: 21/21 tests passed on SM-G990E; `BUILD SUCCESSFUL`.
- Focused JVM compile and pending-command regression:

  ```text
  .\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.smartreminder.data.local.room.model.collaboration.PendingGroupCommandCodecTest"
  ```

  Result: `BUILD SUCCESSFUL`.
- Full JVM suite:

  ```text
  .\gradlew.bat :app:testDebugUnitTest
  ```

  Result: 210 tests, 0 failures, 0 errors; `BUILD SUCCESSFUL`.
- `git diff --check` and `git diff --cached --check`: passed.

Instrumentation emits the existing non-fatal `appops set androidx.test.services ... No UID` warning; the test runner still completed all requested tests successfully.

## Self-review and concerns

- Room schema v2 remains unchanged; schema v3 is exported and validates through `MigrationTestHelper`.
- Migration tests exercise representative rows from schedules, routines, all seven v2 collaboration/pending tables, and verify pending-command columns exactly.
- No SQL/domain/UI/Supabase configuration/Manifest files were changed for this task.
- No live-data or external-service acceptance was required; all requested device tests ran on the available SM-G990E.
- Existing unrelated working-tree changes (`.idea/misc.xml`, `app/src/main/AndroidManifest.xml`, and `.superpowers/groups-preview-*`) remain unstaged after the implementation commit.

## Commit

`d4ab803 feat: upgrade collaboration Room cache to v3`
