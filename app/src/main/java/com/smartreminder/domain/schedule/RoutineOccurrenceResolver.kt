package com.smartreminder.domain.schedule

import com.smartreminder.domain.model.schedule.OverrideType
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineOverride
import java.time.LocalDate

/**
 * Pure domain service that determines whether a [Routine] should run on a specific [LocalDate].
 *
 * Precedence Rule:
 * 1. Master Switch: If [Routine.enabled] is false -> returns false immediately.
 * 2. Applicable Override: If an override matching [Routine.id] and [date] exists:
 *    - FORCE_RUN -> returns true
 *    - SKIP -> returns false
 * 3. Base Recurrence: Evaluates [Routine.recurrence] against [date].
 */
object RoutineOccurrenceResolver {

    fun shouldRun(
        routine: Routine,
        date: LocalDate,
        override: RoutineOverride? = null
    ): Boolean {
        if (!routine.enabled) return false

        val applicableOverride = override?.takeIf {
            it.routineId == routine.id && it.date == date
        }

        return when (applicableOverride?.type) {
            OverrideType.FORCE_RUN -> true
            OverrideType.SKIP -> false
            null -> when (val recurrence = routine.recurrence) {
                is RecurrenceRule.Weekly -> recurrence.days.contains(date.dayOfWeek)
            }
        }
    }
}
