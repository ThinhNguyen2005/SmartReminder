package com.smartreminder.domain.model.task

import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain entity representing an individual task with a scheduled date and time.
 */
data class Task(
    val id: String,
    val title: String,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime,
    val durationMinutes: Int = 30,
    val isCompleted: Boolean = false,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val category: String? = null,
    val isVirtual: Boolean = false,
    val attendees: Int = 0
) {
    init {
        require(title.isNotBlank()) { "Task title must not be blank" }
        require(durationMinutes > 0) { "Task durationMinutes must be greater than zero" }
        require(attendees >= 0) { "Task attendees must be non-negative" }
    }
}
