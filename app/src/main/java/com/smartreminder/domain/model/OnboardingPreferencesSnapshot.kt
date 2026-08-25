package com.smartreminder.domain.model

import java.time.LocalTime

/**
 * Immutable snapshot of onboarding/rhythm configuration for cloud sync.
 * Explicitly excludes device-local preferences such as [ThemeMode].
 */
data class OnboardingPreferencesSnapshot(
    val wakeUpTime: LocalTime,
    val sleepTime: LocalTime,
    val goals: Set<UserGoal>,
    val onboardingCompleted: Boolean
)
