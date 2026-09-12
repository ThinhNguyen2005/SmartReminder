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
import com.smartreminder.data.local.room.mapper.GroupTaskMapper
import com.smartreminder.data.local.room.relation.GroupTaskWithRemindersEntity
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

    @Upsert
    suspend fun upsertGroups(groups: List<CachedCollaborationGroupEntity>)

    @Query("DELETE FROM cached_collaboration_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM cached_collaboration_groups WHERE id NOT IN (:groupIds)")
    suspend fun deleteGroupsNotIn(groupIds: List<String>)

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

    /**
     * Reads an authoritative task snapshot with its child offsets. The parent
     * query owns task ordering; [GroupTaskMapper] owns child ordering when the
     * relation is converted to the domain model.
     */
    @Transaction
    @Query("SELECT * FROM cached_group_tasks WHERE group_id = :groupId ORDER BY created_at ASC, id ASC")
    fun observeTaskDetails(groupId: String): Flow<List<GroupTaskWithRemindersEntity>>

    @Transaction
    @Query("SELECT * FROM cached_group_tasks WHERE group_id = :groupId AND id = :taskId")
    suspend fun getTaskDetails(groupId: String, taskId: String): GroupTaskWithRemindersEntity?

    @Upsert
    suspend fun upsertTaskEntity(task: CachedGroupTaskEntity)

    @Transaction
    suspend fun upsertTask(task: CachedGroupTaskEntity) {
        requireTaskIdAvailable(task)
        upsertTaskEntity(task)
    }

    @Upsert
    suspend fun upsertTaskEntities(tasks: List<CachedGroupTaskEntity>)

    @Transaction
    suspend fun upsertTasks(tasks: List<CachedGroupTaskEntity>) {
        requireTaskIdsAvailable(tasks)
        if (tasks.isNotEmpty()) upsertTaskEntities(tasks)
    }

    @Transaction
    suspend fun upsertTasks(groupId: String, tasks: List<CachedGroupTaskEntity>) {
        requireTaskGroup(groupId, tasks)
        upsertTasks(tasks)
    }

    @Query("DELETE FROM cached_group_tasks WHERE group_id = :groupId")
    suspend fun deleteTasksForGroup(groupId: String)

    @Query("DELETE FROM cached_group_tasks WHERE group_id = :groupId AND id NOT IN (:taskIds)")
    suspend fun deleteTasksForGroupNotIn(groupId: String, taskIds: List<String>)

    @Transaction
    suspend fun replaceTasks(groupId: String, tasks: List<CachedGroupTaskEntity>) {
        // Validate before deleting so a malformed remote snapshot cannot
        // erase the previous authoritative cache for this group.
        requireTaskGroup(groupId, tasks)
        requireTaskIdsAvailable(tasks)
        if (tasks.isEmpty()) {
            deleteTasksForGroup(groupId)
        } else {
            // This overload has no reminder payload. Upsert retained parents
            // in place so Room does not cascade-delete their existing offsets,
            // then remove only parents absent from the task-only snapshot.
            upsertTaskEntities(tasks)
            deleteTasksForGroupNotIn(groupId, tasks.map(CachedGroupTaskEntity::id))
        }
    }

    /** Replaces a group's tasks and all task-owned offsets as one cache snapshot. */
    @Transaction
    suspend fun replaceTasks(
        groupId: String,
        tasks: List<CachedGroupTaskEntity>,
        reminders: List<CachedGroupTaskReminderEntity>
    ) {
        requireTaskGroup(groupId, tasks)
        val taskIds = tasks.map(CachedGroupTaskEntity::id).toSet()
        require(reminders.all { it.taskId in taskIds }) {
            "Every task reminder must belong to a task in the requested group"
        }
        requireTaskReminders(reminders)
        requireTaskIdsAvailable(tasks)
        deleteTasksForGroup(groupId)
        if (tasks.isNotEmpty()) upsertTaskEntities(tasks)
        if (reminders.isNotEmpty()) upsertTaskReminders(reminders)
    }

    @Query("DELETE FROM cached_group_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM cached_group_tasks WHERE group_id = :groupId AND id = :taskId")
    suspend fun deleteTask(groupId: String, taskId: String)

    @Query("SELECT group_id FROM cached_group_tasks WHERE id = :taskId")
    suspend fun findTaskGroupId(taskId: String): String?

    @Transaction
    suspend fun replaceTaskWithReminders(
        task: CachedGroupTaskEntity,
        reminders: List<CachedGroupTaskReminderEntity>
    ) {
        requireTaskReminders(reminders)
        require(reminders.all { it.taskId == task.id }) {
            "Every task reminder must belong to the requested task"
        }
        requireTaskIdAvailable(task)
        upsertTaskEntity(task)
        replaceTaskReminders(task.id, reminders)
    }

    @Transaction
    suspend fun upsertTaskWithReminders(
        task: CachedGroupTaskEntity,
        reminders: List<CachedGroupTaskReminderEntity>
    ) {
        replaceTaskWithReminders(task, reminders)
    }

    // Invites
    @Query("SELECT * FROM cached_group_invites ORDER BY created_at DESC, id ASC")
    fun observeInvites(): Flow<List<CachedGroupInviteEntity>>

    @Query("SELECT * FROM cached_group_invites WHERE group_id = :groupId ORDER BY created_at DESC, id ASC")
    fun observeInvites(groupId: String): Flow<List<CachedGroupInviteEntity>>

    @Upsert
    suspend fun upsertInvites(invites: List<CachedGroupInviteEntity>)

    @Query("DELETE FROM cached_group_invites")
    suspend fun deleteAllInvites()

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

    @Query("DELETE FROM cached_group_invites WHERE id = :inviteId")
    suspend fun deleteInviteById(inviteId: String)

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
        require(reminders.all { it.taskId == taskId }) {
            "Every task reminder must belong to the requested task"
        }
        requireTaskReminders(reminders)
        deleteTaskReminders(taskId)
        if (reminders.isNotEmpty()) upsertTaskReminders(reminders)
    }

    private fun requireTaskGroup(groupId: String, tasks: List<CachedGroupTaskEntity>) {
        require(tasks.all { it.groupId == groupId }) {
            "Every task must belong to the requested group"
        }
        require(tasks.map(CachedGroupTaskEntity::id).distinct().size == tasks.size) {
            "Task IDs must be unique within a requested group snapshot"
        }
    }

    private suspend fun requireTaskIdAvailable(task: CachedGroupTaskEntity) {
        val existingGroupId = findTaskGroupId(task.id)
        require(existingGroupId == null || existingGroupId == task.groupId) {
            "Task ID ${task.id} already belongs to another group"
        }
    }

    private suspend fun requireTaskIdsAvailable(tasks: List<CachedGroupTaskEntity>) {
        require(tasks.map(CachedGroupTaskEntity::id).distinct().size == tasks.size) {
            "Task IDs must be unique within a requested snapshot"
        }
        for (task in tasks) {
            requireTaskIdAvailable(task)
        }
    }

    private fun requireTaskReminders(reminders: List<CachedGroupTaskReminderEntity>) {
        require(reminders.all { it.offsetSeconds > 0 }) {
            "Every task reminder offset must be positive"
        }
        require(
            reminders.groupBy(CachedGroupTaskReminderEntity::taskId).values.all { rows ->
                rows.map(CachedGroupTaskReminderEntity::offsetSeconds).distinct().size == rows.size
            }
        ) {
            "Task reminder offsets must be unique"
        }
    }
}
