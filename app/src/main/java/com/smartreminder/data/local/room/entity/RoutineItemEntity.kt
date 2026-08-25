package com.smartreminder.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_items",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["routine_id"])
    ]
)
data class RoutineItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "routine_id")
    val routineId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "scheduled_minute")
    val scheduledMinute: Int, // 0..1439 (minutes of day)

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int?,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean
)
