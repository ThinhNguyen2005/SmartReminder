package com.smartreminder.domain.model

import java.time.LocalTime

/**
 * Immutable domain model representing user preferences persisted across sessions.
 * Domain layer works with [LocalTime] and [UserGoal]; data layer handles serialization.
 */
data class UserPreferences(
    val wakeUpTime: LocalTime = DEFAULT_WAKE_TIME,
    val sleepTime: LocalTime = DEFAULT_SLEEP_TIME,
    val goals: Set<UserGoal> = DEFAULT_GOALS,
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    companion object {
        val DEFAULT_WAKE_TIME: LocalTime = LocalTime.of(7, 0)
        val DEFAULT_SLEEP_TIME: LocalTime = LocalTime.of(23, 30)
        val DEFAULT_GOALS: Set<UserGoal> = setOf(UserGoal.TASKS, UserGoal.PLANNING)
    }
}
