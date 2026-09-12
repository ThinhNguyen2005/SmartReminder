package com.smartreminder.data.local.room.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity

/** Composite Room read model for one cached task and its task-owned offsets. */
data class GroupTaskWithRemindersEntity(
    @Embedded
    val task: CachedGroupTaskEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "task_id"
    )
    val reminders: List<CachedGroupTaskReminderEntity>
)
