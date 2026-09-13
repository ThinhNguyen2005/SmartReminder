package com.smartreminder.data.repository.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.mapper.GroupTaskMapper
import com.smartreminder.data.remote.collaboration.CollaborationMutationEnvelopeRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMappingException
import com.smartreminder.data.remote.collaboration.CollaborationRemoteDataSource
import com.smartreminder.data.remote.collaboration.CollaborationRemoteMapper
import com.smartreminder.data.remote.collaboration.CollaborationTaskDetailsRemoteDto
import com.smartreminder.data.remote.MissingSupabaseConfigurationException
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
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.repository.CancelGroupTaskCommand
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import io.github.jan.supabase.exceptions.RestException

/**
 * Cache-first repository for the G2 membership and G3 task surfaces.
 *
 * Remote mutations are deliberately online-only. A successful RPC is reported as
 * applied even if its best-effort cache refresh is unavailable; the previous cache
 * remains intact and the next refresh can reconcile it. G3 task mutations never
 * enqueue or project local state; the server response is the only mutation result.
 */
class DefaultCollaborationRepository(
    private val cache: CollaborationCacheDataSource,
    private val remote: CollaborationRemoteDataSource,
    private val network: () -> Boolean,
    private val getCurrentUserId: () -> UserId? = { null },
    private val observeUserId: () -> Flow<UserId?> = { flowOf(getCurrentUserId()) }
) : CollaborationRepository {

    /** Compatibility overload for the original cache/remote/network constructor. */
    constructor(
        cache: CollaborationCacheDataSource,
        remote: CollaborationRemoteDataSource,
        network: () -> Boolean
    ) : this(cache, remote, network, { null })

    override fun currentUserId(): UserId? = getCurrentUserId()

    override fun observeCurrentUserId(): Flow<UserId?> = observeUserId().distinctUntilChanged()

    private val cacheWriteMutex = Mutex()
    private var cacheGeneration = 0L

    override suspend fun clearSessionCache() {
        cacheWriteMutex.withLock {
            cacheGeneration += 1
            cache.clearAll()
        }
    }

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

    override fun observeTaskDetails(groupId: CollaborationGroupId): Flow<List<GroupTaskDetails>> =
        cache.observeTaskDetails(groupId.value).map { details ->
            GroupTaskMapper.toDetailsDomain(details)
        }

    override fun observeInvites(): Flow<List<GroupInvite>> =
        cache.observeInvites().map { invites -> invites.map(CollaborationRemoteMapper::fromCache) }

    override suspend fun refreshGroups(): CollaborationMutationResult {
        val generation = currentCacheGeneration()
        return try {
            val groups = remote.fetchGroups()
            writeCacheIfCurrent(generation) {
                cache.replaceGroups(groups.map(CollaborationRemoteMapper::toCache))
            }
            CollaborationMutationResult.Applied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val result = networkFailure(failure)
            if (result.isAccessLoss()) clearSessionCacheIfCurrent(generation)
            result
        }
    }

    override suspend fun refreshGroup(groupId: CollaborationGroupId): CollaborationMutationResult {
        val generation = currentCacheGeneration()
        return try {
            val group = remote.fetchGroup(groupId.value)
            if (group == null) {
                evictGroupIfCurrent(generation, groupId)
                CollaborationMutationResult.Failure(CollaborationError.NotFound)
            } else {
                val members = remote.fetchMembers(groupId.value)
                writeCacheIfCurrent(generation) {
                    cache.replaceGroup(
                        group = CollaborationRemoteMapper.toCache(group),
                        members = members.map(CollaborationRemoteMapper::toCache)
                    )
                }
                CollaborationMutationResult.Applied
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val result = networkFailure(failure)
            if (result.isAccessLoss()) evictGroupIfCurrent(generation, groupId)
            result
        }
    }

    override suspend fun refreshTasks(groupId: CollaborationGroupId): CollaborationMutationResult {
        val generation = currentCacheGeneration()
        return try {
            val remoteDetails = remote.fetchTaskDetails(groupId.value)
            val snapshot = toTaskCacheSnapshot(groupId, remoteDetails)
            writeCacheIfCurrent(generation) {
                cache.replaceTasks(
                    groupId = groupId.value,
                    tasks = snapshot.tasks,
                    reminders = snapshot.reminders
                )
            }
            CollaborationMutationResult.Applied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val result = networkFailure(failure)
            if (result.isAccessLoss()) evictTasksIfCurrent(generation, groupId)
            result
        }
    }

    override suspend fun refreshInvites(): CollaborationMutationResult {
        val generation = currentCacheGeneration()
        return try {
            val invites = remote.fetchInvites()
            writeCacheIfCurrent(generation) {
                cache.replaceInvites(invites.map(CollaborationRemoteMapper::toCache))
            }
            CollaborationMutationResult.Applied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val result = networkFailure(failure)
            if (result.isAccessLoss()) clearSessionCacheIfCurrent(generation)
            result
        }
    }

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.createGroup(command) },
            resultMapper = CollaborationRemoteMapper::toCreateGroupMutationResult,
            afterApplied = { envelope ->
                refreshGroups()
                envelope.groupIdOrNull()?.let { createdGroupId -> refreshGroup(createdGroupId) }
            }
        )

    override suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.updateGroup(command) },
            afterApplied = { refreshGroupAndList(command.groupId) },
            affectedGroupId = command.groupId
        )

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.inviteMember(command) },
            afterApplied = { refreshInvites(); refreshGroupAndList(command.groupId) },
            affectedGroupId = command.groupId
        )

    override suspend fun acceptInvite(inviteId: GroupInviteId): CollaborationMutationResult =
        executeMutation(
            action = { remote.acceptInvite(AcceptInviteCommand(inviteId)) },
            afterApplied = { envelope ->
                evictInvite(inviteId)
                refreshInvitesAndAffectedGroup(envelope)
            },
            affectedInviteId = inviteId
        )

    override suspend fun declineInvite(inviteId: GroupInviteId): CollaborationMutationResult =
        executeMutation(
            action = { remote.declineInvite(DeclineInviteCommand(inviteId)) },
            afterApplied = { envelope ->
                evictInvite(inviteId)
                refreshInvitesAndAffectedGroup(envelope)
            },
            affectedInviteId = inviteId
        )

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.changeMemberRole(command) },
            afterApplied = { refreshGroupAndList(command.groupId) },
            affectedGroupId = command.groupId
        )

    override suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.removeMember(command) },
            afterApplied = { refreshGroupAndList(command.groupId) },
            affectedGroupId = command.groupId
        )

    override suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationResult =
        executeMutation(
            action = { remote.transferOwnership(command) },
            afterApplied = {
                refreshGroupAndList(command.groupId)
                refreshInvites()
            },
            affectedGroupId = command.groupId
        )

    override suspend fun leaveGroup(groupId: CollaborationGroupId): CollaborationMutationResult =
        executeMutation(
            action = { remote.leaveGroup(LeaveGroupCommand(groupId)) },
            afterApplied = {
                evictGroup(groupId)
                refreshGroups()
                refreshInvites()
            },
            affectedGroupId = groupId
        )

    override suspend fun deleteGroup(groupId: CollaborationGroupId): CollaborationMutationResult =
        executeMutation(
            action = { remote.deleteGroup(DeleteGroupCommand(groupId)) },
            afterApplied = {
                evictGroup(groupId)
                refreshGroups()
                refreshInvites()
            },
            affectedGroupId = groupId
        )

    override suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationResult =
        executeTaskMutation(
            action = { remote.createTask(command) },
            affectedTaskGroupId = command.groupId,
            affectedTaskId = command.taskId
        )

    override suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationResult =
        executeTaskMutation(
            action = { remote.editTask(command) },
            affectedTaskId = command.taskId
        )

    override suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationResult =
        executeTaskMutation(
            action = { remote.reassignTask(command) },
            affectedTaskId = command.taskId
        )

    override suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationResult =
        executeTaskMutation(
            action = { remote.startTask(command) },
            affectedTaskId = command.taskId
        )

    override suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationResult =
        executeTaskMutation(
            action = { remote.completeTask(command) },
            affectedTaskId = command.taskId
        )

    override suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationResult =
        executeTaskMutation(
            action = { remote.cancelTask(command) },
            affectedTaskId = command.taskId
        )

    override suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationResult =
        executeTaskMutation(
            action = { remote.reopenTask(command) },
            affectedTaskId = command.taskId
        )

    private suspend fun executeMutation(
        action: suspend () -> CollaborationMutationEnvelopeRemoteDto,
        resultMapper: (CollaborationMutationEnvelopeRemoteDto) -> CollaborationMutationResult =
            CollaborationRemoteMapper::toG2MutationResult,
        afterApplied: suspend (CollaborationMutationEnvelopeRemoteDto) -> Unit = {},
        afterConflict: suspend (CollaborationMutationEnvelopeRemoteDto) -> Unit = {},
        affectedGroupId: CollaborationGroupId? = null,
        affectedInviteId: GroupInviteId? = null,
        affectedTaskGroupId: CollaborationGroupId? = null,
        affectedTaskId: GroupTaskId? = null
    ): CollaborationMutationResult {
        val networkAvailable = try {
            network()
        } catch (_: SecurityException) {
            // A missing ACCESS_NETWORK_STATE permission must not prevent the remote attempt.
            // Transport/configuration failures are still mapped below to a typed result.
            true
        }
        if (!networkAvailable) return CollaborationMutationResult.NetworkRequired

        val mutationGeneration = beginMutationBoundary()

        return try {
            val envelope = action()
            val result = resultMapper(envelope)
            val completionGeneration = finishMutationBoundary(mutationGeneration)
            if (completionGeneration != null) {
                val taskGroupId = if (result.isAccessLoss()) {
                    resolveTaskGroupIdOrNull(affectedTaskId, envelope, affectedTaskGroupId)
                } else {
                    null
                }
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
                } else if (result.isConflict()) {
                    try {
                        afterConflict(envelope)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A conflict is authoritative even when its follow-up refresh is unavailable.
                        // The prior cache remains available for an explicit retry.
                    }
                } else if (result.isAccessLoss()) {
                    redactMutationCacheIfCurrent(
                        generation = completionGeneration,
                        groupId = affectedGroupId,
                        inviteId = affectedInviteId,
                        taskGroupId = taskGroupId
                    )
                }
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val result = networkFailure(failure)
            val completionGeneration = finishMutationBoundary(mutationGeneration)
            if (completionGeneration != null && result.isAccessLoss()) {
                val taskGroupId = resolveTaskGroupIdOrNull(
                    taskId = affectedTaskId,
                    envelope = null,
                    knownGroupId = affectedTaskGroupId
                )
                redactMutationCacheIfCurrent(
                    generation = completionGeneration,
                    groupId = affectedGroupId,
                    inviteId = affectedInviteId,
                    taskGroupId = taskGroupId
                )
            }
            result
        }
    }

    private suspend fun executeTaskMutation(
        action: suspend () -> CollaborationMutationEnvelopeRemoteDto,
        affectedTaskGroupId: CollaborationGroupId? = null,
        affectedTaskId: GroupTaskId
    ): CollaborationMutationResult = executeMutation(
        action = action,
        resultMapper = CollaborationRemoteMapper::toTaskMutationResult,
        afterApplied = { envelope ->
            resolveTaskGroupIdOrNull(affectedTaskId, envelope, affectedTaskGroupId)
                ?.let { refreshTasks(it) }
        },
        afterConflict = { envelope ->
            resolveTaskGroupIdOrNull(affectedTaskId, envelope, affectedTaskGroupId)
                ?.let { refreshTasks(it) }
        },
        affectedTaskGroupId = affectedTaskGroupId,
        affectedTaskId = affectedTaskId
    )

    private suspend fun refreshGroupAndList(groupId: CollaborationGroupId) {
        refreshGroups()
        refreshGroup(groupId)
    }

    private data class TaskCacheSnapshot(
        val tasks: List<CachedGroupTaskEntity>,
        val reminders: List<CachedGroupTaskReminderEntity>
    )

    private fun toTaskCacheSnapshot(
        groupId: CollaborationGroupId,
        remoteDetails: List<CollaborationTaskDetailsRemoteDto>
    ): TaskCacheSnapshot {
        remoteDetails.forEach { details ->
            if (details.task.groupId != groupId.value) {
                throw CollaborationMappingException(
                    "Task ${details.task.id} belongs to group ${details.task.groupId}, not ${groupId.value}"
                )
            }
        }

        val domainDetails = CollaborationRemoteMapper.toDetailsDomain(remoteDetails)
        if (domainDetails.map { it.task.id }.distinct().size != domainDetails.size) {
            throw CollaborationMappingException(
                "Duplicate collaboration task ids in group ${groupId.value} snapshot"
            )
        }

        val cacheRelations = domainDetails.map(GroupTaskMapper::toEntity)
        return TaskCacheSnapshot(
            tasks = cacheRelations.map { it.task },
            reminders = cacheRelations.flatMap { it.reminders }
        )
    }

    private suspend fun resolveTaskGroupId(
        taskId: GroupTaskId,
        envelope: CollaborationMutationEnvelopeRemoteDto?,
        knownGroupId: CollaborationGroupId? = null
    ): CollaborationGroupId? = knownGroupId
        ?: envelope?.groupIdOrNull()
        ?: cache.findTaskGroupId(taskId.value)
            ?.takeIf(String::isNotBlank)
            ?.let(::CollaborationGroupId)

    private suspend fun resolveTaskGroupIdOrNull(
        taskId: GroupTaskId?,
        envelope: CollaborationMutationEnvelopeRemoteDto?,
        knownGroupId: CollaborationGroupId? = null
    ): CollaborationGroupId? {
        return try {
            if (taskId == null) {
                knownGroupId ?: envelope?.groupIdOrNull()
            } else {
                resolveTaskGroupId(taskId, envelope, knownGroupId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
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

            is RestException -> when (failure.statusCode) {
                401, 403 -> CollaborationError.NotAuthorized
                404 -> CollaborationError.NotFound
                else -> CollaborationError.NetworkUnavailable(failure)
            }

            else -> CollaborationError.NetworkUnavailable(failure)
        }
        return CollaborationMutationResult.Failure(error)
    }

    private suspend fun currentCacheGeneration(): Long = cacheWriteMutex.withLock { cacheGeneration }

    private suspend fun beginMutationBoundary(): Long = cacheWriteMutex.withLock {
        cacheGeneration += 1
        cacheGeneration
    }

    /**
     * Closes the RPC window before any follow-up refresh starts. A session switch
     * or newer mutation wins the generation and suppresses this mutation's cache
     * side effects, while a refresh started during the RPC becomes stale.
     */
    private suspend fun finishMutationBoundary(startGeneration: Long): Long? =
        cacheWriteMutex.withLock {
            if (cacheGeneration != startGeneration) {
                null
            } else {
                cacheGeneration += 1
                cacheGeneration
            }
        }

    private suspend fun writeCacheIfCurrent(generation: Long, write: suspend () -> Unit) {
        cacheWriteMutex.withLock {
            if (cacheGeneration == generation) write()
        }
    }

    private suspend fun clearSessionCacheIfCurrent(generation: Long) {
        cacheWriteMutex.withLock {
            if (cacheGeneration == generation) {
                cacheGeneration += 1
                cache.clearAll()
            }
        }
    }

    private suspend fun evictGroupIfCurrent(generation: Long, groupId: CollaborationGroupId) {
        cacheWriteMutex.withLock {
            if (cacheGeneration == generation) {
                cacheGeneration += 1
                cache.removeGroup(groupId.value)
                cache.removeInvitesForGroup(groupId.value)
            }
        }
    }

    private suspend fun evictGroup(groupId: CollaborationGroupId) {
        cacheWriteMutex.withLock {
            cacheGeneration += 1
            cache.removeGroup(groupId.value)
            cache.removeInvitesForGroup(groupId.value)
        }
    }

    private suspend fun evictTasksIfCurrent(generation: Long, groupId: CollaborationGroupId) {
        cacheWriteMutex.withLock {
            if (cacheGeneration == generation) {
                cacheGeneration += 1
                cache.removeTasksForGroup(groupId.value)
            }
        }
    }

    private suspend fun redactMutationCacheIfCurrent(
        generation: Long,
        groupId: CollaborationGroupId?,
        inviteId: GroupInviteId?,
        taskGroupId: CollaborationGroupId?
    ) {
        cacheWriteMutex.withLock {
            if (cacheGeneration != generation) return@withLock
            cacheGeneration += 1
            groupId?.let {
                cache.removeGroup(it.value)
                cache.removeInvitesForGroup(it.value)
            }
            inviteId?.let { cache.removeInvite(it.value) }
            taskGroupId?.let { cache.removeTasksForGroup(it.value) }
        }
    }

    private suspend fun evictInvite(inviteId: GroupInviteId) {
        cacheWriteMutex.withLock {
            cacheGeneration += 1
            cache.removeInvite(inviteId.value)
        }
    }

    private fun CollaborationMutationResult.isAccessLoss(): Boolean = when (this) {
        is CollaborationMutationResult.NotAuthorized -> true
        is CollaborationMutationResult.Failure -> error is CollaborationError.NotAuthorized ||
            error is CollaborationError.NotFound
        else -> false
    }

    private fun CollaborationMutationResult.isConflict(): Boolean =
        this is CollaborationMutationResult.Conflict ||
            (this is CollaborationMutationResult.Failure && error is CollaborationError.Conflict)

    private fun CollaborationMutationEnvelopeRemoteDto.groupIdOrNull(): CollaborationGroupId? =
        data["group_id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.let(::CollaborationGroupId)
}
