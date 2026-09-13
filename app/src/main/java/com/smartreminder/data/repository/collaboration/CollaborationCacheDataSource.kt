package com.smartreminder.data.repository.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.relation.GroupTaskWithRemindersEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-facing cache contract kept separate from Android Room in repository tests. */
interface CollaborationCacheDataSource {
    fun observeGroups(): Flow<List<CachedCollaborationGroupEntity>>
    fun observeGroup(groupId: String): Flow<CachedCollaborationGroupEntity?>
    fun observeMembers(groupId: String): Flow<List<CachedGroupMemberEntity>>
    fun observeTasks(groupId: String): Flow<List<CachedGroupTaskEntity>>
    /** Cache-first task aggregate read; implementations should return child offsets atomically. */
    fun observeTaskDetails(groupId: String): Flow<List<GroupTaskWithRemindersEntity>> =
        observeTasks(groupId).map { tasks ->
            tasks.map { task -> GroupTaskWithRemindersEntity(task = task, reminders = emptyList()) }
        }
    fun observeInvites(): Flow<List<CachedGroupInviteEntity>>

    suspend fun replaceGroups(groups: List<CachedCollaborationGroupEntity>)
    suspend fun replaceGroup(
        group: CachedCollaborationGroupEntity,
        members: List<CachedGroupMemberEntity>
    )
    suspend fun replaceInvites(invites: List<CachedGroupInviteEntity>)
    suspend fun removeGroup(groupId: String)

    /** Atomically replaces one group's task rows and task-owned reminder offsets. */
    suspend fun replaceTasks(
        groupId: String,
        tasks: List<CachedGroupTaskEntity>,
        reminders: List<CachedGroupTaskReminderEntity>
    ) = Unit

    /** Removes task rows when a group/task is no longer visible to this session. */
    suspend fun removeTasksForGroup(groupId: String) = Unit

    /** Resolves a cached task's group for mutation follow-up refresh/redaction. */
    suspend fun findTaskGroupId(taskId: String): String? = null

    /** Removes all collaboration rows at an authenticated-session boundary. */
    suspend fun clearAll() = Unit

    /** Removes invite rows that could otherwise outlive a revoked group membership. */
    suspend fun removeInvitesForGroup(groupId: String) = Unit

    /** Removes one invite after a response or explicit invite revocation. */
    suspend fun removeInvite(inviteId: String) = Unit
}
