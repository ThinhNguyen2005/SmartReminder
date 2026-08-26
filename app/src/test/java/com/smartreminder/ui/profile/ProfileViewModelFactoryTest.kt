package com.smartreminder.ui.profile

import androidx.lifecycle.ViewModel
import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.model.preferences.UserPreferences
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.RestorePreferencesResult
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ProfileViewModelFactoryTest {

    @Test
    fun `given supported class, when create called, then returns ProfileViewModel instance`() {
        val factory = ProfileViewModelFactory(
            repository = StubUserPreferencesRepository,
            syncCoordinator = StubUserPreferencesSyncCoordinator
        )

        val modelClass: Class<out ViewModel> = ProfileViewModel::class.java
        val result = factory.create(modelClass)

        assertTrue(result is ProfileViewModel)
    }

    @Test
    fun `given unsupported class, when create called, then throws IllegalArgumentException`() {
        val factory = ProfileViewModelFactory(
            repository = StubUserPreferencesRepository,
            syncCoordinator = StubUserPreferencesSyncCoordinator
        )

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnsupportedViewModel::class.java)
        }
    }
}

private class UnsupportedViewModel : ViewModel()

private object StubUserPreferencesRepository : UserPreferencesRepository {
    override val preferences: Flow<UserPreferences> = flowOf(UserPreferences())
    override suspend fun updateRhythm(wakeUpTime: LocalTime, sleepTime: LocalTime) = Unit
    override suspend fun updateGoals(goals: Set<UserGoal>) = Unit
    override suspend fun updateThemeMode(mode: ThemeMode) = Unit
    override suspend fun completeOnboarding(wakeUpTime: LocalTime, sleepTime: LocalTime, goals: Set<UserGoal>) = Unit
    override suspend fun resetOnboarding() = Unit
    override suspend fun replaceOnboardingPreferences(snapshot: OnboardingPreferencesSnapshot) = Unit
    override suspend fun clearOnboardingPreferences() = Unit
}

private object StubUserPreferencesSyncCoordinator : UserPreferencesSyncCoordinator {
    override suspend fun restoreForUser(userId: String): RestorePreferencesResult =
        RestorePreferencesResult.RestoredCompleted

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) = Unit

    override suspend fun signOutAndClearLocal() = Unit
}
