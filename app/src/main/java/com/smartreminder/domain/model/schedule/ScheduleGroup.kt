package com.smartreminder.domain.model.schedule

import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import java.time.Instant

/**
 * Organizational category entity for grouping routines (e.g. Study, Personal, Health).
 * Contains NO scheduling, recurrence, or wake/sleep business logic.
 */
data class ScheduleGroup(
    val id: ScheduleGroupId,
    val name: String,
    val iconKey: String? = null,
    val colorKey: String? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "ScheduleGroup name must not be blank" }
        require(sortOrder >= 0) { "ScheduleGroup sortOrder must be non-negative" }
    }
}
