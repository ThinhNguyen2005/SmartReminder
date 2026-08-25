package com.smartreminder.ui.schedules

import com.smartreminder.domain.model.schedule.ids.RoutineId

sealed interface SchedulesEffect {
    data class OpenRoutine(val routineId: RoutineId) : SchedulesEffect
    data object CreateRoutine : SchedulesEffect
    data object ManageGroups : SchedulesEffect
}
