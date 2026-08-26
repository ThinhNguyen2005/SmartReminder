package com.smartreminder.ui.schedules.editor

sealed interface RoutineEditorEffect {
    data object Saved : RoutineEditorEffect
    data object NavigateBack : RoutineEditorEffect
}
