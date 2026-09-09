package com.smartreminder.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["scheduled_date_epoch_day"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "scheduled_date_epoch_day")
    val scheduledDateEpochDay: Long,

    @ColumnInfo(name = "scheduled_minute")
    val scheduledMinute: Int,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "priority")
    val priority: String,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "is_virtual")
    val isVirtual: Boolean,

    @ColumnInfo(name = "attendees")
    val attendees: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
