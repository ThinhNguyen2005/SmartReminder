package com.smartreminder.data.remote.collaboration

import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.data.remote.MissingSupabaseConfigurationException
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.CancelGroupTaskCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CompleteGroupTaskCommand
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.EditGroupTaskCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.ReassignGroupTaskCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.ReopenGroupTaskCommand
import com.smartreminder.domain.repository.StartGroupTaskCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Narrow RPC transport seam for exact request tests without a live Supabase client. */
fun interface CollaborationRpcInvoker {
    suspend fun invoke(
        function: String,
        parameters: JsonObject
    ): CollaborationMutationEnvelopeRemoteDto
}

/** PostgREST/RPC implementation; no Supabase type escapes this data boundary. */
class SupabaseCollaborationRemoteDataSource private constructor(
    private val supabaseProvider: (() -> SupabaseClient)?,
    private val rpcInvoker: CollaborationRpcInvoker
) : CollaborationRemoteDataSource {

    constructor(supabase: SupabaseClient) : this(
        supabaseProvider = { supabase },
        rpcInvoker = CollaborationRpcInvoker { function, parameters ->
            supabase.postgrest.rpc(function, parameters).decodeAs()
        }
    )

    /** Provider-backed constructor keeps missing configuration out of app startup. */
    constructor(supabaseProvider: () -> SupabaseClient) : this(
        supabaseProvider = supabaseProvider,
        rpcInvoker = CollaborationRpcInvoker { function, parameters ->
            supabaseProvider().postgrest.rpc(function, parameters).decodeAs()
        }
    )

    constructor(rpcInvoker: CollaborationRpcInvoker) : this(
        supabaseProvider = null,
        rpcInvoker = rpcInvoker
    )

    private val supabase: SupabaseClient
        get() = supabaseProvider?.invoke()
            ?: throw MissingSupabaseConfigurationException(
                "A Supabase client is required for collaboration PostgREST reads"
            )

    override suspend fun fetchGroups(): List<CollaborationGroupRemoteDto> =
        supabase.from(GROUPS_TABLE).select().decodeList()

    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? =
        supabase.from(GROUPS_TABLE).select {
            filter { eq("id", groupId) }
        }.decodeSingleOrNull()

    override suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto> {
        val members = supabase.from(MEMBERS_TABLE).select {
            filter { eq("group_id", groupId) }
        }.decodeList<CollaborationMemberRemoteDto>()
        if (members.isEmpty()) return emptyList()

        // The profile query is RLS-scoped to the same active groups as the member query.
        val profiles = supabase.from(PROFILES_TABLE).select().decodeList<UserProfileRemoteDto>()
        val profilesById = profiles.associateBy(UserProfileRemoteDto::userId)
        return members.map { it.copy(profile = profilesById[it.userId]) }
    }

    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> =
        supabase.from(INVITES_TABLE).select().decodeList()

    override suspend fun fetchTasks(groupId: String): List<CollaborationTaskRemoteDto> =
        supabase.from(TASKS_TABLE).select {
            filter { eq("group_id", groupId) }
        }.decodeList<CollaborationTaskRemoteDto>()
            .also { tasks -> tasks.forEach { requireTaskGroup(it, groupId) } }

    override suspend fun fetchTask(
        groupId: String,
        taskId: String
    ): CollaborationTaskRemoteDto? =
        supabase.from(TASKS_TABLE).select {
            filter {
                eq("group_id", groupId)
                eq("id", taskId)
            }
        }.decodeSingleOrNull<CollaborationTaskRemoteDto>()
            ?.also { requireTaskGroup(it, groupId) }

    /**
     * The reminder table has no group column. Verify the parent through the
     * same group-scoped task query before reading child rows, so a caller can
     * never use this boundary to fetch another group's offsets.
     */
    override suspend fun fetchTaskReminders(
        groupId: String,
        taskId: String
    ): List<CollaborationTaskReminderRemoteDto> {
        if (fetchTask(groupId, taskId) == null) return emptyList()
        return fetchTaskRemindersForKnownTask(taskId)
    }

    override suspend fun fetchTaskDetails(
        groupId: String,
        taskId: String
    ): CollaborationTaskDetailsRemoteDto? {
        val task = fetchTask(groupId, taskId) ?: return null
        return CollaborationTaskDetailsRemoteDto(
            task = task,
            reminders = fetchTaskRemindersForKnownTask(task.id)
        )
    }

    override suspend fun fetchTaskDetails(groupId: String): List<CollaborationTaskDetailsRemoteDto> =
        fetchTasks(groupId).map { task ->
            CollaborationTaskDetailsRemoteDto(
                task = task,
                reminders = fetchTaskRemindersForKnownTask(task.id)
            )
        }

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(CREATE_GROUP_RPC, buildJsonObject {
            put("p_name", command.name)
            command.description?.let { put("p_description", it) }
        })

    override suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(UPDATE_GROUP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_name", command.name)
            command.description?.let { put("p_description", it) }
        })

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(INVITE_MEMBER_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_invitee_email", command.email)
        })

    override suspend fun acceptInvite(command: AcceptInviteCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(RESPOND_INVITE_RPC, buildJsonObject {
            put("p_invite_id", command.inviteId.value)
            put("p_accept", true)
        })

    override suspend fun declineInvite(command: DeclineInviteCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(RESPOND_INVITE_RPC, buildJsonObject {
            put("p_invite_id", command.inviteId.value)
            put("p_accept", false)
        })

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(CHANGE_MEMBER_ROLE_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_member_id", command.memberId.value)
            put("p_role", command.newRole.name)
        })

    override suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(REMOVE_MEMBER_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_member_id", command.memberId.value)
        })

    override suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(TRANSFER_OWNERSHIP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_new_owner_id", command.newOwnerId.value)
        })

    override suspend fun leaveGroup(command: LeaveGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(LEAVE_GROUP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
        })

    override suspend fun deleteGroup(command: DeleteGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(DELETE_GROUP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
        })

    override suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(CREATE_TASK_RPC, buildJsonObject {
            put("p_task_id", command.taskId.value)
            put("p_group_id", command.groupId.value)
            put("p_title", command.title)
            put("p_assignee_id", command.assigneeId.value)
            put("p_due_at", command.dueAt.toString())
            put("p_reminder_offsets_seconds", JsonArray(command.reminderOffsetsSeconds.map(::JsonPrimitive)))
            command.description?.let { put("p_description", it) }
        })

    override suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(EDIT_TASK_RPC, buildJsonObject {
            put("p_task_id", command.taskId.value)
            put("p_title", command.title)
            put("p_assignee_id", command.assigneeId.value)
            put("p_due_at", command.dueAt.toString())
            put("p_reminder_offsets_seconds", JsonArray(command.reminderOffsetsSeconds.map(::JsonPrimitive)))
            put("p_expected_version", command.expectedVersion)
            command.description?.let { put("p_description", it) }
        })

    override suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(REASSIGN_TASK_RPC, buildJsonObject {
            put("p_task_id", command.taskId.value)
            put("p_assignee_id", command.assigneeId.value)
            put("p_expected_version", command.expectedVersion)
        })

    override suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(START_TASK_RPC, taskStatusParameters(command.taskId.value, command.expectedVersion))

    override suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(COMPLETE_TASK_RPC, taskStatusParameters(command.taskId.value, command.expectedVersion))

    override suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(CANCEL_TASK_RPC, taskStatusParameters(command.taskId.value, command.expectedVersion))

    override suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(REOPEN_TASK_RPC, taskStatusParameters(command.taskId.value, command.expectedVersion))

    private suspend fun rpc(
        function: String,
        parameters: JsonObject
    ): CollaborationMutationEnvelopeRemoteDto =
        rpcInvoker.invoke(function, parameters)

    private suspend fun fetchTaskRemindersForKnownTask(
        taskId: String
    ): List<CollaborationTaskReminderRemoteDto> =
        supabase.from(TASK_REMINDERS_TABLE).select {
            filter { eq("task_id", taskId) }
        }.decodeList<CollaborationTaskReminderRemoteDto>()
            .also { reminders ->
                reminders.forEach { reminder ->
                    if (reminder.taskId != taskId) {
                        throw CollaborationMappingException(
                            "Reminder ${reminder.taskId} does not belong to task $taskId"
                        )
                    }
                }
            }
            .sortedWith(
                compareBy<CollaborationTaskReminderRemoteDto> { it.offsetSeconds }
                    .thenBy { it.taskId }
            )

    private fun requireTaskGroup(task: CollaborationTaskRemoteDto, groupId: String) {
        if (task.groupId != groupId) {
            throw CollaborationMappingException(
                "Task ${task.id} belongs to group ${task.groupId}, not $groupId"
            )
        }
    }

    private fun taskStatusParameters(taskId: String, expectedVersion: Long): JsonObject =
        buildJsonObject {
            put("p_task_id", taskId)
            put("p_expected_version", expectedVersion)
        }

    companion object {
        const val GROUPS_TABLE = "collaboration_groups"
        const val MEMBERS_TABLE = "group_members"
        const val PROFILES_TABLE = "user_profiles"
        const val INVITES_TABLE = "group_invites"
        const val TASKS_TABLE = "group_tasks"
        const val TASK_REMINDERS_TABLE = "group_task_reminders"

        const val CREATE_GROUP_RPC = "create_collaboration_group"
        const val UPDATE_GROUP_RPC = "update_collaboration_group"
        const val INVITE_MEMBER_RPC = "invite_group_member"
        const val RESPOND_INVITE_RPC = "respond_group_invite"
        const val CHANGE_MEMBER_ROLE_RPC = "change_group_member_role"
        const val REMOVE_MEMBER_RPC = "remove_group_member"
        const val TRANSFER_OWNERSHIP_RPC = "transfer_group_ownership"
        const val LEAVE_GROUP_RPC = "leave_collaboration_group"
        const val DELETE_GROUP_RPC = "delete_collaboration_group"
        const val CREATE_TASK_RPC = "create_group_task"
        const val EDIT_TASK_RPC = "edit_group_task"
        const val REASSIGN_TASK_RPC = "reassign_group_task"
        const val START_TASK_RPC = "start_group_task"
        const val COMPLETE_TASK_RPC = "complete_group_task"
        const val CANCEL_TASK_RPC = "cancel_group_task"
        const val REOPEN_TASK_RPC = "reopen_group_task"

        fun configured(): SupabaseCollaborationRemoteDataSource =
            SupabaseCollaborationRemoteDataSource { SupabaseManager.client }
    }
}
