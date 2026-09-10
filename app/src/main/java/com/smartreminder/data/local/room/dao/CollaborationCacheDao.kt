package com.smartreminder.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupReminderEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollaborationCacheDao {
    // Groups
    @Query("SELECT * FROM cached_collaboration_groups ORDER BY name ASC, id ASC")
    fun observeGroups(): Flow<List<CachedCollaborationGroupEntity>>

    @Query("SELECT * FROM cached_collaboration_groups WHERE id = :groupId")
    fun observeGroup(groupId: String): Flow<CachedCollaborationGroupEntity?>

    @Upsert
    suspend fun upsertGroup(group: CachedCollaborationGroupEntity)

    @Query("DELETE FROM cached_collaboration_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    // Members
    @Query("SELECT * FROM cached_group_members WHERE group_id = :groupId ORDER BY joined_at ASC, user_id ASC")
    fun observeMembers(groupId: String): Flow<List<CachedGroupMemberEntity>>

    @Upsert
    suspend fun upsertMembers(members: List<CachedGroupMemberEntity>)

    @Transaction
    suspend fun upsertMembers(groupId: String, members: List<CachedGroupMemberEntity>) {
        require(members.all { it.groupId == groupId }) {
            "Every member must belong to the requested group"
        }
        if (members.isNotEmpty()) upsertMembers(members)
    }

    @Query("DELETE FROM cached_group_members WHERE group_id = :groupId")
    suspend fun deleteMembersForGroup(groupId: String)

    @Transaction
    suspend fun replaceMembers(groupId: String, members: List<CachedGroupMemberEntity>) {
        deleteMembersForGroup(groupId)
        upsertMembers(groupId, members)
    }

    @Query("DELETE FROM cached_group_members WHERE group_id = :groupId AND user_id = :userId")
    suspend fun deleteMember(groupId: String, userId: String)

    // Tasks
    @Query("SELECT * FROM cached_group_tasks WHERE group_id = :groupId ORDER BY created_at ASC, id ASC")
    fun observeTasks(groupId: String): Flow<List<CachedGroupTaskEntity>>

    @Upsert
    suspend fun upsertTask(task: CachedGroupTaskEntity)

    @Query("DELETE FROM cached_group_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    // Invites
    @Query("SELECT * FROM cached_group_invites ORDER BY created_at DESC, id ASC")
    fun observeInvites(): Flow<List<CachedGroupInviteEntity>>

    @Query("SELECT * FROM cached_group_invites WHERE group_id = :groupId ORDER BY created_at DESC, id ASC")
    fun observeInvites(groupId: String): Flow<List<CachedGroupInviteEntity>>

    @Upsert
    suspend fun upsertInvites(invites: List<CachedGroupInviteEntity>)

    @Transaction
    suspend fun upsertInvites(groupId: String, invites: List<CachedGroupInviteEntity>) {
        require(invites.all { it.groupId == groupId }) {
            "Every invite must belong to the requested group"
        }
        if (invites.isNotEmpty()) upsertInvites(invites)
    }

    @Query("DELETE FROM cached_group_invites WHERE group_id = :groupId")
    suspend fun deleteInvitesForGroup(groupId: String)

    @Query("DELETE FROM cached_group_invites WHERE group_id = :groupId AND id = :inviteId")
    suspend fun deleteInvite(groupId: String, inviteId: String)

    @Transaction
    suspend fun replaceInvites(groupId: String, invites: List<CachedGroupInviteEntity>) {
        deleteInvitesForGroup(groupId)
        upsertInvites(groupId, invites)
    }

    // Reminders
    @Query("SELECT * FROM cached_group_reminders WHERE group_id = :groupId ORDER BY remind_at ASC, id ASC")
    fun observeReminders(groupId: String): Flow<List<CachedGroupReminderEntity>>

    @Upsert
    suspend fun upsertReminder(reminder: CachedGroupReminderEntity)

    // Task Reminders
    @Query("SELECT * FROM cached_group_task_reminders WHERE task_id = :taskId ORDER BY offset_seconds ASC")
    suspend fun getTaskReminders(taskId: String): List<CachedGroupTaskReminderEntity>

    @Upsert
    suspend fun upsertTaskReminders(reminders: List<CachedGroupTaskReminderEntity>)

    @Query("DELETE FROM cached_group_task_reminders WHERE task_id = :taskId")
    suspend fun deleteTaskReminders(taskId: String)

    @Transaction
    suspend fun replaceTaskReminders(taskId: String, reminders: List<CachedGroupTaskReminderEntity>) {
        deleteTaskReminders(taskId)
        if (reminders.isNotEmpty()) upsertTaskReminders(reminders)
    }
}
