package com.smartreminder.data.remote.collaboration

import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand

/** Narrow remote boundary used by the repository and unit-test fakes. */
interface CollaborationRemoteDataSource {
    suspend fun fetchGroups(): List<CollaborationGroupRemoteDto>
    suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto?
    suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto>
    suspend fun fetchInvites(): List<CollaborationInviteRemoteDto>

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
}
