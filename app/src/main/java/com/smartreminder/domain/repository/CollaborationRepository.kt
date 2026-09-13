package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

data class CreateGroupTaskCommand(
    val taskId: GroupTaskId,
    val groupId: CollaborationGroupId,
    val title: String,
    val description: String? = null,
    val assigneeId: UserId,
    val dueAt: Instant,
    val reminderOffsetsSeconds: List<Long>
) {
    init {
        requireTaskTitle(title)
        requireTaskAssignee(assigneeId)
        requireTaskReminderOffsets(reminderOffsetsSeconds)
    }
}

data class EditOwnGroupTaskContentCommand(
    val taskId: GroupTaskId,
    val title: String,
    val description: String? = null,
    val expectedVersion: Long
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(expectedVersion >= 0) { "expectedVersion must not be negative" }
    }
}

interface CollaborationRepository {
    /** Typed actor identity supplied by the composition/data boundary. */
    fun currentUserId(): UserId? = null

    /** Session-safe identity stream; implementations emit whenever the auth account changes. */
    fun observeCurrentUserId(): Flow<UserId?> = flowOf(currentUserId())

    /** Clears all collaboration cache/state at an authenticated-session boundary. */
    suspend fun clearSessionCache() = Unit

    fun observeGroups(): Flow<List<CollaborationGroup>>
    fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?>
    fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>>
    fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>>
    fun observeTaskDetails(groupId: CollaborationGroupId): Flow<List<GroupTaskDetails>> =
        observeTasks(groupId).map { tasks -> tasks.map(::GroupTaskDetails) }

    fun observeTaskDetails(
        groupId: CollaborationGroupId,
        taskId: GroupTaskId
    ): Flow<GroupTaskDetails?> =
        observeTaskDetails(groupId).map { details ->
            details.firstOrNull { it.task.id == taskId }
        }
    fun observeInvites(): Flow<List<GroupInvite>>

    suspend fun refreshGroups(): CollaborationMutationResult
    suspend fun refreshGroup(groupId: CollaborationGroupId): CollaborationMutationResult
    suspend fun refreshTasks(groupId: CollaborationGroupId): CollaborationMutationResult =
        CollaborationMutationResult.NetworkRequired
    suspend fun refreshInvites(): CollaborationMutationResult

    suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationResult
    suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationResult
    suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationResult
    suspend fun acceptInvite(inviteId: GroupInviteId): CollaborationMutationResult
    suspend fun declineInvite(inviteId: GroupInviteId): CollaborationMutationResult
    suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationResult
    suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationResult
    suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationResult
    suspend fun leaveGroup(groupId: CollaborationGroupId): CollaborationMutationResult
    suspend fun deleteGroup(groupId: CollaborationGroupId): CollaborationMutationResult

    suspend fun createGroup(name: String, description: String? = null): CollaborationMutationResult =
        createGroup(CreateGroupCommand(name, description))

    suspend fun updateGroup(
        groupId: CollaborationGroupId,
        name: String,
        description: String? = null
    ): CollaborationMutationResult = updateGroup(UpdateGroupCommand(groupId, name, description))

    suspend fun inviteMember(
        groupId: CollaborationGroupId,
        email: String
    ): CollaborationMutationResult = inviteMember(InviteMemberCommand(groupId, email))

    suspend fun acceptInvite(command: AcceptInviteCommand): CollaborationMutationResult =
        acceptInvite(command.inviteId)

    suspend fun declineInvite(command: DeclineInviteCommand): CollaborationMutationResult =
        declineInvite(command.inviteId)

    suspend fun changeMemberRole(
        groupId: CollaborationGroupId,
        memberId: UserId,
        targetRole: GroupRole
    ): CollaborationMutationResult =
        changeMemberRole(ChangeMemberRoleCommand(groupId, memberId, targetRole))

    suspend fun removeMember(
        groupId: CollaborationGroupId,
        memberId: UserId
    ): CollaborationMutationResult = removeMember(RemoveMemberCommand(groupId, memberId))

    suspend fun transferOwnership(
        groupId: CollaborationGroupId,
        newOwnerId: UserId
    ): CollaborationMutationResult = transferOwnership(TransferOwnershipCommand(groupId, newOwnerId))

    suspend fun leaveGroup(command: LeaveGroupCommand): CollaborationMutationResult =
        leaveGroup(command.groupId)

    suspend fun deleteGroup(command: DeleteGroupCommand): CollaborationMutationResult =
        deleteGroup(command.groupId)

    suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationResult
    suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationResult
    suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationResult
    suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationResult
    suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationResult
    suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationResult
    suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationResult
}
