package com.smartreminder.data.remote.preferences

import com.smartreminder.domain.model.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.UserGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class UserPreferencesRemoteMapperTest {

    @Test
    fun `given LocalTime, when mapping toDto, then converts to minute-of-day integer`() {
        val snapshot = OnboardingPreferencesSnapshot(
            wakeUpTime = LocalTime.of(8, 30),
            sleepTime = LocalTime.of(0, 15),
            goals = emptySet(),
            onboardingCompleted = true
        )

        val dto = UserPreferencesRemoteMapper.toDto("user-123", snapshot)

        assertEquals(510, dto.wakeUpMinute)
        assertEquals(15, dto.sleepMinute)
    }

    @Test
    fun `given valid minutes, when mapping toDomain, then converts to LocalTime`() {
        val dto = UserPreferencesRemoteDto(
            userId = "user-123",
            onboardingCompleted = true,
            wakeUpMinute = 390, // 06:30
            sleepMinute = 1350, // 22:30
            selectedGoals = emptyList()
        )

        val domain = UserPreferencesRemoteMapper.toDomain(dto)

        assertEquals(LocalTime.of(6, 30), domain.wakeUpTime)
        assertEquals(LocalTime.of(22, 30), domain.sleepTime)
    }

    @Test
    fun `given goals set, when mapping toDto, then uses stable storageKeys`() {
        val snapshot = OnboardingPreferencesSnapshot(
            wakeUpTime = LocalTime.of(7, 0),
            sleepTime = LocalTime.of(23, 0),
            goals = setOf(UserGoal.TASKS, UserGoal.PLANNING, UserGoal.STUDY),
            onboardingCompleted = true
        )

        val dto = UserPreferencesRemoteMapper.toDto("user-123", snapshot)

        assertEquals(listOf("tasks", "planning", "study"), dto.selectedGoals)
    }

    @Test
    fun `given string keys, when mapping toDomain, then maps to UserGoal enum set`() {
        val dto = UserPreferencesRemoteDto(
            userId = "user-123",
            onboardingCompleted = true,
            wakeUpMinute = 420,
            sleepMinute = 1380,
            selectedGoals = listOf("routines", "teamwork")
        )

        val domain = UserPreferencesRemoteMapper.toDomain(dto)

        assertEquals(setOf(UserGoal.ROUTINES, UserGoal.TEAMWORK), domain.goals)
    }

    @Test
    fun `given unknown goal keys in remote DTO, when mapping toDomain, then ignores unknown keys safely`() {
        val dto = UserPreferencesRemoteDto(
            userId = "user-123",
            onboardingCompleted = true,
            wakeUpMinute = 420,
            sleepMinute = 1380,
            selectedGoals = listOf("tasks", "unknown_future_goal_key", "planning")
        )

        val domain = UserPreferencesRemoteMapper.toDomain(dto)

        assertEquals(setOf(UserGoal.TASKS, UserGoal.PLANNING), domain.goals)
    }

    @Test
    fun `given invalid wakeUpMinute beyond 1439, when mapping toDomain, then throws IllegalArgumentException`() {
        val dto = UserPreferencesRemoteDto(
            userId = "user-123",
            onboardingCompleted = true,
            wakeUpMinute = 1440,
            sleepMinute = 1380,
            selectedGoals = emptyList()
        )

        assertThrows(IllegalArgumentException::class.java) {
            UserPreferencesRemoteMapper.toDomain(dto)
        }
    }

    @Test
    fun `given negative sleepMinute, when mapping toDomain, then throws IllegalArgumentException`() {
        val dto = UserPreferencesRemoteDto(
            userId = "user-123",
            onboardingCompleted = true,
            wakeUpMinute = 420,
            sleepMinute = -1,
            selectedGoals = emptyList()
        )

        assertThrows(IllegalArgumentException::class.java) {
            UserPreferencesRemoteMapper.toDomain(dto)
        }
    }

    @Test
    fun `given domain snapshot, when mapping toDto and back toDomain, then round-trip preserves all fields`() {
        val original = OnboardingPreferencesSnapshot(
            wakeUpTime = LocalTime.of(6, 45),
            sleepTime = LocalTime.of(23, 15),
            goals = setOf(UserGoal.STUDY, UserGoal.PLANNING, UserGoal.TASKS),
            onboardingCompleted = true
        )

        val dto = UserPreferencesRemoteMapper.toDto("user-abc", original)
        val restored = UserPreferencesRemoteMapper.toDomain(dto)

        assertEquals(original.wakeUpTime, restored.wakeUpTime)
        assertEquals(original.sleepTime, restored.sleepTime)
        assertEquals(original.goals, restored.goals)
        assertEquals(original.onboardingCompleted, restored.onboardingCompleted)
    }
}
