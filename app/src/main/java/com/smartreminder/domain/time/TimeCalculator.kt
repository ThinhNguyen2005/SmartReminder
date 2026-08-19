package com.smartreminder.domain.time

import java.time.LocalTime

/**
 * Domain data model representing awake duration.
 */
data class AwakeDuration(
    val hours: Int,
    val minutes: Int,
    val totalMinutes: Int
)
data class DailyRhythmBreakdown(
    val planningDuration: AwakeDuration,
    val quietDuration: AwakeDuration,
    val planningFraction: Float,
    val quietFraction: Float
)
/**
 * Single source of truth for time calculations across Cue (SmartReminder).
 * Handles cross-midnight transitions, boundaries, and duration calculations.
 */
object TimeCalculator {
    const val MINUTES_PER_DAY = 24 * 60
    /**
     * Tính tổng số phút trong Planning Window (Awake Window)
     */
    fun calculateAwakeMinutes(wakeTime: LocalTime, sleepTime: LocalTime): Int {
        val wakeMinutes = wakeTime.hour * 60 + wakeTime.minute
        val sleepMinutes = sleepTime.hour * 60 + sleepTime.minute
        return when {
            sleepMinutes > wakeMinutes -> sleepMinutes - wakeMinutes
            sleepMinutes < wakeMinutes -> (MINUTES_PER_DAY - wakeMinutes) + sleepMinutes
            else -> 0
        }
    }
    /**
     * Tính tổng số phút trong Quiet Hours
     */
    fun calculateQuietMinutes(wakeTime: LocalTime, sleepTime: LocalTime): Int {
        val planningMinutes = calculateAwakeMinutes(wakeTime, sleepTime)
        return if (planningMinutes == 0) MINUTES_PER_DAY else (MINUTES_PER_DAY - planningMinutes)
    }
    /**
     * Tính toán toàn bộ phân rã 24 giờ (kèm tỷ lệ % phân đoạn cho UI)
     */
    fun calculateDailyRhythmBreakdown(wakeTime: LocalTime, sleepTime: LocalTime): DailyRhythmBreakdown {
        val planningTotal = calculateAwakeMinutes(wakeTime, sleepTime)
        val quietTotal = calculateQuietMinutes(wakeTime, sleepTime)
        val planningHours = planningTotal / 60
        val planningMins = planningTotal % 60
        val quietHours = quietTotal / 60
        val quietMins = quietTotal % 60
        val planningFraction = if (planningTotal == 0) 0f else (planningTotal.toFloat() / MINUTES_PER_DAY).coerceIn(0.1f, 0.9f)
        val quietFraction = (1f - planningFraction).coerceIn(0.1f, 0.9f)
        return DailyRhythmBreakdown(
            planningDuration = AwakeDuration(planningHours, planningMins, planningTotal),
            quietDuration = AwakeDuration(quietHours, quietMins, quietTotal),
            planningFraction = planningFraction,
            quietFraction = quietFraction
        )
    }

    /**
     * Returns a structured [AwakeDuration] containing hours and remaining minutes.
     */
    fun calculateAwakeDuration(wakeTime: LocalTime, sleepTime: LocalTime): AwakeDuration {
        val total = calculateAwakeMinutes(wakeTime, sleepTime)
        return AwakeDuration(
            hours = total / 60,
            minutes = total % 60,
            totalMinutes = total
        )
    }
}
