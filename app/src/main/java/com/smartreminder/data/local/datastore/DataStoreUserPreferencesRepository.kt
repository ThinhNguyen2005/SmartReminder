package com.smartreminder.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.smartreminder.data.local.datastore.UserPreferencesMapper.toLocalTimeSafe
import com.smartreminder.data.local.datastore.UserPreferencesMapper.toMinutesOfDay
import com.smartreminder.data.local.datastore.UserPreferencesMapper.toStorageKeys
import com.smartreminder.data.local.datastore.UserPreferencesMapper.toUserGoals
import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.model.preferences.UserPreferences
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

/**
 * DataStore-backed implementation of [UserPreferencesRepository].
 * All reads are reactive via [Flow]; all writes are suspend atomic edits.
 */
class DataStoreUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> mapToUserPreferences(prefs) }

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.WAKE_UP_MINUTE] = wakeUpTime.toMinutesOfDay()
            prefs[PreferenceKeys.SLEEP_MINUTE] = sleepTime.toMinutesOfDay()
            prefs[PreferenceKeys.SELECTED_GOALS] = goals.toStorageKeys()
            prefs[PreferenceKeys.ONBOARDING_COMPLETED] = true
        }
    }

    override suspend fun updateRhythm(wakeUpTime: LocalTime, sleepTime: LocalTime) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.WAKE_UP_MINUTE] = wakeUpTime.toMinutesOfDay()
            prefs[PreferenceKeys.SLEEP_MINUTE] = sleepTime.toMinutesOfDay()
        }
    }

    override suspend fun updateGoals(goals: Set<UserGoal>) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.SELECTED_GOALS] = goals.toStorageKeys()
        }
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.THEME_MODE] = mode.storageKey
        }
    }

    override suspend fun resetOnboarding() {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.ONBOARDING_COMPLETED] = false
        }
    }

    override suspend fun replaceOnboardingPreferences(snapshot: OnboardingPreferencesSnapshot) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.WAKE_UP_MINUTE] = snapshot.wakeUpTime.toMinutesOfDay()
            prefs[PreferenceKeys.SLEEP_MINUTE] = snapshot.sleepTime.toMinutesOfDay()
            prefs[PreferenceKeys.SELECTED_GOALS] = snapshot.goals.toStorageKeys()
            prefs[PreferenceKeys.ONBOARDING_COMPLETED] = snapshot.onboardingCompleted
        }
    }

    override suspend fun clearOnboardingPreferences() {
        dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.WAKE_UP_MINUTE)
            prefs.remove(PreferenceKeys.SLEEP_MINUTE)
            prefs.remove(PreferenceKeys.SELECTED_GOALS)
            prefs.remove(PreferenceKeys.ONBOARDING_COMPLETED)
        }
    }

    private fun mapToUserPreferences(prefs: Preferences): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            wakeUpTime = prefs[PreferenceKeys.WAKE_UP_MINUTE]
                ?.toLocalTimeSafe(defaults.wakeUpTime)
                ?: defaults.wakeUpTime,
            sleepTime = prefs[PreferenceKeys.SLEEP_MINUTE]
                ?.toLocalTimeSafe(defaults.sleepTime)
                ?: defaults.sleepTime,
            goals = prefs[PreferenceKeys.SELECTED_GOALS]
                ?.toUserGoals()
                ?: defaults.goals,
            onboardingCompleted = prefs[PreferenceKeys.ONBOARDING_COMPLETED]
                ?: defaults.onboardingCompleted,
            themeMode = prefs[PreferenceKeys.THEME_MODE]
                ?.let { ThemeMode.fromStorageKey(it) }
                ?: defaults.themeMode
        )
    }
}
