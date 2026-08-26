package com.smartreminder.data.sync

import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.repository.UserPreferencesCloudRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.RestorePreferencesResult
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Default implementation of [UserPreferencesSyncCoordinator].
 * Coordinates local DataStore and Supabase cloud persistence.
 */
class DefaultUserPreferencesSyncCoordinator(
    private val localRepository: UserPreferencesRepository,
    private val cloudRepository: UserPreferencesCloudRepository,
    private val getCurrentUserId: () -> String?,
    private val signOutAuth: suspend () -> Unit
) : UserPreferencesSyncCoordinator {

    constructor(
        localRepository: UserPreferencesRepository,
        cloudRepository: UserPreferencesCloudRepository,
        supabase: SupabaseClient
    ) : this(
        localRepository = localRepository,
        cloudRepository = cloudRepository,
        getCurrentUserId = { supabase.auth.currentUserOrNull()?.id },
        signOutAuth = { supabase.auth.signOut() }
    )

    override suspend fun restoreForUser(userId: String): RestorePreferencesResult {
        // 1. Fetch remote FIRST (any exception will propagate without modifying local storage)
        val remoteSnapshot = cloudRepository.getForUser(userId)

        return if (remoteSnapshot == null) {
            // Case A: New account -> Clear local onboarding data to prevent inheriting guest data
            localRepository.clearOnboardingPreferences()
            RestorePreferencesResult.NeedsOnboarding
        } else if (!remoteSnapshot.onboardingCompleted) {
            // Case B: Remote exists but onboarding not completed
            localRepository.replaceOnboardingPreferences(remoteSnapshot)
            RestorePreferencesResult.NeedsOnboarding
        } else {
            // Case C: Remote exists and onboarding completed
            localRepository.replaceOnboardingPreferences(remoteSnapshot)
            RestorePreferencesResult.RestoredCompleted
        }
    }

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) {
        val userId = getCurrentUserId()

        if (userId != null) {
            val snapshot = OnboardingPreferencesSnapshot(
                wakeUpTime = wakeUpTime,
                sleepTime = sleepTime,
                goals = goals,
                onboardingCompleted = true
            )
            // Upsert cloud FIRST before mutating local storage to prevent early navigation
            cloudRepository.upsertForUser(userId, snapshot)
        }

        localRepository.completeOnboarding(wakeUpTime, sleepTime, goals)
    }

    override suspend fun signOutAndClearLocal() {
        val userId = getCurrentUserId()

        if (userId != null) {
            val currentPrefs = localRepository.preferences.first()
            if (currentPrefs.onboardingCompleted) {
                val snapshot = OnboardingPreferencesSnapshot(
                    wakeUpTime = currentPrefs.wakeUpTime,
                    sleepTime = currentPrefs.sleepTime,
                    goals = currentPrefs.goals,
                    onboardingCompleted = true
                )
                // 1. Cloud flush latest snapshot
                cloudRepository.upsertForUser(userId, snapshot)
            }
        }

        // 2. Sign out of Auth session
        signOutAuth()

        // 3. Clear local onboarding preferences (theme is preserved)
        localRepository.clearOnboardingPreferences()
    }
}
