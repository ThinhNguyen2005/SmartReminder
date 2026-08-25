package com.smartreminder.ui.schedules

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import java.time.DayOfWeek

enum class SchedulesError {
    LOAD_FAILED,
    UPDATE_ROUTINE_FAILED
}

data class RecurrenceUiModel(
    val days: Set<DayOfWeek>
) {
    val isEveryDay: Boolean
        get() = days.size == DayOfWeek.entries.size
}

data class GroupFilterUiModel(
    val id: ScheduleGroupId,
    val name: String
)

data class RoutineCardUiModel(
    val id: RoutineId,
    val name: String,
    val recurrence: RecurrenceUiModel,
    val itemCount: Int,
    val enabled: Boolean
)

sealed interface RoutineSectionLabelUiModel {
    data class Group(val name: String) : RoutineSectionLabelUiModel
    data object Ungrouped : RoutineSectionLabelUiModel
}

data class RoutineSectionUiModel(
    val groupId: ScheduleGroupId?,
    val label: RoutineSectionLabelUiModel?,
    val routines: List<RoutineCardUiModel>
)

data class SchedulesUiState(
    val isLoading: Boolean = true,
    val selectedGroupId: ScheduleGroupId? = null,
    val groupFilters: List<GroupFilterUiModel> = emptyList(),
    val sections: List<RoutineSectionUiModel> = emptyList(),
    val error: SchedulesError? = null
)
