package com.smartreminder.ui.profile

import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.model.preferences.UserPreferences
import com.smartreminder.domain.model.user.UserProfile
import com.smartreminder.domain.repository.UserProfileRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var fakeUserProfileRepository: FakeUserProfileRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeUserPreferencesRepository()
        fakeSyncCoordinator = FakeProfileSyncCoordinator(fakeRepository)
        fakeUserProfileRepository = FakeUserProfileRepository()
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
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Loaded)
        val loaded = state as ProfileUiState.Loaded
        assertEquals("Alex Doe", loaded.displayName)
        assertEquals("alex@example.com", loaded.email)
        assertEquals("https://example.com/alex.jpg", loaded.avatarUrl)
        assertEquals(customWake, loaded.wakeUpTime)
        assertEquals(customSleep, loaded.sleepTime)
        assertEquals(ThemeMode.DARK, loaded.themeMode)
        assertFalse(loaded.showSignOutDialog)
        assertFalse(loaded.showWakeTimePicker)
        assertFalse(loaded.showSleepTimePicker)
        assertFalse(loaded.showThemePicker)
        assertFalse(loaded.isSyncing)
        assertFalse(loaded.showEditProfileDialog)
    }

    @Test
    fun `given loaded state, when wake time picker opened and dismissed, then state updates correctly`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenWakeTimePicker)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showWakeTimePicker)

        viewModel.onAction(ProfileUiAction.DismissWakeTimePicker)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showWakeTimePicker)
    }

    @Test
    fun `given loaded state, when sleep time picker opened and dismissed, then state updates correctly`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenSleepTimePicker)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showSleepTimePicker)

        viewModel.onAction(ProfileUiAction.DismissSleepTimePicker)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showSleepTimePicker)
    }

    @Test
    fun `given update wake time action, then repository is updated and picker is closed`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
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
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
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
    fun `given theme picker opened and dismissed, then showThemePicker state updates`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenThemePicker)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showThemePicker)

        viewModel.onAction(ProfileUiAction.DismissThemePicker)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showThemePicker)
    }

    @Test
    fun `given update theme mode action, then repository is updated and picker is closed`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenThemePicker)
        viewModel.onAction(ProfileUiAction.UpdateThemeMode(ThemeMode.LIGHT))
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(ThemeMode.LIGHT, loaded.themeMode)
        assertEquals(ThemeMode.LIGHT, fakeRepository.currentPreferences.themeMode)
        assertFalse(loaded.showThemePicker)
    }

    @Test
    fun `given force sync requested and succeeds, then coordinator is called and syncMessage is shown`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.RequestForceSync)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertTrue(fakeSyncCoordinator.forceSyncCalled)
        assertFalse(loaded.isSyncing)
        assertEquals("Đồng bộ tài khoản thành công!", loaded.syncMessage)

        viewModel.onAction(ProfileUiAction.DismissSyncMessage)
        assertNull((viewModel.uiState.value as ProfileUiState.Loaded).syncMessage)
    }

    @Test
    fun `given force sync requested and fails, then error syncMessage is exposed`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        fakeSyncCoordinator.shouldThrowOnForceSync = true

        viewModel.onAction(ProfileUiAction.RequestForceSync)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertFalse(loaded.isSyncing)
        assertNotNull(loaded.syncMessage)
        assertTrue(loaded.syncMessage!!.contains("Đồng bộ thất bại"))
    }

    @Test
    fun `given edit profile dialog, when opened and dismissed, then showEditProfileDialog toggles`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.OpenEditProfile)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showEditProfileDialog)

        viewModel.onAction(ProfileUiAction.DismissEditProfile)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showEditProfileDialog)
    }

    @Test
    fun `given save profile action, then userProfileRepository is updated with new name and avatar`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        val newName = "Batman"
        val newAvatar = "https://example.com/batman.png"
        viewModel.onAction(ProfileUiAction.SaveProfile(newName, newAvatar))
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertEquals(newName, loaded.displayName)
        assertEquals(newAvatar, loaded.avatarUrl)
        assertFalse(loaded.showEditProfileDialog)
        assertFalse(loaded.isSavingProfile)
        assertEquals(newName, fakeUserProfileRepository.profile.displayName)
        assertEquals(newAvatar, fakeUserProfileRepository.profile.avatarUrl)
    }

    @Test
    fun `given save profile failure, then errorMessage is displayed and isSavingProfile resets`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        fakeUserProfileRepository.shouldThrowOnUpdate = true
        viewModel.onAction(ProfileUiAction.SaveProfile("New Name", null))
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as ProfileUiState.Loaded
        assertFalse(loaded.isSavingProfile)
        assertEquals("Update profile failed", loaded.errorMessage)
    }

    @Test
    fun `given sign out request and dismissal, then dialog state toggles properly`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
        advanceUntilIdle()

        viewModel.onAction(ProfileUiAction.RequestSignOut)
        assertTrue((viewModel.uiState.value as ProfileUiState.Loaded).showSignOutDialog)

        viewModel.onAction(ProfileUiAction.DismissSignOutDialog)
        assertFalse((viewModel.uiState.value as ProfileUiState.Loaded).showSignOutDialog)
    }

    @Test
    fun `given confirm sign out, then calls syncCoordinator signOutAndClearLocal`() = runTest {
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
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
        val viewModel = ProfileViewModel(fakeRepository, fakeSyncCoordinator, fakeUserProfileRepository)
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

private class FakeUserProfileRepository : UserProfileRepository {
    var profile = UserProfile(
        displayName = "Alex Doe",
        email = "alex@example.com",
        avatarUrl = "https://example.com/alex.jpg"
    )
    var shouldThrowOnUpdate: Boolean = false

    override suspend fun getCurrentProfile(): UserProfile = profile

    override suspend fun updateProfile(displayName: String?, avatarUrl: String?) {
        if (shouldThrowOnUpdate) throw IOException("Update profile failed")
        profile = profile.copy(
            displayName = displayName ?: profile.displayName,
            avatarUrl = avatarUrl ?: profile.avatarUrl
        )
    }
}

private class FakeProfileSyncCoordinator(
    private val localRepo: FakeUserPreferencesRepository
) : UserPreferencesSyncCoordinator {

    var signOutCalled: Boolean = false
    var shouldThrowOnSignOut: Boolean = false
    var forceSyncCalled: Boolean = false
    var shouldThrowOnForceSync: Boolean = false

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

    override suspend fun forceSync() {
        if (shouldThrowOnForceSync) throw IOException("Simulated force sync failure")
        forceSyncCalled = true
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

    override suspend fun updateNotificationPreferences(
        routineReminders: Boolean,
        taskReminders: Boolean,
        morningBriefing: Boolean,
        quietHours: Boolean
    ) {
        if (shouldThrowOnWrite) throw IOException("Simulated disk write failure")
        _preferencesFlow.value = _preferencesFlow.value.copy(
            routineRemindersEnabled = routineReminders,
            taskRemindersEnabled = taskReminders,
            morningBriefingEnabled = morningBriefing,
            quietHoursEnabled = quietHours
        )
    }
}
