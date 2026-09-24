package com.smartreminder.domain.model.today

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.task.TaskPriority
import java.time.LocalTime

/**
 * Sealed hierarchy representing all distinct items that can be displayed
 * on the chronological Today timeline.
 */
sealed interface TodayTimelineItem {

    val id: String
    val startTime: LocalTime

    /**
     * An occurrence of a recurring routine item scheduled for today.
     */
    data class RoutineEvent(
        override val id: String,
        val routineId: RoutineId,
        val routineItemId: RoutineItemId,
        val title: String,
        val routineName: String,
        override val startTime: LocalTime,
        val durationMinutes: Int = 30,
        val isPast: Boolean = false,
        val categoryTag: String? = null,
        val isVirtual: Boolean = false,
        val attendeeCount: Int = 0
    ) : TodayTimelineItem

    /**
     * A one-off or scheduled task for today.
     */
    data class TaskEvent(
        override val id: String,
        val title: String,
        override val startTime: LocalTime,
        val durationMinutes: Int = 30,
        val isPast: Boolean = false,
        val isCompleted: Boolean = false,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val category: String? = null,
        val isVirtual: Boolean = false,
        val attendeeCount: Int = 0
    ) : TodayTimelineItem

    /**
     * A dynamically calculated free block between scheduled events.
     */
    data class FreeSlotBlock(
        override val id: String,
        override val startTime: LocalTime,
        val endTime: LocalTime,
        val durationMinutes: Int,
        val fitSuggestionTaskTitle: String? = null
    ) : TodayTimelineItem

    /**
     * An AI recommended focus block positioned in an optimal free slot.
     */
    data class SuggestedFocusBlock(
        override val id: String,
        val taskTitle: String,
        override val startTime: LocalTime,
        val durationMinutes: Int,
        val reason: String
    ) : TodayTimelineItem

    /**
     * A visual representation of a schedule conflict where two events collide.
     */
    data class ConflictBlock(
        override val id: String,
        override val startTime: LocalTime,
        val primaryTitle: String,
        val conflictingTitle: String,
        val locationOrDetails: String? = null,
        val suggestedMoveTime: LocalTime? = null,
        val suggestedItemTitle: String? = null
    ) : TodayTimelineItem
}
