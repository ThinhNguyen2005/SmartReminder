package com.smartreminder.data.local.room.entity.collaboration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "cached_group_members",
    primaryKeys = ["group_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = CachedCollaborationGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CachedGroupMemberEntity(
    @ColumnInfo(name = "group_id")
    val groupId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "role")
    val role: String,
    @ColumnInfo(name = "joined_at")
    val joinedAt: Long,
    @ColumnInfo(name = "display_name")
    val displayName: String? = null,
    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String? = null
)
