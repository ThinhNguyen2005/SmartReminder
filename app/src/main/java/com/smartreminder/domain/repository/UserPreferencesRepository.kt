package com.smartreminder.domain.repository

import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/**
 * Domain contract for user preferences persistence.
 * Implementation lives in data layer — domain does not know about DataStore.
 */
interface UserPreferencesRepository {

    /** Reactive stream of current preferences. Emits defaults when storage is empty. */
    val preferences: Flow<UserPreferences>

    /**
     * Atomically persists all onboarding data and marks onboarding as completed.
     * All fields are written in a single transaction — no partial state possible.
     *
     * @throws java.io.IOException if write fails
     */
    suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    )

    /** @throws java.io.IOException if write fails */
    suspend fun updateRhythm(wakeUpTime: LocalTime, sleepTime: LocalTime)

    /** @throws java.io.IOException if write fails */
    suspend fun updateGoals(goals: Set<UserGoal>)

    /** @throws java.io.IOException if write fails */
    suspend fun updateThemeMode(mode: ThemeMode)

    /** Resets onboarding flag to false. Rhythm and goals data are preserved. */
    suspend fun resetOnboarding()

    /**
     * Atomically replaces all onboarding fields from a remote snapshot.
     * Preserves device-local themeMode.
     *
     * @throws java.io.IOException if write fails
     */
    suspend fun replaceOnboardingPreferences(snapshot: OnboardingPreferencesSnapshot)

    /**
     * Clears all onboarding and rhythm preferences, reverting to defaults.
     * Preserves device-local themeMode.
     *
     * @throws java.io.IOException if write fails
     */
    suspend fun clearOnboardingPreferences()
}
