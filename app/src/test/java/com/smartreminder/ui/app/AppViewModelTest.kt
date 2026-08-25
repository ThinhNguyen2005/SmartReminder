package com.smartreminder.ui.app

import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.domain.model.UserGoal
import com.smartreminder.domain.model.UserPreferences
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAppRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAppRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given uncompleted onboarding, when observing appState, then transitions to Onboarding`() = runTest {
        val viewModel = AppViewModel(fakeRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.appState.collect {}
        }
        advanceUntilIdle()

        assertEquals(AppState.Onboarding, viewModel.appState.value)
    }

    @Test
    fun `given completed onboarding, when observing appState, then transitions to Main`() = runTest {
        fakeRepository.setPreferences(UserPreferences(onboardingCompleted = true))
        val viewModel = AppViewModel(fakeRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.appState.collect {}
        }
        advanceUntilIdle()

        assertEquals(AppState.Main, viewModel.appState.value)
    }

    @Test
    fun `given custom preferences, when completeOnboardingForAuthenticatedUser called, then preserves custom rhythm and goals`() = runTest {
        // Given: User previously configured custom rhythm/goals before logging in
        val customWake = LocalTime.of(8, 30)
        val customSleep = LocalTime.of(0, 15)
        val customGoals = setOf(UserGoal.STUDY, UserGoal.TEAMWORK)
        fakeRepository.setPreferences(
            UserPreferences(
                wakeUpTime = customWake,
                sleepTime = customSleep,
                goals = customGoals,
                onboardingCompleted = false
            )
        )

        val viewModel = AppViewModel(fakeRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.appState.collect {}
        }
        advanceUntilIdle()

        // When: User logs in with Google
        viewModel.completeOnboardingForAuthenticatedUser()
        advanceUntilIdle()

        // Then: Onboarding marked completed, but custom wake, sleep, and goals are PRESERVED
        val saved = fakeRepository.currentPreferences
        assertTrue(saved.onboardingCompleted)
        assertEquals(customWake, saved.wakeUpTime)
        assertEquals(customSleep, saved.sleepTime)
        assertEquals(customGoals, saved.goals)
        assertEquals(AppState.Main, viewModel.appState.value)
    }

    @Test
    fun `given completed onboarding, when resetOnboarding called, then onboardingCompleted is false and rhythm is preserved`() = runTest {
        val customWake = LocalTime.of(9, 0)
        fakeRepository.setPreferences(
            UserPreferences(
                wakeUpTime = customWake,
                onboardingCompleted = true
            )
        )

        val viewModel = AppViewModel(fakeRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.appState.collect {}
        }
        advanceUntilIdle()
        assertEquals(AppState.Main, viewModel.appState.value)

        // When: Reset onboarding
        viewModel.resetOnboarding()
        advanceUntilIdle()

        // Then: State becomes Onboarding, but custom wake time is preserved
        assertFalse(fakeRepository.currentPreferences.onboardingCompleted)
        assertEquals(customWake, fakeRepository.currentPreferences.wakeUpTime)
        assertEquals(AppState.Onboarding, viewModel.appState.value)
    }
}

private class FakeAppRepository : UserPreferencesRepository {
    private val _preferencesFlow = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = _preferencesFlow.asStateFlow()

    val currentPreferences: UserPreferences
        get() = _preferencesFlow.value

    fun setPreferences(prefs: UserPreferences) {
        _preferencesFlow.value = prefs
    }

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) {
        _preferencesFlow.value = _preferencesFlow.value.copy(
            wakeUpTime = wakeUpTime,
            sleepTime = sleepTime,
            goals = goals,
            onboardingCompleted = true
        )
    }

    override suspend fun updateRhythm(wakeUpTime: LocalTime, sleepTime: LocalTime) {
        _preferencesFlow.value = _preferencesFlow.value.copy(
            wakeUpTime = wakeUpTime,
            sleepTime = sleepTime
        )
    }

    override suspend fun updateGoals(goals: Set<UserGoal>) {
        _preferencesFlow.value = _preferencesFlow.value.copy(goals = goals)
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        _preferencesFlow.value = _preferencesFlow.value.copy(themeMode = mode)
    }

    override suspend fun resetOnboarding() {
        _preferencesFlow.value = _preferencesFlow.value.copy(onboardingCompleted = false)
    }
}
