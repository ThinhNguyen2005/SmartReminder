package com.smartreminder.data.remote.collaboration

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

/** Narrow remote boundary used by the repository and unit-test fakes. */
interface CollaborationRemoteDataSource {
    suspend fun fetchGroups(): List<CollaborationGroupRemoteDto>
    suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto?
    suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto>
    suspend fun fetchInvites(): List<CollaborationInviteRemoteDto>

    /**
     * Reads are group-scoped at the transport boundary. Implementations must
     * never widen these queries to the caller's other groups.
     *
     * Defaults keep existing test fakes source-compatible while the task
     * surface is introduced; the Supabase implementation overrides every
     * operation below.
     */
    suspend fun fetchTasks(groupId: String): List<CollaborationTaskRemoteDto> =
        unsupportedTaskRemoteOperation()

    suspend fun fetchTask(groupId: String, taskId: String): CollaborationTaskRemoteDto? =
        unsupportedTaskRemoteOperation()

    suspend fun fetchTaskReminders(
        groupId: String,
        taskId: String
    ): List<CollaborationTaskReminderRemoteDto> = unsupportedTaskRemoteOperation()

    suspend fun fetchTaskDetails(
        groupId: String,
        taskId: String
    ): CollaborationTaskDetailsRemoteDto? {
        val task = fetchTask(groupId, taskId) ?: return null
        return CollaborationTaskDetailsRemoteDto(task, fetchTaskReminders(groupId, taskId))
    }

    suspend fun fetchTaskDetails(groupId: String): List<CollaborationTaskDetailsRemoteDto> =
        fetchTasks(groupId).map { task ->
            CollaborationTaskDetailsRemoteDto(
                task = task,
                reminders = fetchTaskReminders(groupId, task.id)
            )
        }

    suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun acceptInvite(command: AcceptInviteCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun declineInvite(command: DeclineInviteCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun leaveGroup(command: LeaveGroupCommand): CollaborationMutationEnvelopeRemoteDto
    suspend fun deleteGroup(command: DeleteGroupCommand): CollaborationMutationEnvelopeRemoteDto

    suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        unsupportedTaskRemoteOperation()

    suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        unsupportedTaskRemoteOperation()

    suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        unsupportedTaskRemoteOperation()

    suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        unsupportedTaskRemoteOperation()

    suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        unsupportedTaskRemoteOperation()

    suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        unsupportedTaskRemoteOperation()

    suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        unsupportedTaskRemoteOperation()
}

private fun unsupportedTaskRemoteOperation(): Nothing =
    throw UnsupportedOperationException("Group task remote operations are not implemented")
