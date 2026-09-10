# Task 4 report - Supabase data source and default repository

## Status

Complete. Implementation commit: `e184dad` (`feat: add G2 collaboration data source`).

## Files and APIs

- `app/src/main/java/com/smartreminder/data/remote/collaboration/CollaborationRemoteDto.kt`
  - Adds serializable PostgREST DTOs for groups, members, profiles, invites, and the exact `{ status, error, data }` RPC envelope.
- `app/src/main/java/com/smartreminder/data/remote/collaboration/CollaborationRemoteMapper.kt`
  - Maps remote rows to typed domain models and Room v3 cache entities.
  - Normalizes role/status/error strings into domain enums; unknown values become typed failures or explicit mapping errors, never raw database values.
- `app/src/main/java/com/smartreminder/data/remote/collaboration/CollaborationRemoteDataSource.kt`
  - Defines the SDK-free repository-facing remote boundary for reads and all G2 membership commands.
- `app/src/main/java/com/smartreminder/data/remote/collaboration/SupabaseCollaborationRemoteDataSource.kt`
  - Implements PostgREST reads and the exact Task 2 RPC names/parameter keys.
  - Reads member profiles through the RLS-scoped `user_profiles` projection.
- `app/src/main/java/com/smartreminder/data/repository/collaboration/CollaborationCacheDataSource.kt`
  - Defines the testable cache boundary and atomic replacement operations.
- `app/src/main/java/com/smartreminder/data/local/room/repository/RoomCollaborationCacheDataSource.kt`
  - Adapts Room v3 DAO/transactions to the cache boundary.
- `app/src/main/java/com/smartreminder/data/repository/collaboration/DefaultCollaborationRepository.kt`
  - Provides cache-first `Flow`s, failure-safe refreshes, online-only mutations, typed envelope mapping, and affected group/list/invite refreshes after applied RPCs.
  - Requires an injected `network: () -> Boolean`; it has no fake always-online default.
- `app/src/main/java/com/smartreminder/data/local/room/dao/CollaborationCacheDao.kt`
  - Adds only non-schema DAO operations needed for atomic full group/invite cache replacement (`upsertGroups`, `deleteAllGroups`, `deleteAllInvites`).
- `app/src/main/java/com/smartreminder/data/remote/SupabaseConfiguration.kt`
  - Adds `SupabaseConfiguration.requireComplete()` and a clear `MissingSupabaseConfigurationException`.
- `app/src/main/java/com/smartreminder/data/remote/SupabaseClient.kt`
  - Removes the hard-coded Supabase URL/anon key and reads generated BuildConfig fields; Google OAuth client configuration remains separate.
- `app/build.gradle.kts`
  - Reads `supabase.url`/`supabase.anonKey`, `SUPABASE_URL`/`SUPABASE_ANON_KEY`, or ignored `local.properties` values into BuildConfig without committing credentials.
- Tests:
  - `app/src/test/java/com/smartreminder/data/remote/collaboration/CollaborationRemoteMapperTest.kt`
  - `app/src/test/java/com/smartreminder/data/repository/collaboration/DefaultCollaborationRepositoryTest.kt`
  - `app/src/test/java/com/smartreminder/data/remote/SupabaseConfigurationTest.kt`
  - The pre-existing auth URL fixture now uses a neutral G2-shaped example URL rather than the removed project identifier.

## TDD evidence

### RED

After adding the focused tests before the production APIs, the focused command failed during Kotlin test compilation with the intended missing-feature errors, including unresolved `SupabaseConfiguration`, `CollaborationRemoteMapper`, `CollaborationRemoteDataSource`, `CollaborationCacheDataSource`, and `DefaultCollaborationRepository` references.

```text
.\gradlew.bat :app:testDebugUnitTest --tests "com.smartreminder.data.remote.collaboration.CollaborationRemoteMapperTest" --tests "com.smartreminder.data.repository.collaboration.DefaultCollaborationRepositoryTest" --tests "com.smartreminder.data.remote.SupabaseConfigurationTest" --rerun-tasks
```

### GREEN

The same focused command passed after the minimal implementation. It covers cache-first observation, successful atomic refresh, refresh failure/cache retention, offline `NetworkRequired`, applied mutation refreshes, remote profile/status mapping, typed envelope mapping, and missing-config failure.

## Verification

- Focused repository/mapper/config JVM tests: `BUILD SUCCESSFUL`.
- Full JVM suite:

  ```text
  .\gradlew.bat :app:testDebugUnitTest
  ```

  Result: 220 tests, 0 failures, 0 errors; `BUILD SUCCESSFUL`.
- Production Kotlin compile:

  ```text
  .\gradlew.bat :app:compileDebugKotlin
  ```

  Result: `BUILD SUCCESSFUL`.
- Android-test Kotlin compile (including Room DAO generation):

  ```text
  .\gradlew.bat :app:compileDebugAndroidTestKotlin
  ```

  Result: `BUILD SUCCESSFUL`.
- Final pre-commit combined verification:

  ```text
  .\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
  ```

  Result: `BUILD SUCCESSFUL`.
- Staged `git diff --check`: passed.
- No live Supabase integration smoke was run; the dedicated G2 dev target/credentials are intentionally not configured.

## Self-review and concerns

- No UI, ViewModel, Manifest, `.idea`, or preview artifact changes were staged. Existing unrelated working-tree changes remain untouched.
- No hard-coded Supabase URL or anon key remains under `app`/`doc`; no credential was added to the commit. Missing configuration fails clearly when the configured Supabase client is created.
- Supabase SDK types are confined to the remote data-source/client layer; repository tests use narrow local/remote boundaries.
- Group task mutation methods intentionally throw an explicit G3-scope `UnsupportedOperationException`; Task 4 does not fake G3 behavior or queue membership commands.
- Task 5 must inject a real connectivity check when wiring the repository; using `{ true }` in production would violate the offline contract.
- `refreshGroups` atomically replaces the full group cache, which also drops child cache rows through the existing Room foreign keys; subsequent group-detail refresh repopulates members. Network failures occur before replacement and retain valid cache.
- `GroupMember` Task 1 has no profile fields, so profile display name/avatar are retained in the Task 3 cache projection but are not fabricated into the domain model.

## Commit

`e184dad feat: add G2 collaboration data source`
