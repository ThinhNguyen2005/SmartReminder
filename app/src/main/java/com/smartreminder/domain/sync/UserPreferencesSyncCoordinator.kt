package com.smartreminder.domain.sync

import com.smartreminder.domain.model.UserGoal
import java.time.LocalTime

sealed interface RestorePreferencesResult {
    data object RestoredCompleted : RestorePreferencesResult
    data object NeedsOnboarding : RestorePreferencesResult
}

/**
 * Coordinates local DataStore persistence with remote Supabase cloud persistence.
 * Enforces atomic flush-before-signout and cloud-before-local-completion invariants.
 */
interface UserPreferencesSyncCoordinator {

    /**
     * Fetches remote preferences for [userId].
     * If remote exists and completed, hydrates local DataStore and returns [RestorePreferencesResult.RestoredCompleted].
     * If remote exists and not completed, hydrates local DataStore and returns [RestorePreferencesResult.NeedsOnboarding].
     * If remote is null (new account), clears local onboarding preferences and returns [RestorePreferencesResult.NeedsOnboarding].
     *
     * @throws Exception if network or remote fetch fails (does NOT mutate local on failure).
     */
    suspend fun restoreForUser(
        userId: String
    ): RestorePreferencesResult

    /**
     * Persists onboarding completion.
     * If authenticated with Supabase, upserts to cloud FIRST, then commits to local DataStore.
     * If unauthenticated guest, writes directly to local DataStore.
     *
     * @throws Exception if cloud upsert or local write fails.
     */
    suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    )

    /**
     * Flushes current local preferences to cloud (if authenticated and onboarding completed),
     * signs out of Supabase auth, and clears local onboarding preferences from DataStore (preserving theme).
     *
     * @throws Exception if cloud flush or auth sign out fails.
     */
    suspend fun signOutAndClearLocal()
}
