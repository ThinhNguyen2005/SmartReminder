package com.smartreminder.data.local.room.entity.collaboration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_group_tasks",
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
data class CachedGroupTaskEntity(
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
    @ColumnInfo(name = "assignee_id")
    val assigneeId: String,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "version")
    val version: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
