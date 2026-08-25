package com.smartreminder.domain.schedule

import com.smartreminder.domain.model.schedule.OverrideType
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ids.RoutineId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RoutineOccurrenceResolverTest {

    private val routineId = RoutineId("university_day")
    private val mwfRoutine = Routine(
        id = routineId,
        name = "University Day",
        recurrence = RecurrenceRule.Weekly(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        ),
        enabled = true
    )

    // 2026-08-24 is Monday, 2026-08-25 is Tuesday, 2026-08-26 is Wednesday
    private val monday = LocalDate.of(2026, 8, 24)
    private val tuesday = LocalDate.of(2026, 8, 25)
    private val wednesday = LocalDate.of(2026, 8, 26)

    @Test
    fun `given weekly MWF routine, when resolving Monday, then returns true`() {
        val shouldRun = RoutineOccurrenceResolver.shouldRun(mwfRoutine, monday)
        assertTrue(shouldRun)
    }

    @Test
    fun `given weekly MWF routine, when resolving Tuesday, then returns false`() {
        val shouldRun = RoutineOccurrenceResolver.shouldRun(mwfRoutine, tuesday)
        assertFalse(shouldRun)
    }

    @Test
    fun `given weekly MWF routine on Wednesday, when override is SKIP, then returns false`() {
        val skipOverride = RoutineOverride(
            routineId = routineId,
            date = wednesday,
            type = OverrideType.SKIP
        )

        val shouldRun = RoutineOccurrenceResolver.shouldRun(mwfRoutine, wednesday, skipOverride)
        assertFalse(shouldRun)
    }

    @Test
    fun `given weekly MWF routine on Tuesday, when override is FORCE_RUN, then returns true`() {
        val forceRunOverride = RoutineOverride(
            routineId = routineId,
            date = tuesday,
            type = OverrideType.FORCE_RUN
        )

        val shouldRun = RoutineOccurrenceResolver.shouldRun(mwfRoutine, tuesday, forceRunOverride)
        assertTrue(shouldRun)
    }

    @Test
    fun `given disabled weekly routine, when resolving matching Monday, then returns false`() {
        val disabledRoutine = mwfRoutine.copy(enabled = false)

        val shouldRun = RoutineOccurrenceResolver.shouldRun(disabledRoutine, monday)
        assertFalse(shouldRun)
    }

    @Test
    fun `given disabled weekly routine, when override is FORCE_RUN, then returns false (master switch rule)`() {
        val disabledRoutine = mwfRoutine.copy(enabled = false)
        val forceRunOverride = RoutineOverride(
            routineId = routineId,
            date = tuesday,
            type = OverrideType.FORCE_RUN
        )

        val shouldRun = RoutineOccurrenceResolver.shouldRun(disabledRoutine, tuesday, forceRunOverride)
        assertFalse(shouldRun)
    }

    @Test
    fun `given override belongs to another routine, when resolving, then ignores override and uses recurrence`() {
        val otherRoutineOverride = RoutineOverride(
            routineId = RoutineId("gym_day"),
            date = wednesday,
            type = OverrideType.SKIP
        )

        // Wednesday should still run because the SKIP override is for another routine
        val shouldRun = RoutineOccurrenceResolver.shouldRun(mwfRoutine, wednesday, otherRoutineOverride)
        assertTrue(shouldRun)
    }

    @Test
    fun `given override date differs from requested date, when resolving, then ignores override and uses recurrence`() {
        val wrongDateOverride = RoutineOverride(
            routineId = routineId,
            date = LocalDate.of(2026, 8, 30),
            type = OverrideType.SKIP
        )

        // Wednesday should still run because the override date is 2026-08-30
        val shouldRun = RoutineOccurrenceResolver.shouldRun(mwfRoutine, wednesday, wrongDateOverride)
        assertTrue(shouldRun)
    }
}
