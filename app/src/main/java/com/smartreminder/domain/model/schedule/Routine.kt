package com.smartreminder.domain.model.schedule

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import java.time.Instant

/**
 * Aggregate root representing a recurring schedule definition (e.g. University Day, Morning Routine).
 * [enabled] serves as the master switch.
 */
data class Routine(
    val id: RoutineId,
    val groupId: ScheduleGroupId? = null,
    val name: String,
    val description: String? = null,
    val iconKey: String? = null,
    val colorKey: String? = null,
    val recurrence: RecurrenceRule,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Routine name must not be blank" }
        require(sortOrder >= 0) { "Routine sortOrder must be non-negative" }
    }
}
