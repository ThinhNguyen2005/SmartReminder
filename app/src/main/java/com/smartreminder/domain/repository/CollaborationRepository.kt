package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import kotlinx.coroutines.flow.Flow
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
        require(title.isNotBlank()) { "title must not be blank" }
        require(reminderOffsetsSeconds.isNotEmpty()) { "at least 1 reminder offset required" }
        require(reminderOffsetsSeconds.size <= 5) { "at most 5 reminder offsets allowed" }
        require(reminderOffsetsSeconds.all { it > 0 }) { "all reminder offsets must be positive" }
        require(reminderOffsetsSeconds.distinct().size == reminderOffsetsSeconds.size) {
            "reminder offsets must be unique"
        }
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

    fun observeGroups(): Flow<List<CollaborationGroup>>
    fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?>
    fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>>
    fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>>
    fun observeInvites(): Flow<List<GroupInvite>>

    suspend fun refreshGroups(): CollaborationMutationResult
    suspend fun refreshGroup(groupId: CollaborationGroupId): CollaborationMutationResult
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
    suspend fun startTask(taskId: GroupTaskId): CollaborationMutationResult
    suspend fun completeTask(taskId: GroupTaskId): CollaborationMutationResult
    suspend fun editOwnTaskContent(command: EditOwnGroupTaskContentCommand): CollaborationMutationResult
}
