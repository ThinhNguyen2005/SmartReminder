package com.smartreminder.domain.today

import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.task.Task
import com.smartreminder.domain.model.task.TaskPriority
import com.smartreminder.domain.model.today.TodayTimelineItem.FreeSlotBlock
import com.smartreminder.domain.model.today.TodayTimelineItem.RoutineEvent
import com.smartreminder.domain.model.today.TodayTimelineItem.TaskEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class TodayTimelineResolverTest {

    private val monday = LocalDate.of(2026, 8, 24)
    private val currentTime = LocalTime.of(10, 12)
    private val wakeTime = LocalTime.of(7, 0)
    private val sleepTime = LocalTime.of(23, 0)

    private val studyRoutine = Routine(
        id = RoutineId("study_routine"),
        name = "Study",
        recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        enabled = true
    )

    private val studyItem = RoutineItem(
        id = RoutineItemId("study_item_1"),
        routineId = studyRoutine.id,
        title = "Study Kotlin",
        scheduledTime = LocalTime.of(11, 30),
        durationMinutes = 60
    )

    @Test
    fun `given scheduled routine and task, when resolving today timeline, then items are ordered and progress is computed`() {
        val taskPast = Task(
            id = "task_past",
            title = "Class",
            scheduledDate = monday,
            scheduledTime = LocalTime.of(9, 0),
            durationMinutes = 60,
            isCompleted = true
        )

        val taskFuture = Task(
            id = "task_future",
            title = "Project Meeting",
            scheduledDate = monday,
            scheduledTime = LocalTime.of(14, 0),
            durationMinutes = 60,
            priority = TaskPriority.HIGH
        )

        val result = TodayTimelineResolver.resolve(
            currentDate = monday,
            currentTime = currentTime, // 10:12
            wakeUpTime = wakeTime,
            sleepTime = sleepTime,
            routinesWithDetails = listOf(
                RoutineDetails(
                    routine = studyRoutine,
                    items = listOf(studyItem)
                )
            ),
            tasks = listOf(taskPast, taskFuture)
        )

        // Progress: total = 1 routine + 2 tasks = 3; completed = 1 (taskPast is completed)
        assertEquals(3, result.progress.totalCount)
        assertEquals(1, result.progress.completedCount)

        // Items should contain events + free slots
        val events = result.items.filter { it is RoutineEvent || it is TaskEvent }
        assertEquals(3, events.size)

        // Class at 09:00 should be marked as past because end time (10:00) < currentTime (10:12)
        val classEvent = events.first { it.startTime == LocalTime.of(9, 0) } as TaskEvent
        assertTrue(classEvent.isPast)

        // Study Kotlin at 11:30 should not be past
        val studyEvent = events.first { it.startTime == LocalTime.of(11, 30) } as RoutineEvent
        assertTrue(!studyEvent.isPast)

        // Timeline items should be chronologically sorted
        for (i in 0 until result.items.size - 1) {
            assertTrue(!result.items[i + 1].startTime.isBefore(result.items[i].startTime))
        }

        // AI Suggestion should be present
        assertNotNull(result.aiSuggestion)
    }
}
