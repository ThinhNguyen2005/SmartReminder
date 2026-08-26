package com.smartreminder.ui.schedules.editor

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import java.time.DayOfWeek
import java.time.LocalTime

sealed interface RoutineEditorMode {
    data object Create : RoutineEditorMode

    data class Edit(val routineId: RoutineId) : RoutineEditorMode
}

data class RoutineEditorGroupUiModel(
    val id: ScheduleGroupId,
    val name: String
)

data class RoutineItemDraftUiModel(
    val draftKey: Long,
    val existingItemId: RoutineItemId?,
    val title: String,
    val scheduledTime: LocalTime,
    val durationMinutes: Int?,
    val enabled: Boolean
)

data class RoutineItemEditorUiState(
    val draftKey: Long?,
    val title: String = "",
    val scheduledTime: LocalTime = LocalTime.of(9, 0),
    val titleError: RoutineEditorFieldError? = null
)

data class RoutineEditorUiState(
    val mode: RoutineEditorMode? = null,
    val isLoading: Boolean = false,
    val name: String = "",
    val selectedGroupId: ScheduleGroupId? = null,
    val groups: List<RoutineEditorGroupUiModel> = emptyList(),
    val selectedDays: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val items: List<RoutineItemDraftUiModel> = emptyList(),
    val itemEditor: RoutineItemEditorUiState? = null,
    val nameError: RoutineEditorFieldError? = null,
    val daysError: RoutineEditorFieldError? = null,
    val isSaving: Boolean = false,
    val saveError: RoutineEditorError? = null,
    val showDiscardConfirmation: Boolean = false
)

enum class RoutineEditorFieldError {
    NAME_REQUIRED,
    DAYS_REQUIRED,
    ITEM_TITLE_REQUIRED
}

enum class RoutineEditorError {
    LOAD_FAILED,
    SAVE_FAILED
}
