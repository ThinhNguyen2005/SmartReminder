package com.smartreminder.domain.model.today

import java.time.LocalTime

/**
 * Domain model describing an overlap conflict between two scheduled items.
 */
data class ScheduleConflict(
    val conflictTime: LocalTime,
    val primaryTitle: String,
    val conflictingTitle: String,
    val locationOrDetails: String? = null,
    val suggestedMoveTime: LocalTime? = null,
    val suggestedItemTitle: String? = null,
    val conflictingEventId: String? = null
)
