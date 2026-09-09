package com.smartreminder.domain.today

import java.time.LocalTime

/**
 * Value object representing a continuous span of time on a single day.
 */
data class TimeInterval(
    val start: LocalTime,
    val end: LocalTime
) {
    init {
        require(!end.isBefore(start)) { "End time ($end) cannot be before start time ($start)" }
    }

    val durationMinutes: Int
        get() = (end.toSecondOfDay() - start.toSecondOfDay()) / 60
}
