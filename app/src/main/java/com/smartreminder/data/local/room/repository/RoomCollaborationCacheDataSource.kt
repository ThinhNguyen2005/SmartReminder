package com.smartreminder.data.local.room.repository

import androidx.room.withTransaction
import com.smartreminder.data.local.room.CueDatabase
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
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

    override fun observeTaskDetails(groupId: String) = dao.observeTaskDetails(groupId)

    override fun observeInvites(): Flow<List<CachedGroupInviteEntity>> = dao.observeInvites()

    override suspend fun replaceGroups(groups: List<CachedCollaborationGroupEntity>) {
        database.withTransaction {
            val distinctGroups = groups.distinctBy(CachedCollaborationGroupEntity::id)
            if (distinctGroups.isEmpty()) {
                dao.deleteAllGroups()
            } else {
                // Retained parents are upserted in place so their member/task/profile
                // children survive a later detail-refresh failure. Only stale parents
                // are deleted, which intentionally cascades their own child rows.
                dao.deleteGroupsNotIn(distinctGroups.map(CachedCollaborationGroupEntity::id))
                dao.upsertGroups(distinctGroups)
            }
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
        database.withTransaction {
            dao.deleteGroup(groupId)
            dao.deleteInvitesForGroup(groupId)
        }
    }

    override suspend fun replaceTasks(
        groupId: String,
        tasks: List<CachedGroupTaskEntity>,
        reminders: List<CachedGroupTaskReminderEntity>
    ) {
        database.withTransaction {
            dao.replaceTasks(groupId, tasks, reminders)
        }
    }

    override suspend fun removeTasksForGroup(groupId: String) {
        dao.deleteTasksForGroup(groupId)
    }

    override suspend fun findTaskGroupId(taskId: String): String? = dao.findTaskGroupId(taskId)

    override suspend fun removeInvitesForGroup(groupId: String) {
        dao.deleteInvitesForGroup(groupId)
    }

    override suspend fun removeInvite(inviteId: String) {
        dao.deleteInviteById(inviteId)
    }

    override suspend fun clearAll() {
        database.withTransaction {
            dao.deleteAllInvites()
            dao.deleteAllGroups()
        }
    }
}
