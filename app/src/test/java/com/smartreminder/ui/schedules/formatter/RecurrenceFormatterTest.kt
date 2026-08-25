package com.smartreminder.ui.schedules.formatter

import com.smartreminder.ui.schedules.RecurrenceUiModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class RecurrenceFormatterTest {

    private val enLabels = RecurrenceLabels(
        everyDay = "Every day",
        dayAbbreviations = mapOf(
            DayOfWeek.MONDAY to "Mon",
            DayOfWeek.TUESDAY to "Tue",
            DayOfWeek.WEDNESDAY to "Wed",
            DayOfWeek.THURSDAY to "Thu",
            DayOfWeek.FRIDAY to "Fri",
            DayOfWeek.SATURDAY to "Sat",
            DayOfWeek.SUNDAY to "Sun"
        )
    )

    private val viLabels = RecurrenceLabels(
        everyDay = "Mỗi ngày",
        dayAbbreviations = mapOf(
            DayOfWeek.MONDAY to "T2",
            DayOfWeek.TUESDAY to "T3",
            DayOfWeek.WEDNESDAY to "T4",
            DayOfWeek.THURSDAY to "T5",
            DayOfWeek.FRIDAY to "T6",
            DayOfWeek.SATURDAY to "T7",
            DayOfWeek.SUNDAY to "CN"
        )
    )

    @Test
    fun `given MWF recurrence, when formatted in English, then returns Mon Wed Fri`() {
        val recurrence = RecurrenceUiModel(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )
        val formatted = RecurrenceFormatter.format(recurrence, enLabels)
        assertEquals("Mon · Wed · Fri", formatted)
    }

    @Test
    fun `given MWF recurrence, when formatted in Vietnamese, then returns T2 T4 T6`() {
        val recurrence = RecurrenceUiModel(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )
        val formatted = RecurrenceFormatter.format(recurrence, viLabels)
        assertEquals("T2 · T4 · T6", formatted)
    }

    @Test
    fun `given all 7 days recurrence, when formatted, then returns Every day`() {
        val recurrence = RecurrenceUiModel(
            days = DayOfWeek.entries.toSet()
        )
        val formattedEn = RecurrenceFormatter.format(recurrence, enLabels)
        val formattedVi = RecurrenceFormatter.format(recurrence, viLabels)

        assertEquals("Every day", formattedEn)
        assertEquals("Mỗi ngày", formattedVi)
    }

    @Test
    fun `given unordered days set, when formatted, then orders days by ISO day of week Monday to Sunday`() {
        val recurrence = RecurrenceUiModel(
            days = setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY, DayOfWeek.MONDAY)
        )
        val formatted = RecurrenceFormatter.format(recurrence, enLabels)
        assertEquals("Mon · Tue · Sun", formatted)
    }

    @Test
    fun `given single day recurrence, when formatted, then returns single day abbreviation`() {
        val recurrence = RecurrenceUiModel(
            days = setOf(DayOfWeek.SATURDAY)
        )
        val formatted = RecurrenceFormatter.format(recurrence, enLabels)
        assertEquals("Sat", formatted)
    }
}
