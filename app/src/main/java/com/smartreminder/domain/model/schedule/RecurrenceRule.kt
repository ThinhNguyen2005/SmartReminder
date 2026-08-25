package com.smartreminder.domain.model.schedule

import java.time.DayOfWeek

/**
 * Domain value object defining recurring scheduling policies.
 * V1 focuses strictly on weekly recurrence.
 */
sealed interface RecurrenceRule {

    data class Weekly(
        val days: Set<DayOfWeek>
    ) : RecurrenceRule {
        init {
            require(days.isNotEmpty()) { "Weekly recurrence must contain at least one day" }
        }
    }
}
