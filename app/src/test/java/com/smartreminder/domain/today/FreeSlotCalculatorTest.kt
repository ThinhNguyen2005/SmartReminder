package com.smartreminder.domain.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class FreeSlotCalculatorTest {

    private val wakeTime = LocalTime.of(7, 0)
    private val sleepTime = LocalTime.of(23, 0)

    @Test
    fun `given completely empty schedule, when calculating free slots, then returns single slot covering awake window`() {
        val freeSlots = FreeSlotCalculator.calculateFreeSlots(
            wakeUpTime = wakeTime,
            sleepTime = sleepTime,
            busyIntervals = emptyList()
        )

        assertEquals(1, freeSlots.size)
        assertEquals(wakeTime, freeSlots[0].startTime)
        assertEquals(sleepTime, freeSlots[0].endTime)
        assertEquals(16 * 60, freeSlots[0].durationMinutes) // 7:00 to 23:00 = 16 hours = 960 mins
    }

    @Test
    fun `given single 90 minute event in middle of day, when calculating free slots, then returns morning and evening slots`() {
        val event = TimeInterval(LocalTime.of(10, 0), LocalTime.of(11, 30))

        val freeSlots = FreeSlotCalculator.calculateFreeSlots(
            wakeUpTime = wakeTime,
            sleepTime = sleepTime,
            busyIntervals = listOf(event)
        )

        assertEquals(2, freeSlots.size)

        // Morning slot: 07:00 to 10:00 (180 mins)
        assertEquals(LocalTime.of(7, 0), freeSlots[0].startTime)
        assertEquals(LocalTime.of(10, 0), freeSlots[0].endTime)
        assertEquals(180, freeSlots[0].durationMinutes)

        // Afternoon/Evening slot: 11:30 to 23:00 (11.5 hours = 690 mins)
        assertEquals(LocalTime.of(11, 30), freeSlots[1].startTime)
        assertEquals(LocalTime.of(23, 0), freeSlots[1].endTime)
        assertEquals(690, freeSlots[1].durationMinutes)
    }

    @Test
    fun `given two events with 10 min gap, when minSlot is 15 min, then ignores the gap`() {
        val event1 = TimeInterval(LocalTime.of(9, 0), LocalTime.of(10, 0))
        val event2 = TimeInterval(LocalTime.of(10, 10), LocalTime.of(11, 0)) // only 10 mins gap

        val freeSlots = FreeSlotCalculator.calculateFreeSlots(
            wakeUpTime = LocalTime.of(9, 0),
            sleepTime = LocalTime.of(11, 0),
            busyIntervals = listOf(event1, event2),
            minSlotMinutes = 15
        )

        // The 10 minute gap is ignored because minSlotMinutes is 15
        assertTrue(freeSlots.isEmpty())
    }

    @Test
    fun `given overlapping busy events, when calculating free slots, then merges them properly`() {
        val event1 = TimeInterval(LocalTime.of(14, 0), LocalTime.of(15, 30))
        val event2 = TimeInterval(LocalTime.of(15, 0), LocalTime.of(16, 0)) // overlaps with event1

        val freeSlots = FreeSlotCalculator.calculateFreeSlots(
            wakeUpTime = LocalTime.of(13, 0),
            sleepTime = LocalTime.of(17, 0),
            busyIntervals = listOf(event1, event2)
        )

        assertEquals(2, freeSlots.size)
        // 13:00 to 14:00 (60 mins)
        assertEquals(LocalTime.of(13, 0), freeSlots[0].startTime)
        assertEquals(LocalTime.of(14, 0), freeSlots[0].endTime)

        // 16:00 to 17:00 (60 mins)
        assertEquals(LocalTime.of(16, 0), freeSlots[1].startTime)
        assertEquals(LocalTime.of(17, 0), freeSlots[1].endTime)
    }

    @Test
    fun `given completely busy day covering awake window, when calculating free slots, then returns empty list`() {
        val busyFullDay = TimeInterval(wakeTime, sleepTime)

        val freeSlots = FreeSlotCalculator.calculateFreeSlots(
            wakeUpTime = wakeTime,
            sleepTime = sleepTime,
            busyIntervals = listOf(busyFullDay)
        )

        assertTrue(freeSlots.isEmpty())
    }
}
