package com.smartreminder.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.smartreminder.domain.model.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.domain.model.UserGoal
import com.smartreminder.domain.model.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreUserPreferencesRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createRepository(
        initialPrefs: Preferences = emptyPreferences()
    ): Pair<DataStore<Preferences>, DataStoreUserPreferencesRepository> {
        val dataStore = InMemoryPreferencesDataStore(initialPrefs)
        return dataStore to DataStoreUserPreferencesRepository(dataStore)
    }

    @Test
    fun `given empty DataStore, when observing preferences, then emits default values`() = runTest(testDispatcher) {
        val (_, repository) = createRepository()

        // When
        val prefs = repository.preferences.first()

        // Then: Defaults are 07:00 / 23:30 / TASKS+PLANNING / onboardingCompleted=false / SYSTEM
        assertEquals(LocalTime.of(7, 0), prefs.wakeUpTime)
        assertEquals(LocalTime.of(23, 30), prefs.sleepTime)
        assertEquals(setOf(UserGoal.TASKS, UserGoal.PLANNING), prefs.goals)
        assertFalse(prefs.onboardingCompleted)
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
    }

    @Test
    fun `given custom values, when completing onboarding atomically, then all fields persist and completed is true`() = runTest(testDispatcher) {
        val (_, repository) = createRepository()

        // Given
        val wake = LocalTime.of(8, 0)
        val sleep = LocalTime.of(23, 0)
        val goals = setOf(UserGoal.STUDY, UserGoal.PLANNING, UserGoal.TEAMWORK)

        // When
        repository.completeOnboarding(wake, sleep, goals)
        val prefs = repository.preferences.first()

        // Then
        assertEquals(wake, prefs.wakeUpTime)
        assertEquals(sleep, prefs.sleepTime)
        assertEquals(goals, prefs.goals)
        assertTrue(prefs.onboardingCompleted)
    }

    @Test
    fun `given times, when completing onboarding, then minutes of day are persisted correctly`() = runTest(testDispatcher) {
        val (dataStore, repository) = createRepository()

        // Given: 08:30 (510 min) and 00:15 (15 min)
        val wake = LocalTime.of(8, 30)
        val sleep = LocalTime.of(0, 15)

        // When
        repository.completeOnboarding(wake, sleep, setOf(UserGoal.TASKS))
        val rawPrefs = dataStore.data.first()

        // Then: Raw DataStore contains Int minutes
        assertEquals(510, rawPrefs[PreferenceKeys.WAKE_UP_MINUTE])
        assertEquals(15, rawPrefs[PreferenceKeys.SLEEP_MINUTE])
    }

    @Test
    fun `given existing preferences, when updating rhythm, then only rhythm changes while goals remain`() = runTest(testDispatcher) {
        val (_, repository) = createRepository()

        // Given
        repository.completeOnboarding(
            LocalTime.of(7, 0),
            LocalTime.of(23, 30),
            setOf(UserGoal.ROUTINES, UserGoal.STUDY)
        )

        // When: Only update rhythm
        repository.updateRhythm(LocalTime.of(6, 30), LocalTime.of(22, 30))
        val prefs = repository.preferences.first()

        // Then
        assertEquals(LocalTime.of(6, 30), prefs.wakeUpTime)
        assertEquals(LocalTime.of(22, 30), prefs.sleepTime)
        assertEquals(setOf(UserGoal.ROUTINES, UserGoal.STUDY), prefs.goals)
        assertTrue(prefs.onboardingCompleted)
    }

    @Test
    fun `given existing preferences, when updating goals, then uses stable storageKeys`() = runTest(testDispatcher) {
        val (dataStore, repository) = createRepository()

        // When
        repository.updateGoals(setOf(UserGoal.TASKS, UserGoal.TEAMWORK))
        val rawPrefs = dataStore.data.first()
        val prefs = repository.preferences.first()

        // Then
        assertEquals(setOf("tasks", "teamwork"), rawPrefs[PreferenceKeys.SELECTED_GOALS])
        assertEquals(setOf(UserGoal.TASKS, UserGoal.TEAMWORK), prefs.goals)
    }

    @Test
    fun `given completed onboarding, when resetting onboarding, then completed becomes false but data is preserved`() = runTest(testDispatcher) {
        val (_, repository) = createRepository()

        // Given
        val customWake = LocalTime.of(9, 0)
        val customSleep = LocalTime.of(1, 0)
        val customGoals = setOf(UserGoal.STUDY)
        repository.completeOnboarding(customWake, customSleep, customGoals)

        // When
        repository.resetOnboarding()
        val prefs = repository.preferences.first()

        // Then
        assertFalse(prefs.onboardingCompleted)
        assertEquals(customWake, prefs.wakeUpTime)
        assertEquals(customSleep, prefs.sleepTime)
        assertEquals(customGoals, prefs.goals)
    }

    @Test
    fun `given theme mode, when updating theme, then enum storageKey persists`() = runTest(testDispatcher) {
        val (dataStore, repository) = createRepository()

        // When
        repository.updateThemeMode(ThemeMode.DARK)
        val rawPrefs = dataStore.data.first()
        val prefs = repository.preferences.first()

        // Then
        assertEquals("dark", rawPrefs[PreferenceKeys.THEME_MODE])
        assertEquals(ThemeMode.DARK, prefs.themeMode)
    }

    @Test
    fun `given corrupted minutes in DataStore, when mapping, then falls back safely without throwing`() = runTest(testDispatcher) {
        val (dataStore, repository) = createRepository()

        // Given: Out of range minute values (e.g. 9999)
        dataStore.edit { it[PreferenceKeys.WAKE_UP_MINUTE] = 9999 }

        // When
        val prefs = repository.preferences.first()

        // Then: Safe fallback to default wake up time
        assertEquals(UserPreferences.DEFAULT_WAKE_TIME, prefs.wakeUpTime)
    }

    @Test
    fun `given custom wake, sleep, goals and DARK theme, when clearOnboardingPreferences, then resets to defaults and preserves DARK theme`() = runTest(testDispatcher) {
        val (_, repository) = createRepository()

        // Given
        repository.completeOnboarding(
            wakeUpTime = LocalTime.of(6, 0),
            sleepTime = LocalTime.of(22, 0),
            goals = setOf(UserGoal.ROUTINES, UserGoal.TEAMWORK)
        )
        repository.updateThemeMode(ThemeMode.DARK)

        // When
        repository.clearOnboardingPreferences()
        val prefs = repository.preferences.first()

        // Then
        assertEquals(UserPreferences.DEFAULT_WAKE_TIME, prefs.wakeUpTime)
        assertEquals(UserPreferences.DEFAULT_SLEEP_TIME, prefs.sleepTime)
        assertEquals(UserPreferences.DEFAULT_GOALS, prefs.goals)
        assertFalse(prefs.onboardingCompleted)
        assertEquals(ThemeMode.DARK, prefs.themeMode)
    }

    @Test
    fun `given remote snapshot, when replaceOnboardingPreferences, then all onboarding fields match snapshot and theme is preserved`() = runTest(testDispatcher) {
        val (_, repository) = createRepository()

        // Given
        repository.updateThemeMode(ThemeMode.LIGHT)
        val snapshot = OnboardingPreferencesSnapshot(
            wakeUpTime = LocalTime.of(5, 30),
            sleepTime = LocalTime.of(21, 45),
            goals = setOf(UserGoal.STUDY, UserGoal.PLANNING),
            onboardingCompleted = true
        )

        // When
        repository.replaceOnboardingPreferences(snapshot)
        val prefs = repository.preferences.first()

        // Then
        assertEquals(LocalTime.of(5, 30), prefs.wakeUpTime)
        assertEquals(LocalTime.of(21, 45), prefs.sleepTime)
        assertEquals(setOf(UserGoal.STUDY, UserGoal.PLANNING), prefs.goals)
        assertTrue(prefs.onboardingCompleted)
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)
    }
}

/**
 * Clean in-memory [DataStore] implementation for unit tests.
 */
private class InMemoryPreferencesDataStore(
    initialPreferences: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val _dataFlow = MutableStateFlow(initialPreferences)
    override val data: Flow<Preferences> = _dataFlow.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(_dataFlow.value)
        _dataFlow.value = updated
        return updated
    }
}
