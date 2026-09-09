package com.smartreminder.domain.today

import com.smartreminder.domain.model.today.ScheduleConflict
import com.smartreminder.domain.model.today.TodayTimelineItem.FreeSlotBlock
import java.time.LocalTime

data class SchedulableEvent(
    val id: String,
    val title: String,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val locationOrDetails: String? = null
) {
    val endTime: LocalTime = startTime.plusMinutes(durationMinutes.toLong())
}

/**
 * Pure domain service that detects temporal overlaps between events
 * and computes actionable resolution suggestions using available free slots.
 */
object ConflictDetector {

    /**
     * Finds overlapping pairs and matches them with suitable free slots for resolution.
     */
    fun detectConflicts(
        events: List<SchedulableEvent>,
        freeSlots: List<FreeSlotBlock> = emptyList()
    ): List<ScheduleConflict> {
        if (events.size < 2) return emptyList()

        val sorted = events.sortedBy { it.startTime }
        val conflicts = mutableListOf<ScheduleConflict>()

        for (i in 0 until sorted.size - 1) {
            val first = sorted[i]
            for (j in i + 1 until sorted.size) {
                val second = sorted[j]

                // Since sorted by startTime, second.startTime >= first.startTime
                if (second.startTime.isBefore(first.endTime)) {
                    // Overlap detected!
                    val conflictTime = second.startTime
                    val movableEvent = second // by default, recommend moving the subsequent/overlapping event

                    // Find a free slot that can accommodate the movable event
                    val suitableSlot = freeSlots.firstOrNull { slot ->
                        slot.durationMinutes >= movableEvent.durationMinutes &&
                                slot.startTime.isAfter(first.endTime)
                    } ?: freeSlots.firstOrNull { it.durationMinutes >= movableEvent.durationMinutes }

                    conflicts.add(
                        ScheduleConflict(
                            conflictTime = conflictTime,
                            primaryTitle = first.title,
                            conflictingTitle = second.title,
                            locationOrDetails = second.locationOrDetails ?: first.locationOrDetails,
                            suggestedMoveTime = suitableSlot?.startTime,
                            suggestedItemTitle = movableEvent.title,
                            conflictingEventId = movableEvent.id
                        )
                    )
                } else {
                    // Because it's sorted, no subsequent items will start before first.endTime
                    break
                }
            }
        }

        return conflicts
    }
}
