package com.smartreminder.data.local.room.entity.collaboration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "cached_group_task_reminders",
    primaryKeys = ["task_id", "offset_seconds"],
    foreignKeys = [
        ForeignKey(
            entity = CachedGroupTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CachedGroupTaskReminderEntity(
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "offset_seconds")
    val offsetSeconds: Long
)
