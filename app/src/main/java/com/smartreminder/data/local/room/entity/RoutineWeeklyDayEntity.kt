package com.smartreminder.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "routine_weekly_days",
    primaryKeys = ["routine_id", "day_of_week"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RoutineWeeklyDayEntity(
    @ColumnInfo(name = "routine_id")
    val routineId: String,

    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int // 1 (Monday) .. 7 (Sunday) ISO-8601
)
