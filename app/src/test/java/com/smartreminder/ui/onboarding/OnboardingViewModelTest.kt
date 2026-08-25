package com.smartreminder.ui.onboarding

import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.domain.model.UserGoal
import com.smartreminder.domain.model.UserPreferences
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.IOException
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeUserPreferencesRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeUserPreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given default repository, when viewModel initializes, then uiState hydrates with defaults`() = runTest {
        // When
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(LocalTime.of(7, 0), state.wakeUpTime)
        assertEquals(LocalTime.of(23, 30), state.sleepTime)
        assertEquals(setOf(UserGoal.TASKS, UserGoal.PLANNING), state.selectedGoals)
        assertEquals(OnboardingStep.RHYTHM, state.currentStep)
        assertFalse(state.isSaving)
        assertFalse(state.saveError)
    }

    @Test
    fun `given repository with previous data, when viewModel initializes, then uiState hydrates previous rhythm and goals`() = runTest {
        // Given: User reset onboarding after previously setting custom values
        fakeRepository.setPreferences(
            UserPreferences(
                wakeUpTime = LocalTime.of(8, 15),
                sleepTime = LocalTime.of(0, 0),
                goals = setOf(UserGoal.STUDY, UserGoal.TEAMWORK),
                onboardingCompleted = false
            )
        )

        // When
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // Then: State hydrates previous values
        val state = viewModel.uiState.value
        assertEquals(LocalTime.of(8, 15), state.wakeUpTime)
        assertEquals(LocalTime.of(0, 0), state.sleepTime)
        assertEquals(setOf(UserGoal.STUDY, UserGoal.TEAMWORK), state.selectedGoals)
    }

    @Test
    fun `given uiState, when toggling goal, then goal is added or removed`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // When: Toggle STUDY (not selected by default)
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.STUDY))
        assertTrue(viewModel.uiState.value.selectedGoals.contains(UserGoal.STUDY))

        // When: Toggle TASKS (selected by default)
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.TASKS))
        assertFalse(viewModel.uiState.value.selectedGoals.contains(UserGoal.TASKS))
    }

    @Test
    fun `given custom selections, when completing onboarding, then repository receives exact current snapshot atomically`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // Given: User customizes wake time and goals
        viewModel.onAction(OnboardingAction.UpdateWakeTime(LocalTime.of(6, 45)))
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.ROUTINES))

        // When: Complete
        viewModel.onAction(OnboardingAction.Complete)
        advanceUntilIdle()

        // Then: Repository saved exact snapshot
        val saved = fakeRepository.currentPreferences
        assertEquals(LocalTime.of(6, 45), saved.wakeUpTime)
        assertEquals(LocalTime.of(23, 30), saved.sleepTime)
        assertTrue(saved.goals.contains(UserGoal.ROUTINES))
        assertTrue(saved.onboardingCompleted)
    }

    @Test
    fun `given step 1 customizations, when skipping onboarding, then exact current snapshot is saved`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // Given: User only customizes sleep time then taps Skip immediately
        viewModel.onAction(OnboardingAction.UpdateSleepTime(LocalTime.of(22, 0)))

        // When: Skip
        viewModel.onAction(OnboardingAction.Skip)
        advanceUntilIdle()

        // Then: Snapshot saved with customized sleep and default wake/goals
        val saved = fakeRepository.currentPreferences
        assertEquals(LocalTime.of(7, 0), saved.wakeUpTime)
        assertEquals(LocalTime.of(22, 0), saved.sleepTime)
        assertEquals(setOf(UserGoal.TASKS, UserGoal.PLANNING), saved.goals)
        assertTrue(saved.onboardingCompleted)
    }

    @Test
    fun `given repository that throws IOException, when completing onboarding, then saveError is exposed to UI`() = runTest {
        fakeRepository.shouldThrowOnWrite = true
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // When: Complete
        viewModel.onAction(OnboardingAction.Complete)
        advanceUntilIdle()

        // Then: isSaving is false, saveError is true
        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.saveError)
    }

    @Test
    fun `given successful complete, when saving finishes, then isSaving is reset to false`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // When: Complete
        viewModel.onAction(OnboardingAction.Complete)
        advanceUntilIdle()

        // Then: isSaving is reset to false
        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertFalse(state.saveError)
    }

    @Test
    fun `given completed onboarding, when completing again after reset, then isSaving does not block subsequent attempts`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        // First completion
        viewModel.onAction(OnboardingAction.Complete)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)

        // Reset
        fakeRepository.resetOnboarding()
        advanceUntilIdle()

        // Second completion with new wake time
        viewModel.onAction(OnboardingAction.UpdateWakeTime(LocalTime.of(8, 0)))
        viewModel.onAction(OnboardingAction.Complete)
        advanceUntilIdle()

        // Then: Second completion succeeds without being blocked by isSaving
        assertEquals(LocalTime.of(8, 0), fakeRepository.currentPreferences.wakeUpTime)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `given steps, when navigating, then currentStep updates correctly`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository)
        advanceUntilIdle()

        viewModel.onAction(OnboardingAction.NavigateToStep(OnboardingStep.GOALS))
        assertEquals(OnboardingStep.GOALS, viewModel.uiState.value.currentStep)

        viewModel.onAction(OnboardingAction.NavigateToStep(OnboardingStep.TIMELINE))
        assertEquals(OnboardingStep.TIMELINE, viewModel.uiState.value.currentStep)
    }
}

/** Fake in-memory implementation for deterministic ViewModel unit tests */
private class FakeUserPreferencesRepository : UserPreferencesRepository {

    private val _preferencesFlow = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = _preferencesFlow.asStateFlow()

    val currentPreferences: UserPreferences
        get() = _preferencesFlow.value

    var shouldThrowOnWrite: Boolean = false

    fun setPreferences(prefs: UserPreferences) {
        _preferencesFlow.value = prefs
    }

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        _preferencesFlow.value = _preferencesFlow.value.copy(
            wakeUpTime = wakeUpTime,
            sleepTime = sleepTime,
            goals = goals,
            onboardingCompleted = true
        )
    }

    override suspend fun updateRhythm(wakeUpTime: LocalTime, sleepTime: LocalTime) {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        _preferencesFlow.value = _preferencesFlow.value.copy(
            wakeUpTime = wakeUpTime,
            sleepTime = sleepTime
        )
    }

    override suspend fun updateGoals(goals: Set<UserGoal>) {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        _preferencesFlow.value = _preferencesFlow.value.copy(goals = goals)
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        _preferencesFlow.value = _preferencesFlow.value.copy(themeMode = mode)
    }

    override suspend fun resetOnboarding() {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        _preferencesFlow.value = _preferencesFlow.value.copy(onboardingCompleted = false)
    }
}
