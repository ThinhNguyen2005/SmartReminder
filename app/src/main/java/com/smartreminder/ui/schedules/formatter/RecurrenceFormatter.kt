package com.smartreminder.ui.schedules.formatter

import com.smartreminder.ui.schedules.RecurrenceUiModel
import java.time.DayOfWeek

data class RecurrenceLabels(
    val everyDay: String,
    val dayAbbreviations: Map<DayOfWeek, String>
)

object RecurrenceFormatter {

    fun format(recurrence: RecurrenceUiModel, labels: RecurrenceLabels): String {
        if (recurrence.isEveryDay) {
            return labels.everyDay
        }

        return recurrence.days
            .sortedBy { it.value } // Monday (1) .. Sunday (7)
            .mapNotNull { labels.dayAbbreviations[it] }
            .joinToString(" · ")
    }
}
