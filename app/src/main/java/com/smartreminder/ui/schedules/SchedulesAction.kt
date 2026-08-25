package com.smartreminder.ui.schedules

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId

sealed interface SchedulesAction {
    data class SelectGroup(val groupId: ScheduleGroupId?) : SchedulesAction
    data class SetRoutineEnabled(val routineId: RoutineId, val enabled: Boolean) : SchedulesAction
    data class OpenRoutine(val routineId: RoutineId) : SchedulesAction
    data object CreateRoutine : SchedulesAction
    data object ManageGroups : SchedulesAction
    data object DismissError : SchedulesAction
}
