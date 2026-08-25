package com.smartreminder.domain.model.schedule

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import java.time.LocalTime

/**
 * Individual timeline entry within a [Routine].
 * Represents recurring milestone behaviors (e.g. 07:00 Prepare, 08:00 Class).
 */
data class RoutineItem(
    val id: RoutineItemId,
    val routineId: RoutineId,
    val title: String,
    val scheduledTime: LocalTime,
    val durationMinutes: Int? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true
) {
    init {
        require(title.isNotBlank()) { "RoutineItem title must not be blank" }
        require(durationMinutes == null || durationMinutes > 0) { "RoutineItem durationMinutes must be greater than zero when specified" }
        require(sortOrder >= 0) { "RoutineItem sortOrder must be non-negative" }
    }
}
