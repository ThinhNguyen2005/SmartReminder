package com.smartreminder.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "routine_overrides",
    primaryKeys = ["routine_id", "override_date_epoch_day"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RoutineOverrideEntity(
    @ColumnInfo(name = "routine_id")
    val routineId: String,

    @ColumnInfo(name = "override_date_epoch_day")
    val overrideDateEpochDay: Long,

    @ColumnInfo(name = "override_type")
    val overrideType: String // "skip", "force_run"
)
