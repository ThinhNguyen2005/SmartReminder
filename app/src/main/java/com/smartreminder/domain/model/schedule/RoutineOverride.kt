package com.smartreminder.domain.model.schedule

import com.smartreminder.domain.model.schedule.ids.RoutineId
import java.time.LocalDate

enum class OverrideType(val storageKey: String) {
    SKIP("skip"),
    FORCE_RUN("force_run");

    companion object {
        fun fromStorageKey(key: String): OverrideType? =
            entries.firstOrNull { it.storageKey == key }
    }
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
