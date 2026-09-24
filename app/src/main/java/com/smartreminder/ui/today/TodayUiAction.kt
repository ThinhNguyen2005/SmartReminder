package com.smartreminder.ui.today

import com.smartreminder.domain.model.today.ScheduleConflict
import java.time.LocalTime

sealed interface TodayUiAction {
    data class ChangeViewMode(val mode: TodayViewMode) : TodayUiAction
    data class ToggleTaskCompletion(val taskId: String, val isCompleted: Boolean) : TodayUiAction
    data class AcceptFocusBlock(val taskTitle: String, val startTime: LocalTime) : TodayUiAction
    data class ResolveConflict(val conflict: ScheduleConflict) : TodayUiAction
    data class AddEventClick(val defaultStartTime: LocalTime? = null) : TodayUiAction
    data object DismissAddTask : TodayUiAction
    data class CreateCustomTask(
        val title: String,
        val startTime: LocalTime,
        val durationMinutes: Int,
        val priority: com.smartreminder.domain.model.task.TaskPriority,
        val category: String? = "General"
    ) : TodayUiAction
    data object NotificationClick : TodayUiAction
    data object ProfileAvatarClick : TodayUiAction
    data object Refresh : TodayUiAction
}
