package com.smartreminder.data.sync

import com.smartreminder.domain.model.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.domain.model.UserGoal
import com.smartreminder.domain.model.UserPreferences
import com.smartreminder.domain.repository.UserPreferencesCloudRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.RestorePreferencesResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalTime

class DefaultUserPreferencesSyncCoordinatorTest {

    private lateinit var fakeLocalRepo: FakeLocalPreferencesRepository
    private lateinit var fakeCloudRepo: FakeCloudPreferencesRepository
    private var currentUserId: String? = null
    private var signOutCalled: Boolean = false
    private var shouldThrowOnSignOut: Boolean = false
    private val callOrderList = mutableListOf<String>()

    private lateinit var coordinator: DefaultUserPreferencesSyncCoordinator

    @Before
    fun setup() {
        fakeLocalRepo = FakeLocalPreferencesRepository(callOrderList)
        fakeCloudRepo = FakeCloudPreferencesRepository(callOrderList)
        currentUserId = null
        signOutCalled = false
        shouldThrowOnSignOut = false
        callOrderList.clear()

        coordinator = DefaultUserPreferencesSyncCoordinator(
            localRepository = fakeLocalRepo,
            cloudRepository = fakeCloudRepo,
            getCurrentUserId = { currentUserId },
            signOutAuth = {
                if (shouldThrowOnSignOut) throw IOException("Auth sign out failure")
                signOutCalled = true
                callOrderList.add("auth.signOut")
            }
        )
    }

    @Test
    fun `1 Given remote completed prefs, when restoreForUser, then local replaced and result is RestoredCompleted`() = runTest {
        // Given
        val remoteSnapshot = OnboardingPreferencesSnapshot(
            wakeUpTime = LocalTime.of(6, 30),
            sleepTime = LocalTime.of(22, 30),
            goals = setOf(UserGoal.STUDY, UserGoal.PLANNING),
            onboardingCompleted = true
        )
        fakeCloudRepo.snapshotToReturn = remoteSnapshot

        // When
        val result = coordinator.restoreForUser("user-123")

        // Then
        assertEquals(RestorePreferencesResult.RestoredCompleted, result)
        assertEquals(LocalTime.of(6, 30), fakeLocalRepo.currentPrefs.wakeUpTime)
        assertEquals(LocalTime.of(22, 30), fakeLocalRepo.currentPrefs.sleepTime)
        assertEquals(setOf(UserGoal.STUDY, UserGoal.PLANNING), fakeLocalRepo.currentPrefs.goals)
        assertTrue(fakeLocalRepo.currentPrefs.onboardingCompleted)
    }

    @Test
    fun `2 Given remote row missing, when restore, then onboarding local cleared, theme preserved and result is NeedsOnboarding`() = runTest {
        // Given: Local has existing custom preferences and DARK theme
        fakeLocalRepo.setPreferences(
            UserPreferences(
                wakeUpTime = LocalTime.of(8, 0),
                sleepTime = LocalTime.of(0, 0),
                goals = setOf(UserGoal.ROUTINES),
                onboardingCompleted = true,
                themeMode = ThemeMode.DARK
            )
        )
        fakeCloudRepo.snapshotToReturn = null

        // When
        val result = coordinator.restoreForUser("new-user-456")

        // Then
        assertEquals(RestorePreferencesResult.NeedsOnboarding, result)
        assertEquals(UserPreferences.DEFAULT_WAKE_TIME, fakeLocalRepo.currentPrefs.wakeUpTime)
        assertEquals(UserPreferences.DEFAULT_SLEEP_TIME, fakeLocalRepo.currentPrefs.sleepTime)
        assertEquals(UserPreferences.DEFAULT_GOALS, fakeLocalRepo.currentPrefs.goals)
        assertFalse(fakeLocalRepo.currentPrefs.onboardingCompleted)
        assertEquals(ThemeMode.DARK, fakeLocalRepo.currentPrefs.themeMode)
    }

    @Test
    fun `3 Given remote onboardingCompleted false, when restore, then remote values hydrated and result is NeedsOnboarding`() = runTest {
        // Given
        val remoteSnapshot = OnboardingPreferencesSnapshot(
            wakeUpTime = LocalTime.of(7, 45),
            sleepTime = LocalTime.of(23, 15),
            goals = setOf(UserGoal.TEAMWORK),
            onboardingCompleted = false
        )
        fakeCloudRepo.snapshotToReturn = remoteSnapshot

        // When
        val result = coordinator.restoreForUser("user-789")

        // Then
        assertEquals(RestorePreferencesResult.NeedsOnboarding, result)
        assertEquals(LocalTime.of(7, 45), fakeLocalRepo.currentPrefs.wakeUpTime)
        assertEquals(LocalTime.of(23, 15), fakeLocalRepo.currentPrefs.sleepTime)
        assertEquals(setOf(UserGoal.TEAMWORK), fakeLocalRepo.currentPrefs.goals)
        assertFalse(fakeLocalRepo.currentPrefs.onboardingCompleted)
    }

    @Test
    fun `4 Given remote fetch throws, when restore, then local data remains unchanged`() = runTest {
        // Given
        val initialPrefs = UserPreferences(
            wakeUpTime = LocalTime.of(9, 0),
            sleepTime = LocalTime.of(1, 0),
            goals = setOf(UserGoal.STUDY),
            onboardingCompleted = true,
            themeMode = ThemeMode.LIGHT
        )
        fakeLocalRepo.setPreferences(initialPrefs)
        fakeCloudRepo.shouldThrowOnGet = true

        // When / Then
        var threw = false
        try {
            coordinator.restoreForUser("user-err")
        } catch (e: IOException) {
            threw = true
        }

        assertTrue("Expected IOException", threw)
        assertEquals(initialPrefs, fakeLocalRepo.currentPrefs)
    }

    @Test
    fun `5 Given unauthenticated guest, when completeOnboarding, then local completes and cloud is not written`() = runTest {
        // Given: Guest (no user id)
        currentUserId = null

        // When
        coordinator.completeOnboarding(
            wakeUpTime = LocalTime.of(7, 0),
            sleepTime = LocalTime.of(23, 0),
            goals = setOf(UserGoal.TASKS)
        )

        // Then
        assertTrue(fakeLocalRepo.currentPrefs.onboardingCompleted)
        assertEquals(0, fakeCloudRepo.upsertedSnapshots.size)
    }

    @Test
    fun `6 Given authenticated user, when completeOnboarding, then cloud upsert occurs BEFORE local completed write`() = runTest {
        // Given
        currentUserId = "user-auth-1"

        // When
        coordinator.completeOnboarding(
            wakeUpTime = LocalTime.of(6, 0),
            sleepTime = LocalTime.of(22, 0),
            goals = setOf(UserGoal.PLANNING)
        )

        // Then: Order must be cloud.upsert -> local.completeOnboarding
        assertEquals(listOf("cloud.upsert", "local.completeOnboarding"), callOrderList)
        assertTrue(fakeLocalRepo.currentPrefs.onboardingCompleted)
        assertEquals(1, fakeCloudRepo.upsertedSnapshots.size)
    }

    @Test
    fun `7 Given cloud onboarding upsert throws, then local onboardingCompleted remains false`() = runTest {
        // Given
        currentUserId = "user-auth-err"
        fakeCloudRepo.shouldThrowOnUpsert = true

        // When / Then
        var threw = false
        try {
            coordinator.completeOnboarding(
                wakeUpTime = LocalTime.of(6, 0),
                sleepTime = LocalTime.of(22, 0),
                goals = setOf(UserGoal.PLANNING)
            )
        } catch (e: IOException) {
            threw = true
        }

        assertTrue("Expected IOException", threw)
        assertFalse(fakeLocalRepo.currentPrefs.onboardingCompleted)
    }

    @Test
    fun `8 Given completed authenticated local preferences, when signOutAndClearLocal, then cloud flush, auth signOut, and local cleared in exact order`() = runTest {
        // Given
        currentUserId = "user-signout-1"
        fakeLocalRepo.setPreferences(
            UserPreferences(
                wakeUpTime = LocalTime.of(6, 15),
                sleepTime = LocalTime.of(22, 15),
                goals = setOf(UserGoal.STUDY),
                onboardingCompleted = true,
                themeMode = ThemeMode.DARK
            )
        )

        // When
        coordinator.signOutAndClearLocal()

        // Then
        assertEquals(listOf("cloud.upsert", "auth.signOut", "local.clearOnboardingPreferences"), callOrderList)
        assertTrue(signOutCalled)
        assertFalse(fakeLocalRepo.currentPrefs.onboardingCompleted)
        assertEquals(ThemeMode.DARK, fakeLocalRepo.currentPrefs.themeMode)
    }

    @Test
    fun `9 Given final cloud flush fails, then signOut NOT called and local clear NOT called`() = runTest {
        // Given
        currentUserId = "user-signout-fail"
        fakeLocalRepo.setPreferences(
            UserPreferences(onboardingCompleted = true)
        )
        fakeCloudRepo.shouldThrowOnUpsert = true

        // When / Then
        var threw = false
        try {
            coordinator.signOutAndClearLocal()
        } catch (e: IOException) {
            threw = true
        }

        assertTrue("Expected IOException", threw)
        assertFalse(signOutCalled)
        assertEquals(emptyList<String>(), callOrderList.filter { it == "auth.signOut" || it == "local.clearOnboardingPreferences" })
    }

    @Test
    fun `10 Given signOut fails, then local clear NOT called`() = runTest {
        // Given
        currentUserId = "user-signout-auth-fail"
        fakeLocalRepo.setPreferences(
            UserPreferences(onboardingCompleted = true)
        )
        shouldThrowOnSignOut = true

        // When / Then
        var threw = false
        try {
            coordinator.signOutAndClearLocal()
        } catch (e: IOException) {
            threw = true
        }

        assertTrue("Expected IOException", threw)
        assertTrue(fakeLocalRepo.currentPrefs.onboardingCompleted)
    }

    @Test
    fun `11 Clear local preserves theme`() = runTest {
        // Given
        fakeLocalRepo.setPreferences(
            UserPreferences(
                wakeUpTime = LocalTime.of(8, 0),
                sleepTime = LocalTime.of(0, 0),
                goals = setOf(UserGoal.TASKS),
                onboardingCompleted = true,
                themeMode = ThemeMode.DARK
            )
        )

        // When
        coordinator.signOutAndClearLocal()

        // Then
        assertEquals(ThemeMode.DARK, fakeLocalRepo.currentPrefs.themeMode)
    }
}

private class FakeLocalPreferencesRepository(
    private val callOrderList: MutableList<String>
) : UserPreferencesRepository {

    private val _flow = MutableStateFlow(UserPreferences())
    override val preferences: Flow<UserPreferences> = _flow.asStateFlow()

    val currentPrefs: UserPreferences get() = _flow.value

    fun setPreferences(prefs: UserPreferences) {
        _flow.value = prefs
    }

    override suspend fun completeOnboarding(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        goals: Set<UserGoal>
    ) {
        callOrderList.add("local.completeOnboarding")
        _flow.value = _flow.value.copy(
            wakeUpTime = wakeUpTime,
            sleepTime = sleepTime,
            goals = goals,
            onboardingCompleted = true
        )
    }

    override suspend fun updateRhythm(wakeUpTime: LocalTime, sleepTime: LocalTime) {
        _flow.value = _flow.value.copy(wakeUpTime = wakeUpTime, sleepTime = sleepTime)
    }

    override suspend fun updateGoals(goals: Set<UserGoal>) {
        _flow.value = _flow.value.copy(goals = goals)
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        _flow.value = _flow.value.copy(themeMode = mode)
    }

    override suspend fun resetOnboarding() {
        _flow.value = _flow.value.copy(onboardingCompleted = false)
    }

    override suspend fun replaceOnboardingPreferences(snapshot: OnboardingPreferencesSnapshot) {
        callOrderList.add("local.replaceOnboardingPreferences")
        _flow.value = _flow.value.copy(
            wakeUpTime = snapshot.wakeUpTime,
            sleepTime = snapshot.sleepTime,
            goals = snapshot.goals,
            onboardingCompleted = snapshot.onboardingCompleted
        )
    }

    override suspend fun clearOnboardingPreferences() {
        callOrderList.add("local.clearOnboardingPreferences")
        val defaults = UserPreferences()
        _flow.value = _flow.value.copy(
            wakeUpTime = defaults.wakeUpTime,
            sleepTime = defaults.sleepTime,
            goals = defaults.goals,
            onboardingCompleted = false
        )
    }
}

private class FakeCloudPreferencesRepository(
    private val callOrderList: MutableList<String>
) : UserPreferencesCloudRepository {

    var snapshotToReturn: OnboardingPreferencesSnapshot? = null
    var shouldThrowOnGet: Boolean = false
    var shouldThrowOnUpsert: Boolean = false
    val upsertedSnapshots = mutableListOf<Pair<String, OnboardingPreferencesSnapshot>>()

    override suspend fun getForUser(userId: String): OnboardingPreferencesSnapshot? {
        callOrderList.add("cloud.getForUser")
        if (shouldThrowOnGet) throw IOException("Remote network error on get")
        return snapshotToReturn
    }

    override suspend fun upsertForUser(userId: String, snapshot: OnboardingPreferencesSnapshot) {
        callOrderList.add("cloud.upsert")
        if (shouldThrowOnUpsert) throw IOException("Remote network error on upsert")
        upsertedSnapshots.add(userId to snapshot)
    }
}
