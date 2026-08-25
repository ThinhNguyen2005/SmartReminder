package com.smartreminder.ui.profile

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
class ProfileViewModelTest {

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
    fun `given default repository, when viewModel initializes, then uiState hydrates with Loaded state`() = runTest {
        // Given
        val customWake = LocalTime.of(6, 30)
        val customSleep = LocalTime.of(22, 30)
        fakeRepository.setPreferences(
            UserPreferences(
                wakeUpTime = customWake,
                sleepTime = customSleep,
                themeMode = ThemeMode.DARK
            )
        )

        // When
        val viewModel = ProfileViewModel(fakeRepository)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Loaded)
        val loaded = state as ProfileUiState.Loaded
        assertEquals(customWake, loaded.wakeUpTime)
        assertEquals(customSleep, loaded.sleepTime)
        assertEquals(ThemeMode.DARK, loaded.themeMode)
        assertFalse(loaded.showSignOutDialog)
        assertFalse(loaded.showWakeTimePicker)
        assertFalse(loaded.showSleepTimePicker)
    }

    @Test
    fun `when RequestSignOut action dispatched, then showSignOutDialog becomes true`() = runTest {
        // Given
        val viewModel = ProfileViewModel(fakeRepository)
        advanceUntilIdle()

        // When
        viewModel.onAction(ProfileUiAction.RequestSignOut)

        // Then
        val state = viewModel.uiState.value as ProfileUiState.Loaded
        assertTrue(state.showSignOutDialog)
    }

    @Test
    fun `when DismissSignOutDialog action dispatched, then showSignOutDialog becomes false`() = runTest {
        // Given
        val viewModel = ProfileViewModel(fakeRepository)
        advanceUntilIdle()
        viewModel.onAction(ProfileUiAction.RequestSignOut)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showSignOutDialog)

        // When
        viewModel.onAction(ProfileUiAction.DismissSignOutDialog)

        // Then
        val state = viewModel.uiState.value as ProfileUiState.Loaded
        assertFalse(state.showSignOutDialog)
    }

    @Test
    fun `when OpenWakeTimePicker and DismissWakeTimePicker dispatched, state updates accordingly`() = runTest {
        // Given
        val viewModel = ProfileViewModel(fakeRepository)
        advanceUntilIdle()

        // When
        viewModel.onAction(ProfileUiAction.OpenWakeTimePicker)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showWakeTimePicker)

        viewModel.onAction(ProfileUiAction.DismissWakeTimePicker)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showWakeTimePicker)
    }

    @Test
    fun `when UpdateWakeTime dispatched, then repository is updated and picker is dismissed`() = runTest {
        // Given
        val viewModel = ProfileViewModel(fakeRepository)
        advanceUntilIdle()
        val newWakeTime = LocalTime.of(5, 45)

        // When
        viewModel.onAction(ProfileUiAction.UpdateWakeTime(newWakeTime))
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(newWakeTime, state.wakeUpTime)
        assertEquals(newWakeTime, fakeRepository.currentPreferences.wakeUpTime)
        assertFalse(state.showWakeTimePicker)
    }

    @Test
    fun `when OpenSleepTimePicker and DismissSleepTimePicker dispatched, state updates accordingly`() = runTest {
        // Given
        val viewModel = ProfileViewModel(fakeRepository)
        advanceUntilIdle()

        // When
        viewModel.onAction(ProfileUiAction.OpenSleepTimePicker)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showSleepTimePicker)

        viewModel.onAction(ProfileUiAction.DismissSleepTimePicker)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showSleepTimePicker)
    }

    @Test
    fun `when UpdateSleepTime dispatched, then repository is updated and picker is dismissed`() = runTest {
        // Given
        val viewModel = ProfileViewModel(fakeRepository)
        advanceUntilIdle()
        val newSleepTime = LocalTime.of(23, 15)

        // When
        viewModel.onAction(ProfileUiAction.UpdateSleepTime(newSleepTime))
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(newSleepTime, state.sleepTime)
        assertEquals(newSleepTime, fakeRepository.currentPreferences.sleepTime)
        assertFalse(state.showSleepTimePicker)
    }

    @Test
    fun `when ConfirmSignOut dispatched, onSignedOut callback is invoked`() = runTest {
        // Given
        var signedOutCalled = false
        val viewModel = ProfileViewModel(
            repository = fakeRepository,
            onSignedOut = { signedOutCalled = true }
        )
        advanceUntilIdle()

        // When
        viewModel.onAction(ProfileUiAction.ConfirmSignOut)
        advanceUntilIdle()

        // Then
        assertTrue(signedOutCalled)
    }
}

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
