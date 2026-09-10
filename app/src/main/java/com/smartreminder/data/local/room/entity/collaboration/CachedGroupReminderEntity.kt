package com.smartreminder.data.local.room.entity.collaboration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_group_reminders",
    foreignKeys = [
        ForeignKey(
            entity = CachedCollaborationGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["group_id"])]
)
data class CachedGroupReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "audience_type")
    val audienceType: String,
    @ColumnInfo(name = "audience_user_id")
    val audienceUserId: String?,
    @ColumnInfo(name = "remind_at")
    val remindAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
