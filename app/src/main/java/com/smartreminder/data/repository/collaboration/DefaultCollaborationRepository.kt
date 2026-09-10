package com.smartreminder.data.repository.collaboration

import com.smartreminder.data.remote.collaboration.CollaborationMutationEnvelopeRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMappingException
import com.smartreminder.data.remote.collaboration.CollaborationRemoteDataSource
import com.smartreminder.data.remote.collaboration.CollaborationRemoteMapper
import com.smartreminder.data.remote.MissingSupabaseConfigurationException
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.EditOwnGroupTaskContentCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cache-first repository for the G2 group membership surface.
 *
 * Remote mutations are deliberately online-only. A successful RPC is reported as
 * applied even if its best-effort cache refresh is unavailable; the previous cache
 * remains intact and the next refresh can reconcile it.
 */
class DefaultCollaborationRepository(
    private val cache: CollaborationCacheDataSource,
    private val remote: CollaborationRemoteDataSource,
    private val network: () -> Boolean,
    private val getCurrentUserId: () -> UserId? = { null }
) : CollaborationRepository {

    /** Compatibility overload for the original cache/remote/network constructor. */
    constructor(
        cache: CollaborationCacheDataSource,
        remote: CollaborationRemoteDataSource,
        network: () -> Boolean
    ) : this(cache, remote, network, { null })

    override fun currentUserId(): UserId? = getCurrentUserId()

    override fun observeGroups(): Flow<List<CollaborationGroup>> =
        cache.observeGroups().map { groups -> groups.map(CollaborationRemoteMapper::fromCache) }

    override fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?> =
        cache.observeGroup(groupId.value).map { it?.let(CollaborationRemoteMapper::fromCache) }

    override fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>> =
        cache.observeMembers(groupId.value).map { members ->
            members.map(CollaborationRemoteMapper::fromCache)
        }

    override fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>> =
        cache.observeTasks(groupId.value).map { tasks ->
            tasks.map(CollaborationRemoteMapper::fromCache)
        }

    override fun observeInvites(): Flow<List<GroupInvite>> =
        cache.observeInvites().map { invites -> invites.map(CollaborationRemoteMapper::fromCache) }

    override suspend fun refreshGroups(): CollaborationMutationResult = try {
        val groups = remote.fetchGroups()
        cache.replaceGroups(groups.map(CollaborationRemoteMapper::toCache))
        CollaborationMutationResult.Applied
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        networkFailure(failure)
    }

    override suspend fun refreshGroup(groupId: CollaborationGroupId): CollaborationMutationResult = try {
        val group = remote.fetchGroup(groupId.value)
        if (group == null) {
            cache.removeGroup(groupId.value)
            CollaborationMutationResult.Failure(CollaborationError.NotFound)
        } else {
            val members = remote.fetchMembers(groupId.value)
            cache.replaceGroup(
                group = CollaborationRemoteMapper.toCache(group),
                members = members.map(CollaborationRemoteMapper::toCache)
            )
            CollaborationMutationResult.Applied
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        networkFailure(failure)
    }

    override suspend fun refreshInvites(): CollaborationMutationResult = try {
        val invites = remote.fetchInvites()
        cache.replaceInvites(invites.map(CollaborationRemoteMapper::toCache))
        CollaborationMutationResult.Applied
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        networkFailure(failure)
    }

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.createGroup(command) },
            resultMapper = CollaborationRemoteMapper::toCreateGroupMutationResult,
            afterApplied = { refreshGroups() }
        )

    override suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.updateGroup(command) },
            afterApplied = { refreshGroupAndList(command.groupId) }
        )

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.inviteMember(command) },
            afterApplied = { refreshInvites(); refreshGroupAndList(command.groupId) }
        )

    override suspend fun acceptInvite(inviteId: GroupInviteId): CollaborationMutationResult =
        executeMutation(
            action = { remote.acceptInvite(AcceptInviteCommand(inviteId)) },
            afterApplied = { envelope -> refreshInvitesAndAffectedGroup(envelope) }
        )

    override suspend fun declineInvite(inviteId: GroupInviteId): CollaborationMutationResult =
        executeMutation(
            action = { remote.declineInvite(DeclineInviteCommand(inviteId)) },
            afterApplied = { envelope -> refreshInvitesAndAffectedGroup(envelope) }
        )

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.changeMemberRole(command) },
            afterApplied = { refreshGroupAndList(command.groupId) }
        )

    override suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.removeMember(command) },
            afterApplied = { refreshGroupAndList(command.groupId) }
        )

    override suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.transferOwnership(command) },
            afterApplied = {
                refreshGroupAndList(command.groupId)
                refreshInvites()
            }
        )

    override suspend fun leaveGroup(groupId: CollaborationGroupId): CollaborationMutationResult =
        executeMutation(
            action = { remote.leaveGroup(LeaveGroupCommand(groupId)) },
            afterApplied = {
                refreshGroups()
                refreshInvites()
            }
        )

    override suspend fun deleteGroup(groupId: CollaborationGroupId): CollaborationMutationResult =
        executeMutation(
            action = { remote.deleteGroup(DeleteGroupCommand(groupId)) },
            afterApplied = {
                refreshGroups()
                refreshInvites()
            }
        )

    override suspend fun createTask(command: com.smartreminder.domain.repository.CreateGroupTaskCommand): CollaborationMutationResult =
        unsupportedTaskMutation()

    override suspend fun startTask(taskId: GroupTaskId): CollaborationMutationResult =
        unsupportedTaskMutation()

    override suspend fun completeTask(taskId: GroupTaskId): CollaborationMutationResult =
        unsupportedTaskMutation()

    override suspend fun editOwnTaskContent(command: EditOwnGroupTaskContentCommand): CollaborationMutationResult =
        unsupportedTaskMutation()

    private suspend fun executeMutation(
        action: suspend () -> CollaborationMutationEnvelopeRemoteDto,
        resultMapper: (CollaborationMutationEnvelopeRemoteDto) -> CollaborationMutationResult =
            CollaborationRemoteMapper::toMutationResult,
        afterApplied: suspend (CollaborationMutationEnvelopeRemoteDto) -> Unit = {}
    ): CollaborationMutationResult {
        val networkAvailable = try {
            network()
        } catch (_: SecurityException) {
            // A missing ACCESS_NETWORK_STATE permission must not prevent the remote attempt.
            // Transport/configuration failures are still mapped below to a typed result.
            true
        }
        if (!networkAvailable) return CollaborationMutationResult.NetworkRequired

        return try {
            val envelope = action()
            val result = resultMapper(envelope)
            if (result === CollaborationMutationResult.Applied ||
                result is CollaborationMutationResult.Created
            ) {
                try {
                    afterApplied(envelope)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The RPC already applied. Keep the truthful result and retain cache rows.
                }
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            networkFailure(failure)
        }
    }

    private suspend fun refreshGroupAndList(groupId: CollaborationGroupId) {
        refreshGroups()
        refreshGroup(groupId)
    }

    private suspend fun refreshInvitesAndAffectedGroup(
        envelope: CollaborationMutationEnvelopeRemoteDto
    ) {
        refreshInvites()
        val groupId = envelope.data["group_id"]?.jsonPrimitive?.contentOrNull
        if (!groupId.isNullOrBlank()) {
            refreshGroupAndList(CollaborationGroupId(groupId))
        } else {
            refreshGroups()
        }
    }

    private fun networkFailure(failure: Exception): CollaborationMutationResult {
        val error = when (failure) {
            is MissingSupabaseConfigurationException -> CollaborationError.ConfigurationMissing
            is CollaborationMappingException,
            is SerializationException -> CollaborationError.MappingFailure(
                message = failure.message ?: "Invalid collaboration response",
                cause = failure
            )

            else -> CollaborationError.NetworkUnavailable(failure)
        }
        return CollaborationMutationResult.Failure(error)
    }

    private fun unsupportedTaskMutation(): Nothing =
        throw UnsupportedOperationException("Group task mutations are scheduled for G3")
}
