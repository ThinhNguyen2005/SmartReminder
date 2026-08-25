package com.smartreminder.data.preferences

import com.smartreminder.domain.model.UserGoal
import com.smartreminder.domain.model.UserPreferences
import java.time.LocalTime

/**
 * Safe bidirectional mappers between domain types and DataStore primitives.
 * Handles invalid data gracefully — no exceptions leak to Flow.
 */
internal object UserPreferencesMapper {

    private const val MIN_MINUTES = 0
    private const val MAX_MINUTES = 1439 // 23:59

    fun LocalTime.toMinutesOfDay(): Int = hour * 60 + minute

    /**
     * Converts minutes-of-day to [LocalTime].
     * Falls back to [fallback] for out-of-range values instead of throwing DateTimeException.
     */
    fun Int.toLocalTimeSafe(fallback: LocalTime): LocalTime =
        if (this in MIN_MINUTES..MAX_MINUTES) {
            LocalTime.of(this / 60, this % 60)
        } else {
            fallback
        }

    fun Set<UserGoal>.toStorageKeys(): Set<String> =
        mapTo(mutableSetOf()) { it.storageKey }

    /** Unknown keys are silently dropped — forward-compatible with future goal removals. */
    fun Set<String>.toUserGoals(): Set<UserGoal> =
        mapNotNull { UserGoal.fromStorageKey(it) }.toSet()
}
