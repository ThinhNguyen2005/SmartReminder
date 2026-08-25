package com.smartreminder.ui.profile

import com.smartreminder.domain.model.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.domain.model.UserGoal
import com.smartreminder.domain.model.UserPreferences
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
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeUserPreferencesRepository
    private lateinit var fakeSyncCoordinator: FakeProfileSyncCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeUserPreferencesRepository()
        fakeSyncCoordinator = FakeProfileSyncCoordinator(fakeRepository)
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
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
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
    fun `given loaded state, when wake time picker opened and dismissed, then state updates correctly`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenWakeTimePicker)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showWakeTimePicker)

        viewModel.onAction(ProfileUiAction.DismissWakeTimePicker)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showWakeTimePicker)
    }

    @Test
    fun `given loaded state, when sleep time picker opened and dismissed, then state updates correctly`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenSleepTimePicker)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showSleepTimePicker)

        viewModel.onAction(ProfileUiAction.DismissSleepTimePicker)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showSleepTimePicker)
    }

    @Test
    fun `given update wake time action, then repository is updated and picker is closed`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenWakeTimePicker)
        val newWake = LocalTime.of(5, 45)
        viewModel.onAction(ProfileUiAction.UpdateWakeTime(newWake))
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(newWake, loaded.wakeUpTime)
        assertEquals(newWake, fakeRepository.currentPreferences.wakeUpTime)
        assertFalse(loaded.showWakeTimePicker)
    }

    @Test
    fun `given update sleep time action, then repository is updated and picker is closed`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenSleepTimePicker)
        val newSleep = LocalTime.of(23, 15)
        viewModel.onAction(ProfileUiAction.UpdateSleepTime(newSleep))
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(newSleep, loaded.sleepTime)
        assertEquals(newSleep, fakeRepository.currentPreferences.sleepTime)
        assertFalse(loaded.showSleepTimePicker)
    }

    @Test
    fun `given sign out request and dismissal, then dialog state toggles properly`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.RequestSignOut)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showSignOutDialog)

        viewModel.onAction(ProfileUiAction.DismissSignOutDialog)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showSignOutDialog)
    }

    @Test
    fun `given confirm sign out, then calls syncCoordinator signOutAndClearLocal`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.RequestSignOut)
        viewModel.onAction(ProfileUiAction.ConfirmSignOut)
        advanceUntilIdle()

        // Then
        assertTrue(fakeSyncCoordinator.signOutCalled)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showSignOutDialog)
    }

    @Test
    fun `given confirm sign out failure, then exposes errorMessage on Profile state`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator)
        advanceUntilIdle()

        fakeSyncCoordinator.shouldThrowOnSignOut = true

        viewModel.onAction(ProfileUiAction.RequestSignOut)
        viewModel.onAction(ProfileUiAction.ConfirmSignOut)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertFalse(loaded.showSignOutDialog)
        assertEquals("Simulated cloud sign out failure", loaded.errorMessage)

        viewModel.onAction(ProfileUiAction.DismissError)
        assertEquals(null, (viewModel.uiState.value as ProfileUiState.Loaded).errorMessage)
    }
}

private class FakeProfileSyncCoordinator(
    private val localRepo: FakeUserPreferencesRepository
) : UserPreferencesSyncCoordinator {

    var signOutCalled: Boolean = false
    var shouldThrowOnSignOut: Boolean = false

    override suspend fun restoreForUser(userId: String): RestorePreferencesResult {
        return RestorePreferencesResult.RestoredCompleted
    }

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) {
        localRepo.completeOnboarding(wakeUpTime, sleepTime, goals)
    }

    override suspend fun signOutAndClearLocal() {
        if (shouldThrowOnSignOut) throw IOException("Simulated cloud sign out failure")
        signOutCalled = true
        localRepo.clearOnboardingPreferences()
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
