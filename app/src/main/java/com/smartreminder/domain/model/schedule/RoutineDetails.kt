package com.smartreminder.domain.model.schedule

/**
 * Composite read model bundling a [Routine] definition with its ordered [RoutineItem]s.
 * Used when full timeline details are needed (e.g. editor, detail views).
 */
data class RoutineDetails(
    val routine: Routine,
    val items: List<RoutineItem> = emptyList()
)
