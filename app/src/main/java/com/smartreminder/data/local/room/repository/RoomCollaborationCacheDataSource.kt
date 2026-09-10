package com.smartreminder.data.local.room.repository

import androidx.room.withTransaction
import com.smartreminder.data.local.room.CueDatabase
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.repository.collaboration.CollaborationCacheDataSource
import kotlinx.coroutines.flow.Flow

/** Atomic cache adapter used by the production collaboration repository. */
class RoomCollaborationCacheDataSource(
    private val database: CueDatabase
) : CollaborationCacheDataSource {
    private val dao = database.collaborationCacheDao()

    override fun observeGroups(): Flow<List<CachedCollaborationGroupEntity>> = dao.observeGroups()

    override fun observeGroup(groupId: String): Flow<CachedCollaborationGroupEntity?> =
        dao.observeGroup(groupId)

    override fun observeMembers(groupId: String): Flow<List<CachedGroupMemberEntity>> =
        dao.observeMembers(groupId)

    override fun observeTasks(groupId: String): Flow<List<CachedGroupTaskEntity>> =
        dao.observeTasks(groupId)

    override fun observeInvites(): Flow<List<CachedGroupInviteEntity>> = dao.observeInvites()

    override suspend fun replaceGroups(groups: List<CachedCollaborationGroupEntity>) {
        database.withTransaction {
            dao.deleteAllGroups()
            if (groups.isNotEmpty()) dao.upsertGroups(groups)
        }
    }

    override suspend fun replaceGroup(
        group: CachedCollaborationGroupEntity,
        members: List<CachedGroupMemberEntity>
    ) {
        database.withTransaction {
            dao.upsertGroup(group)
            dao.replaceMembers(group.id, members)
        }
    }

    override suspend fun replaceInvites(invites: List<CachedGroupInviteEntity>) {
        database.withTransaction {
            dao.deleteAllInvites()
            if (invites.isNotEmpty()) dao.upsertInvites(invites)
        }
    }

    override suspend fun removeGroup(groupId: String) {
        dao.deleteGroup(groupId)
    }
}
