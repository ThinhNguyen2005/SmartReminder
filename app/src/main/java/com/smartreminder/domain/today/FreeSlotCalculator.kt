package com.smartreminder.domain.today

import com.smartreminder.domain.model.today.TodayTimelineItem.FreeSlotBlock
import java.time.LocalTime

/**
 * Pure domain service calculating available free time slots between scheduled events
 * within the user's Awake Window (wakeUpTime to sleepTime).
 */
object FreeSlotCalculator {

    const val DEFAULT_MIN_SLOT_MINUTES = 15

    /**
     * Calculates free slots given the user's waking boundary and a list of busy intervals.
     *
     * @param wakeUpTime Beginning of the planning window (e.g. 07:00).
     * @param sleepTime End of the planning window (e.g. 23:00).
     * @param busyIntervals List of scheduled intervals during the day.
     * @param minSlotMinutes Minimum duration required to consider a gap as a free slot.
     * @return Ordered list of [FreeSlotBlock]s.
     */
    fun calculateFreeSlots(
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        busyIntervals: List<TimeInterval>,
        minSlotMinutes: Int = DEFAULT_MIN_SLOT_MINUTES
    ): List<FreeSlotBlock> {
        // Effective end of daytime planning on current day
        val effectiveSleepTime = if (sleepTime.isBefore(wakeUpTime)) {
            LocalTime.of(23, 59)
        } else {
            sleepTime
        }

        if (effectiveSleepTime.isBefore(wakeUpTime)) return emptyList()

        // 1. Filter and clip busy intervals within [wakeUpTime, effectiveSleepTime]
        val validIntervals = busyIntervals.mapNotNull { interval ->
            val clippedStart = maxOf(interval.start, wakeUpTime)
            val clippedEnd = minOf(interval.end, effectiveSleepTime)
            if (clippedEnd.isAfter(clippedStart)) {
                TimeInterval(clippedStart, clippedEnd)
            } else {
                null
            }
        }.sortedBy { it.start }

        // 2. Merge overlapping or contiguous intervals
        val mergedIntervals = mutableListOf<TimeInterval>()
        for (interval in validIntervals) {
            if (mergedIntervals.isEmpty()) {
                mergedIntervals.add(interval)
            } else {
                val last = mergedIntervals.last()
                if (!interval.start.isAfter(last.end)) {
                    // Overlaps or contiguous -> extend
                    val newEnd = maxOf(last.end, interval.end)
                    mergedIntervals[mergedIntervals.lastIndex] = TimeInterval(last.start, newEnd)
                } else {
                    mergedIntervals.add(interval)
                }
            }
        }

        // 3. Find gaps
        val freeSlots = mutableListOf<FreeSlotBlock>()
        var currentPointer = wakeUpTime

        for (busy in mergedIntervals) {
            if (busy.start.isAfter(currentPointer)) {
                val gapMinutes = (busy.start.toSecondOfDay() - currentPointer.toSecondOfDay()) / 60
                if (gapMinutes >= minSlotMinutes) {
                    freeSlots.add(
                        FreeSlotBlock(
                            id = "free_${currentPointer}_${busy.start}",
                            startTime = currentPointer,
                            endTime = busy.start,
                            durationMinutes = gapMinutes
                        )
                    )
                }
            }
            if (busy.end.isAfter(currentPointer)) {
                currentPointer = busy.end
            }
        }

        // Final gap from last event to effectiveSleepTime
        if (effectiveSleepTime.isAfter(currentPointer)) {
            val trailingMinutes = (effectiveSleepTime.toSecondOfDay() - currentPointer.toSecondOfDay()) / 60
            if (trailingMinutes >= minSlotMinutes) {
                freeSlots.add(
                    FreeSlotBlock(
                        id = "free_${currentPointer}_$effectiveSleepTime",
                        startTime = currentPointer,
                        endTime = effectiveSleepTime,
                        durationMinutes = trailingMinutes
                    )
                )
            }
        }

        return freeSlots
    }
}
