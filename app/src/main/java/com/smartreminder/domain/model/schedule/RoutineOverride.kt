package com.smartreminder.domain.model.schedule

import com.smartreminder.domain.model.schedule.ids.RoutineId
import java.time.LocalDate

enum class OverrideType {
    SKIP,
    FORCE_RUN
}

/**
 * Natural composite identity (routineId + date) representing a single-day exception
 * without mutating the underlying [Routine.enabled] master switch.
 */
data class RoutineOverride(
    val routineId: RoutineId,
    val date: LocalDate,
    val type: OverrideType
)
