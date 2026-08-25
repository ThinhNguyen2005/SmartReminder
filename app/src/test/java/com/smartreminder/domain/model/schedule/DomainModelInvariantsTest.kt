package com.smartreminder.domain.model.schedule

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class DomainModelInvariantsTest {

    @Test(expected = IllegalArgumentException::class)
    fun `given blank ScheduleGroupId, when instantiated, then throws IllegalArgumentException`() {
        ScheduleGroupId("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank RoutineId, when instantiated, then throws IllegalArgumentException`() {
        RoutineId("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank RoutineItemId, when instantiated, then throws IllegalArgumentException`() {
        RoutineItemId(" \t ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given empty days set in Weekly recurrence, when instantiated, then throws IllegalArgumentException`() {
        RecurrenceRule.Weekly(emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank name in ScheduleGroup, when instantiated, then throws IllegalArgumentException`() {
        ScheduleGroup(
            id = ScheduleGroupId("group_1"),
            name = "   "
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given negative sortOrder in ScheduleGroup, when instantiated, then throws IllegalArgumentException`() {
        ScheduleGroup(
            id = ScheduleGroupId("group_1"),
            name = "Study",
            sortOrder = -1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank name in Routine, when instantiated, then throws IllegalArgumentException`() {
        Routine(
            id = RoutineId("routine_1"),
            name = "",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given negative sortOrder in Routine, when instantiated, then throws IllegalArgumentException`() {
        Routine(
            id = RoutineId("routine_1"),
            name = "Morning",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)),
            sortOrder = -5
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank title in RoutineItem, when instantiated, then throws IllegalArgumentException`() {
        RoutineItem(
            id = RoutineItemId("item_1"),
            routineId = RoutineId("routine_1"),
            title = "   ",
            scheduledTime = LocalTime.of(7, 0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given zero durationMinutes in RoutineItem, when instantiated, then throws IllegalArgumentException`() {
        RoutineItem(
            id = RoutineItemId("item_1"),
            routineId = RoutineId("routine_1"),
            title = "Prepare",
            scheduledTime = LocalTime.of(7, 0),
            durationMinutes = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given negative sortOrder in RoutineItem, when instantiated, then throws IllegalArgumentException`() {
        RoutineItem(
            id = RoutineItemId("item_1"),
            routineId = RoutineId("routine_1"),
            title = "Prepare",
            scheduledTime = LocalTime.of(7, 0),
            sortOrder = -1
        )
    }

    @Test
    fun `given valid parameters, when models instantiated, then properties match`() {
        val group = ScheduleGroup(
            id = ScheduleGroupId("group_1"),
            name = "Study",
            iconKey = "school",
            colorKey = "indigo"
        )
        val item = RoutineItem(
            id = RoutineItemId("item_1"),
            routineId = RoutineId("routine_1"),
            title = "Class",
            scheduledTime = LocalTime.of(8, 0),
            durationMinutes = 90
        )
        val routine = Routine(
            id = RoutineId("routine_1"),
            groupId = group.id,
            name = "University Day",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY))
        )
        val details = RoutineDetails(routine, listOf(item))

        assertEquals("Study", group.name)
        assertEquals("University Day", details.routine.name)
        assertEquals(1, details.items.size)
        assertEquals("Class", details.items.first().title)
    }
}
