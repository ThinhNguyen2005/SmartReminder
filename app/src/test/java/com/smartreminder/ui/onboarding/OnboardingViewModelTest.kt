package com.smartreminder.ui.onboarding

import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.model.preferences.UserPreferences
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.RestorePreferencesResult
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator
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
    private lateinit var fakeSyncCoordinator: FakeUserPreferencesSyncCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeUserPreferencesRepository()
        fakeSyncCoordinator = FakeUserPreferencesSyncCoordinator(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given default repository, when viewModel initializes, then uiState hydrates with defaults`() = runTest {
        // When
        val viewModel = OnboardingViewModel(fakeRepository, fakeSyncCoordinator)
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
        // Given
        fakeRepository.setPreferences(
            UserPreferences(
                wakeUpTime = LocalTime.of(8, 30),
                sleepTime = LocalTime.of(0, 15),
                goals = setOf(UserGoal.STUDY, UserGoal.TEAMWORK),
                onboardingCompleted = false
            )
        )

        // When
        val viewModel = OnboardingViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertEquals(LocalTime.of(8, 30), state.wakeUpTime)
        assertEquals(LocalTime.of(0, 15), state.sleepTime)
        assertEquals(setOf(UserGoal.STUDY, UserGoal.TEAMWORK), state.selectedGoals)
    }

    @Test
    fun `given rhythm updates, when onAction called, then uiState updates and picker closes`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(OnboardingAction.OpenTimePicker(TimePickerTarget.WAKE_UP))
        assertEquals(TimePickerTarget.WAKE_UP, viewModel.uiState.value.activeTimePicker)

        viewModel.onAction(OnboardingAction.UpdateWakeTime(LocalTime.of(6, 45)))
        assertEquals(LocalTime.of(6, 45), viewModel.uiState.value.wakeUpTime)
        assertEquals(null, viewModel.uiState.value.activeTimePicker)

        viewModel.onAction(OnboardingAction.OpenTimePicker(TimePickerTarget.SLEEP))
        viewModel.onAction(OnboardingAction.UpdateSleepTime(LocalTime.of(22, 15)))
        assertEquals(LocalTime.of(22, 15), viewModel.uiState.value.sleepTime)
        assertEquals(null, viewModel.uiState.value.activeTimePicker)
    }

    @Test
    fun `given goal selection, when toggling, then goals are added or removed`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        // Initially contains TASKS and PLANNING
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.STUDY))
        assertTrue(UserGoal.STUDY in viewModel.uiState.value.selectedGoals)

        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.TASKS))
        assertFalse(UserGoal.TASKS in viewModel.uiState.value.selectedGoals)
    }

    @Test
    fun `given complete action, when sync coordinator succeeds, then persists data to repository`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        val customWake = LocalTime.of(6, 0)
        val customSleep = LocalTime.of(22, 0)
        val customGoals = setOf(UserGoal.STUDY, UserGoal.ROUTINES)

        viewModel.onAction(OnboardingAction.UpdateWakeTime(customWake))
        viewModel.onAction(OnboardingAction.UpdateSleepTime(customSleep))
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.TASKS)) // remove
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.PLANNING)) // remove
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.STUDY)) // add
        viewModel.onAction(OnboardingAction.ToggleGoal(UserGoal.ROUTINES)) // add

        // When
        viewModel.onAction(OnboardingAction.Complete)
        advanceUntilIdle()

        // Then
        assertTrue(fakeRepository.currentPreferences.onboardingCompleted)
        assertEquals(customWake, fakeRepository.currentPreferences.wakeUpTime)
        assertEquals(customSleep, fakeRepository.currentPreferences.sleepTime)
        assertEquals(customGoals, fakeRepository.currentPreferences.goals)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.saveError)
    }

    @Test
    fun `given cloud sync failure on complete, then saveError is true and local completed is not true`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        fakeSyncCoordinator.shouldThrowOnComplete = true

        // When
        viewModel.onAction(OnboardingAction.Complete)
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.saveError)
        assertFalse(fakeRepository.currentPreferences.onboardingCompleted)
    }

    @Test
    fun `given step navigation, when onAction called, then currentStep updates`() = runTest {
        val viewModel = OnboardingViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(OnboardingAction.NavigateToStep(OnboardingStep.GOALS))
        assertEquals(OnboardingStep.GOALS, viewModel.uiState.value.currentStep)

        viewModel.onAction(OnboardingAction.NavigateToStep(OnboardingStep.TIMELINE))
        assertEquals(OnboardingStep.TIMELINE, viewModel.uiState.value.currentStep)
    }
}

/** Fake sync coordinator for OnboardingViewModel unit tests */
private class FakeUserPreferencesSyncCoordinator(
    private val localRepo: FakeUserPreferencesRepository
) : UserPreferencesSyncCoordinator {

    var shouldThrowOnComplete: Boolean = false

    override suspend fun restoreForUser(userId: String): RestorePreferencesResult {
        return RestorePreferencesResult.RestoredCompleted
    }

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) {
        if (shouldThrowOnComplete) throw IOException("Simulated cloud write failure")
        localRepo.completeOnboarding(wakeUpTime, sleepTime, goals)
    }

    override suspend fun signOutAndClearLocal() {
        localRepo.clearOnboardingPreferences()
    }
}

/** Fake in-memory repository implementation for deterministic ViewModel unit tests */
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

    override suspend fun replaceOnboardingPreferences(snapshot: OnboardingPreferencesSnapshot) {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        _preferencesFlow.value = _preferencesFlow.value.copy(
            wakeUpTime = snapshot.wakeUpTime,
            sleepTime = snapshot.sleepTime,
            goals = snapshot.goals,
            onboardingCompleted = snapshot.onboardingCompleted
        )
    }

    override suspend fun clearOnboardingPreferences() {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        val defaults = UserPreferences()
        _preferencesFlow.value = _preferencesFlow.value.copy(
            wakeUpTime = defaults.wakeUpTime,
            sleepTime = defaults.sleepTime,
            goals = defaults.goals,
            onboardingCompleted = false
        )
    }
}
