package com.smartreminder.ui.schedules.editor

import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import java.time.DayOfWeek
import java.time.LocalTime

sealed interface RoutineEditorAction {
    data class Initialize(val mode: RoutineEditorMode, val entryToken: Long) : RoutineEditorAction
    data class ChangeName(val name: String) : RoutineEditorAction
    data class SelectGroup(val groupId: ScheduleGroupId?) : RoutineEditorAction
    data class ToggleDay(val day: DayOfWeek) : RoutineEditorAction
    data class SetEnabled(val enabled: Boolean) : RoutineEditorAction
    data object OpenAddItem : RoutineEditorAction
    data class OpenEditItem(val draftKey: Long) : RoutineEditorAction
    data class ChangeItemTitle(val title: String) : RoutineEditorAction
    data class ChangeItemTime(val scheduledTime: LocalTime) : RoutineEditorAction
    data object ConfirmItemEditor : RoutineEditorAction
    data object DismissItemEditor : RoutineEditorAction
    data class RemoveItem(val draftKey: Long) : RoutineEditorAction
    data object Save : RoutineEditorAction
    data object BackPressed : RoutineEditorAction
    data object ConfirmDiscard : RoutineEditorAction
    data object CancelDiscard : RoutineEditorAction
    data object DismissError : RoutineEditorAction
}
