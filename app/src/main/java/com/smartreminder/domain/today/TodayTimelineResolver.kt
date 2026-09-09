package com.smartreminder.domain.today

import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.task.Task
import com.smartreminder.domain.model.today.AiSuggestion
import com.smartreminder.domain.model.today.DailyProgress
import com.smartreminder.domain.model.today.ScheduleConflict
import com.smartreminder.domain.model.today.TodayTimelineItem
import com.smartreminder.domain.model.today.TodayTimelineItem.FreeSlotBlock
import com.smartreminder.domain.model.today.TodayTimelineItem.RoutineEvent
import com.smartreminder.domain.model.today.TodayTimelineItem.SuggestedFocusBlock
import com.smartreminder.domain.model.today.TodayTimelineItem.TaskEvent
import com.smartreminder.domain.schedule.RoutineOccurrenceResolver
import java.time.LocalDate
import java.time.LocalTime

data class TodayTimelineResult(
    val items: List<TodayTimelineItem>,
    val progress: DailyProgress,
    val activeConflict: ScheduleConflict?,
    val aiSuggestion: AiSuggestion?
)

/**
 * Pure domain orchestrator that resolves the daily schedule stream.
 */
object TodayTimelineResolver {

    fun resolve(
        currentDate: LocalDate,
        currentTime: LocalTime,
        wakeUpTime: LocalTime,
        sleepTime: LocalTime,
        routinesWithDetails: List<RoutineDetails>,
        routineOverrides: List<RoutineOverride> = emptyList(),
        tasks: List<Task> = emptyList()
    ): TodayTimelineResult {
        // 1. Filter routines scheduled for today
        val activeRoutineItems = mutableListOf<RoutineEvent>()
        for (details in routinesWithDetails) {
            val routine = details.routine
            val override = routineOverrides.firstOrNull { it.routineId == routine.id && it.date == currentDate }
            if (RoutineOccurrenceResolver.shouldRun(routine, currentDate, override)) {
                for (item in details.items) {
                    if (item.enabled) {
                        val duration = item.durationMinutes ?: 30
                        val isPast = item.scheduledTime.plusMinutes(duration.toLong()).isBefore(currentTime)
                        activeRoutineItems.add(
                            RoutineEvent(
                                id = "routine_item_${item.id.value}",
                                routineId = routine.id,
                                routineItemId = item.id,
                                title = item.title,
                                routineName = routine.name,
                                startTime = item.scheduledTime,
                                durationMinutes = duration,
                                isPast = isPast,
                                categoryTag = routine.name
                            )
                        )
                    }
                }
            }
        }

        // 2. Filter tasks for today
        val todayTasks = tasks.filter { it.scheduledDate == currentDate }
        val taskEvents = todayTasks.map { task ->
            val isPast = task.scheduledTime.plusMinutes(task.durationMinutes.toLong()).isBefore(currentTime)
            TaskEvent(
                id = "task_${task.id}",
                title = task.title,
                startTime = task.scheduledTime,
                durationMinutes = task.durationMinutes,
                isPast = isPast,
                isCompleted = task.isCompleted,
                priority = task.priority,
                category = task.category,
                isVirtual = task.isVirtual,
                attendeeCount = task.attendees
            )
        }

        // 3. Build schedulable events list for conflict & free slot calculation
        val allSchedulables = mutableListOf<SchedulableEvent>()
        activeRoutineItems.forEach {
            allSchedulables.add(
                SchedulableEvent(
                    id = it.id,
                    title = it.title,
                    startTime = it.startTime,
                    durationMinutes = it.durationMinutes
                )
            )
        }
        taskEvents.forEach {
            allSchedulables.add(
                SchedulableEvent(
                    id = it.id,
                    title = it.title,
                    startTime = it.startTime,
                    durationMinutes = it.durationMinutes,
                    locationOrDetails = it.category
                )
            )
        }

        // 4. Calculate busy intervals and free slots
        val busyIntervals = allSchedulables.map {
            TimeInterval(it.startTime, it.startTime.plusMinutes(it.durationMinutes.toLong()))
        }
        val rawFreeSlots = FreeSlotCalculator.calculateFreeSlots(wakeUpTime, sleepTime, busyIntervals)

        // 5. Detect conflicts
        val detectedConflicts = ConflictDetector.detectConflicts(allSchedulables, rawFreeSlots)
        val activeConflict = detectedConflicts.firstOrNull()

        // 6. Focus block & AI Suggestion matching
        // Find if there is an uncompleted task or a focus block we can place in an upcoming free slot
        var aiSuggestion: AiSuggestion? = null
        var focusBlock: SuggestedFocusBlock? = null
        val remainingFreeSlots = rawFreeSlots.toMutableList()

        val candidateTask = todayTasks.firstOrNull { !it.isCompleted && it.durationMinutes >= 45 }
        val focusCandidateSlot = rawFreeSlots.firstOrNull { it.durationMinutes >= 60 && !it.startTime.isBefore(currentTime) }

        if (focusCandidateSlot != null && candidateTask != null) {
            val suggestedDuration = minOf(candidateTask.durationMinutes, focusCandidateSlot.durationMinutes)
            focusBlock = SuggestedFocusBlock(
                id = "suggested_focus_${candidateTask.id}",
                taskTitle = candidateTask.title,
                startTime = focusCandidateSlot.startTime,
                durationMinutes = suggestedDuration,
                reason = "Fits ${suggestedDuration}m focus window"
            )
            aiSuggestion = AiSuggestion(
                id = "ai_focus_suggestion",
                message = "Your afternoon looks busy. I found a ${suggestedDuration}-minute focus window between ${focusCandidateSlot.startTime} and ${focusCandidateSlot.startTime.plusMinutes(suggestedDuration.toLong())}.",
                focusWindowStart = focusCandidateSlot.startTime,
                focusWindowEnd = focusCandidateSlot.startTime.plusMinutes(suggestedDuration.toLong()),
                suggestedTaskTitle = candidateTask.title,
                durationMinutes = suggestedDuration
            )
            // Replace or adjust the free slot
            remainingFreeSlots.remove(focusCandidateSlot)
        } else if (rawFreeSlots.isNotEmpty()) {
            val nextSlot = rawFreeSlots.firstOrNull { !it.startTime.isBefore(currentTime) } ?: rawFreeSlots.first()
            aiSuggestion = AiSuggestion(
                id = "ai_free_suggestion",
                message = "You have ${nextSlot.durationMinutes} minutes free starting at ${nextSlot.startTime}.",
                focusWindowStart = nextSlot.startTime,
                focusWindowEnd = nextSlot.endTime,
                durationMinutes = nextSlot.durationMinutes
            )
        }

        // 7. Calculate Daily Progress
        val completedRoutineCount = activeRoutineItems.count { it.isPast }
        val completedTaskCount = taskEvents.count { it.isCompleted }
        val totalCount = activeRoutineItems.size + taskEvents.size
        val completedCount = completedRoutineCount + completedTaskCount
        val progress = DailyProgress(completedCount = completedCount, totalCount = totalCount)

        // 8. Merge and sort timeline items
        val allTimelineItems = mutableListOf<TodayTimelineItem>()
        allTimelineItems.addAll(activeRoutineItems)
        allTimelineItems.addAll(taskEvents)
        allTimelineItems.addAll(remainingFreeSlots)
        if (focusBlock != null) {
            allTimelineItems.add(focusBlock)
        }
        allTimelineItems.sortBy { it.startTime }

        return TodayTimelineResult(
            items = allTimelineItems,
            progress = progress,
            activeConflict = activeConflict,
            aiSuggestion = aiSuggestion
        )
    }
}
