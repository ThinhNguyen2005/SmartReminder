package com.smartreminder.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TimeCalculatorTest {

    @Test
    fun `given normal daytime schedule, when calculating awake minutes, then returns correct duration`() {
        // Given: 07:00 wake, 23:30 sleep
        val wake = LocalTime.of(7, 0)
        val sleep = LocalTime.of(23, 30)

        // When
        val minutes = TimeCalculator.calculateAwakeMinutes(wake, sleep)

        // Then: 16h 30m = 990 minutes
        assertEquals(990, minutes)
    }
    @Test
    fun `given 07_00 wake and 23_30 sleep, when calculating rhythm breakdown, then returns 16h30 planning and 7h30 quiet`() {
        // Given
        val wake = LocalTime.of(7, 0)
        val sleep = LocalTime.of(23, 30)

        // When
        val breakdown = TimeCalculator.calculateDailyRhythmBreakdown(wake, sleep)

        // Then
        assertEquals(16, breakdown.planningDuration.hours)
        assertEquals(30, breakdown.planningDuration.minutes)
        assertEquals(7, breakdown.quietDuration.hours)
        assertEquals(30, breakdown.quietDuration.minutes)
    }

    @Test
    fun `given 10_00 wake and 02_00 sleep, when calculating rhythm breakdown, then handles cross midnight correctly`() {
        // Given
        val wake = LocalTime.of(10, 0)
        val sleep = LocalTime.of(2, 0)

        // When
        val breakdown = TimeCalculator.calculateDailyRhythmBreakdown(wake, sleep)

        // Then
        assertEquals(16, breakdown.planningDuration.hours)
        assertEquals(0, breakdown.planningDuration.minutes)
        assertEquals(8, breakdown.quietDuration.hours)
        assertEquals(0, breakdown.quietDuration.minutes)
    }
    @Test
    fun `given cross-midnight schedule, when calculating awake minutes, then returns correct duration`() {
        // Given: 10:00 wake, 02:00 sleep (next morning)
        val wake = LocalTime.of(10, 0)
        val sleep = LocalTime.of(2, 0)

        // When
        val minutes = TimeCalculator.calculateAwakeMinutes(wake, sleep)

        // Then: 14h (until midnight) + 2h (after midnight) = 16h = 960 minutes
        assertEquals(960, minutes)
    }

    @Test
    fun `given late night cross-midnight schedule, when calculating awake minutes, then returns correct short duration`() {
        // Given: 23:30 wake/activity start, 00:30 sleep
        val wake = LocalTime.of(23, 30)
        val sleep = LocalTime.of(0, 30)

        // When
        val minutes = TimeCalculator.calculateAwakeMinutes(wake, sleep)

        // Then: 1 hour = 60 minutes
        assertEquals(60, minutes)
    }

    @Test
    fun `given midnight start schedule, when calculating awake minutes, then returns correct duration`() {
        // Given: 00:00 wake, 07:00 sleep
        val wake = LocalTime.of(0, 0)
        val sleep = LocalTime.of(7, 0)

        // When
        val minutes = TimeCalculator.calculateAwakeMinutes(wake, sleep)

        // Then: 7 hours = 420 minutes
        assertEquals(420, minutes)
    }

    @Test
    fun `given identical wake and sleep times, when calculating awake minutes, then returns zero minutes`() {
        // Given: 08:00 wake, 08:00 sleep
        val wake = LocalTime.of(8, 0)
        val sleep = LocalTime.of(8, 0)

        // When
        val minutes = TimeCalculator.calculateAwakeMinutes(wake, sleep)

        // Then: Business rule specifies 0 minutes
        assertEquals(0, minutes)
    }

    @Test
    fun `given wake and sleep times, when calculating awake duration model, then returns structured hours and minutes`() {
        // Given: 07:15 wake, 23:45 sleep
        val wake = LocalTime.of(7, 15)
        val sleep = LocalTime.of(23, 45)

        // When
        val duration = TimeCalculator.calculateAwakeDuration(wake, sleep)

        // Then: 16 hours, 30 minutes, 990 total minutes
        assertEquals(16, duration.hours)
        assertEquals(30, duration.minutes)
        assertEquals(990, duration.totalMinutes)
    }
}
