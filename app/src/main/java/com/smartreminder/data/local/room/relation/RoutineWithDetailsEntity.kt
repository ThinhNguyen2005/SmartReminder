package com.smartreminder.data.local.room.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.smartreminder.data.local.room.entity.RoutineEntity
import com.smartreminder.data.local.room.entity.RoutineItemEntity
import com.smartreminder.data.local.room.entity.RoutineWeeklyDayEntity

/**
 * Composite read entity bundling a [RoutineEntity] with its [RoutineWeeklyDayEntity]s and [RoutineItemEntity]s.
 * Intentionally does NOT include override history.
 */
data class RoutineWithDetailsEntity(
    @Embedded
    val routine: RoutineEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "routine_id"
    )
    val weeklyDays: List<RoutineWeeklyDayEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "routine_id"
    )
    val items: List<RoutineItemEntity>
)
