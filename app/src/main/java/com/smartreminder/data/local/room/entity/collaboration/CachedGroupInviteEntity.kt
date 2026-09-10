package com.smartreminder.data.local.room.entity.collaboration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_group_invites",
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
data class CachedGroupInviteEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    @ColumnInfo(name = "inviter_id")
    val inviterId: String,
    @ColumnInfo(name = "invitee_user_id")
    val inviteeUserId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "responded_at")
    val respondedAt: Long?
)
