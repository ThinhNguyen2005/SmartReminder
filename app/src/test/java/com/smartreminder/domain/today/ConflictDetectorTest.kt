package com.smartreminder.domain.today

import com.smartreminder.domain.model.today.TodayTimelineItem.FreeSlotBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ConflictDetectorTest {

    @Test
    fun `given non-overlapping events, when detecting conflicts, then returns empty list`() {
        val event1 = SchedulableEvent(
            id = "1",
            title = "Class",
            startTime = LocalTime.of(9, 0),
            durationMinutes = 60
        )
        val event2 = SchedulableEvent(
            id = "2",
            title = "Meeting",
            startTime = LocalTime.of(10, 0), // Starts exactly when event 1 ends
            durationMinutes = 60
        )

        val conflicts = ConflictDetector.detectConflicts(listOf(event1, event2))
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `given overlapping events, when detecting conflicts, then returns conflict with suggestion from free slot`() {
        // Lecture: 13:00 to 15:00
        val lecture = SchedulableEvent(
            id = "1",
            title = "Lecture",
            startTime = LocalTime.of(13, 0),
            durationMinutes = 120
        )
        // Project Meeting: 14:30 to 15:30 (overlaps with Lecture from 14:30 to 15:00)
        val projectMeeting = SchedulableEvent(
            id = "2",
            title = "Project Meeting",
            startTime = LocalTime.of(14, 30),
            durationMinutes = 60
        )

        // Free slot available at 17:00 for 90 minutes
        val freeSlot = FreeSlotBlock(
            id = "free_1",
            startTime = LocalTime.of(17, 0),
            endTime = LocalTime.of(18, 30),
            durationMinutes = 90
        )

        val conflicts = ConflictDetector.detectConflicts(
            events = listOf(lecture, projectMeeting),
            freeSlots = listOf(freeSlot)
        )

        assertEquals(1, conflicts.size)
        val conflict = conflicts[0]
        assertEquals(LocalTime.of(14, 30), conflict.conflictTime)
        assertEquals("Lecture", conflict.primaryTitle)
        assertEquals("Project Meeting", conflict.conflictingTitle)
        assertEquals(LocalTime.of(17, 0), conflict.suggestedMoveTime)
        assertNotNull(conflict.suggestedItemTitle)
    }
}
