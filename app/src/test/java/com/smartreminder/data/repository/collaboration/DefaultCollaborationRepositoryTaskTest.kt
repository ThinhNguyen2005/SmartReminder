package com.smartreminder.data.repository.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.mapper.GroupTaskMapper
import com.smartreminder.data.local.room.relation.GroupTaskWithRemindersEntity
import com.smartreminder.data.remote.collaboration.CollaborationGroupRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationInviteRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMemberRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMutationEnvelopeRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationRemoteDataSource
import com.smartreminder.data.remote.collaboration.CollaborationTaskDetailsRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationTaskRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationTaskReminderRemoteDto
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.CancelGroupTaskCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
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
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCollaborationRepositoryTaskTest {

    private val groupId = CollaborationGroupId("group-1")
    private val taskId = GroupTaskId("task-1")
    private val secondTaskId = GroupTaskId("task-2")
    private val assigneeId = UserId("member-1")
    private val dueAt = Instant.parse("2026-09-12T11:00:00Z")

    @Test
    fun `task observations are cache first and expose aggregate reminders`() = runTest {
        val cachedDetails = cachedDetails(title = "Cached task")
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails))
        val remote = FakeTaskRemote()
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        assertEquals(
            listOf("Cached task"),
            repository.observeTasks(groupId).first().map { it.title }
        )
        assertEquals(
            listOf(60L, 300L),
            repository.observeTaskDetails(groupId).first().single().reminders
                .map { it.offsetSeconds }
        )
        assertEquals(0, remote.fetchTaskDetailsCalls)
    }

    @Test
    fun `refreshTasks maps remote aggregate and replaces cache atomically`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Old")))
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(remoteDetails(title = "Authoritative"))
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        assertEquals(CollaborationMutationResult.Applied, repository.refreshTasks(groupId))
        assertEquals(listOf(groupId.value), remote.fetchTaskDetailsGroupIds)
        assertEquals(1, cache.replaceTasksCalls)
        assertEquals(
            listOf("Authoritative"),
            repository.observeTasks(groupId).first().map { it.title }
        )
        assertEquals(
            listOf(60L, 300L),
            repository.observeTaskDetails(groupId).first().single().reminders
                .map { it.offsetSeconds }
        )
    }

    @Test
    fun `refreshTasks failure retains the previous cache`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Keep me")))
        val remote = FakeTaskRemote().apply {
            taskDetailsFailure = IllegalStateException("network down")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.refreshTasks(groupId)

        assertTrue(result is CollaborationMutationResult.Failure)
        assertTrue(
            (result as CollaborationMutationResult.Failure).error is
                CollaborationError.NetworkUnavailable
        )
        assertEquals(listOf("Keep me"), repository.observeTasks(groupId).first().map { it.title })
        assertEquals(0, cache.replaceTasksCalls)
    }

    @Test
    fun `refreshTasks mapping failure retains the previous cache`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Keep me")))
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(
                remoteDetails(title = "Malformed").copy(
                    task = remoteDetails(title = "Malformed").task.copy(status = "BROKEN")
                )
            )
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.refreshTasks(groupId)

        assertTrue(result is CollaborationMutationResult.Failure)
        assertTrue(
            (result as CollaborationMutationResult.Failure).error is
                CollaborationError.MappingFailure
        )
        assertEquals(listOf("Keep me"), repository.observeTasks(groupId).first().map { it.title })
        assertEquals(0, cache.replaceTasksCalls)
    }

    @Test
    fun `refreshTasks completing after session clear cannot repopulate a redacted cache`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Private")))
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(remoteDetails(title = "Stale account task"))
            blockNextTaskDetails()
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val refresh = async { repository.refreshTasks(groupId) }
        remote.taskDetailsStarted.await()
        repository.clearSessionCache()
        remote.taskDetailsCompletion.complete(Unit)

        assertEquals(CollaborationMutationResult.Applied, refresh.await())
        assertTrue(repository.observeTasks(groupId).first().isEmpty())
        assertEquals(0, cache.replaceTasksCalls)
    }

    @Test
    fun `refresh started while task rpc is pending cannot overwrite post mutation refresh`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Old")))
        val remote = FakeTaskRemote().apply {
            taskDetailsByCall = listOf(
                listOf(remoteDetails(title = "Old")),
                listOf(remoteDetails(title = "New"))
            )
            blockNextTaskMutation()
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val mutation = async {
            repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))
        }
        remote.taskMutationStarted.await()

        remote.blockNextTaskDetails()
        val staleRefresh = async { repository.refreshTasks(groupId) }
        remote.taskDetailsStarted.await()

        remote.taskMutationCompletion.complete(Unit)
        assertEquals(CollaborationMutationResult.Applied, mutation.await())

        remote.taskDetailsCompletion.complete(Unit)
        assertEquals(CollaborationMutationResult.Applied, staleRefresh.await())
        assertEquals(
            listOf("New"),
            repository.observeTasks(groupId).first().map { it.title }
        )
    }

    @Test
    fun `same-session unrelated mutation does not suppress task follow-up`() = runTest {
        listOf("APPLIED", "CONFLICT", "NOT_AUTHORIZED").forEach { status ->
            val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Old")))
            val remote = FakeTaskRemote().apply {
                taskDetails = listOf(remoteDetails(title = "Authoritative"))
                taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = status)
                blockNextTaskMutation()
            }
            val repository = DefaultCollaborationRepository(cache, remote) { true }

            val mutation = async {
                repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))
            }
            remote.taskMutationStarted.await()

            assertEquals(
                CollaborationMutationResult.Applied,
                repository.updateGroup(
                    UpdateGroupCommand(
                        groupId = CollaborationGroupId("unrelated-group"),
                        name = "Renamed"
                    )
                )
            )
            remote.taskMutationCompletion.complete(Unit)

            val result = mutation.await()
            when (status) {
                "APPLIED" -> assertEquals(CollaborationMutationResult.Applied, result)
                "CONFLICT" -> assertTrue(result is CollaborationMutationResult.Conflict)
                "NOT_AUTHORIZED" -> assertEquals(
                    CollaborationMutationResult.NotAuthorized(CollaborationError.NotAuthorized),
                    result
                )
            }

            if (status == "NOT_AUTHORIZED") {
                assertEquals(1, cache.removeTasksCalls)
                assertTrue(repository.observeTasks(groupId).first().isEmpty())
            } else {
                assertEquals(1, remote.fetchTaskDetailsCalls)
                assertEquals(
                    listOf("Authoritative"),
                    repository.observeTasks(groupId).first().map { it.title }
                )
            }
        }
    }

    @Test
    fun `same-session unrelated task mutation does not suppress task follow-up`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Old")))
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(remoteDetails(title = "Authoritative"))
            blockNextTaskMutation()
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val mutation = async {
            repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))
        }
        remote.taskMutationStarted.await()

        assertEquals(
            CollaborationMutationResult.Applied,
            repository.startTask(
                StartGroupTaskCommand(
                    taskId = GroupTaskId("unrelated-task"),
                    expectedVersion = 0L
                )
            )
        )
        remote.taskMutationCompletion.complete(Unit)

        assertEquals(CollaborationMutationResult.Applied, mutation.await())
        assertEquals(1, remote.fetchTaskDetailsCalls)
        assertEquals(
            listOf("Authoritative"),
            repository.observeTasks(groupId).first().map { it.title }
        )
    }

    @Test
    fun `same-group failed mutation does not discard an in-flight successful refresh`() = runTest {
        listOf("INVALID_STATE", "NETWORK", "CANCELLED").forEach { failureMode ->
            val cache = FakeTaskCache(
                cachedDetails = listOf(
                    cachedDetails(title = "Task one old"),
                    cachedDetails(title = "Task two old", taskId = secondTaskId)
                )
            )
            val remote = FakeTaskRemote().apply {
                taskDetails = listOf(
                    remoteDetails(title = "Task one new"),
                    remoteDetails(title = "Task two old", taskId = secondTaskId)
                )
                taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
            }
            val repository = DefaultCollaborationRepository(cache, remote) { true }

            remote.blockNextTaskDetails()
            val mutationA = async {
                repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))
            }
            remote.taskDetailsStarted.await()

            when (failureMode) {
                "INVALID_STATE" -> {
                    remote.taskMutationEnvelope =
                        CollaborationMutationEnvelopeRemoteDto(status = "INVALID_STATE")
                }
                "NETWORK" -> remote.taskMutationFailure = IllegalStateException("network down")
                "CANCELLED" -> remote.taskMutationFailure = CancellationException("cancelled")
            }
            val mutationB = try {
                repository.startTask(
                    StartGroupTaskCommand(secondTaskId, expectedVersion = 0L)
                )
            } catch (_: CancellationException) {
                assertEquals("CANCELLED", failureMode)
                null
            }

            when (failureMode) {
                "INVALID_STATE" -> {
                    assertTrue(mutationB is CollaborationMutationResult.InvalidState)
                    assertTrue(
                        (mutationB as CollaborationMutationResult.InvalidState).error is
                            CollaborationError.InvalidState
                    )
                }
                "NETWORK" -> {
                    assertTrue(mutationB is CollaborationMutationResult.Failure)
                    assertTrue(
                        (mutationB as CollaborationMutationResult.Failure).error is
                            CollaborationError.NetworkUnavailable
                    )
                }
                "CANCELLED" -> assertEquals(null, mutationB)
            }
            remote.taskDetailsCompletion.complete(Unit)

            assertEquals(CollaborationMutationResult.Applied, mutationA.await())
            assertEquals(2, remote.fetchTaskDetailsCalls)
            assertEquals(1, cache.replaceTasksCalls)
            assertEquals(
                listOf("Task one new", "Task two old"),
                repository.observeTasks(groupId).first().map { it.title }
            )
        }
    }

    @Test
    fun `same-group successful mutation supplies the single successor refresh`() = runTest {
        val cache = FakeTaskCache(
            cachedDetails = listOf(
                cachedDetails(title = "Task one old"),
                cachedDetails(title = "Task two old", taskId = secondTaskId)
            )
        )
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(
                remoteDetails(title = "Task one new"),
                remoteDetails(title = "Task two old", taskId = secondTaskId)
            )
            taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        remote.blockNextTaskDetails()
        val mutationA = async {
            repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))
        }
        remote.taskDetailsStarted.await()

        assertEquals(
            CollaborationMutationResult.Applied,
            repository.startTask(StartGroupTaskCommand(secondTaskId, expectedVersion = 0L))
        )
        remote.taskDetailsCompletion.complete(Unit)

        assertEquals(CollaborationMutationResult.Applied, mutationA.await())
        assertEquals(2, remote.fetchTaskDetailsCalls)
        assertEquals(1, cache.replaceTasksCalls)
        assertEquals(
            listOf("Task one new", "Task two old"),
            repository.observeTasks(groupId).first().map { it.title }
        )
    }

    @Test
    fun `conflict refreshes authoritative task cache and preserves conflict result`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Stale")))
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(remoteDetails(title = "Server version"))
            taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = "CONFLICT")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))

        assertTrue(result is CollaborationMutationResult.Conflict)
        assertEquals(1, remote.fetchTaskDetailsCalls)
        assertEquals(
            listOf("Server version"),
            repository.observeTasks(groupId).first().map { it.title }
        )
    }

    @Test
    fun `conflict keeps the old cache when authoritative refresh fails`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Stale")))
        val remote = FakeTaskRemote().apply {
            taskDetailsFailure = IllegalStateException("refresh unavailable")
            taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = "CONFLICT")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))

        assertTrue(result is CollaborationMutationResult.Conflict)
        assertEquals(listOf("Stale"), repository.observeTasks(groupId).first().map { it.title })
        assertEquals(0, cache.replaceTasksCalls)
    }

    @Test
    fun `cache lookup failure never replaces authoritative applied or conflict result`() = runTest {
        listOf("APPLIED", "CONFLICT").forEach { status ->
            val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Keep"))).apply {
                findTaskGroupFailure = IllegalStateException("cache unavailable")
            }
            val remote = FakeTaskRemote().apply {
                taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = status)
            }
            val repository = DefaultCollaborationRepository(cache, remote) { true }

            val result = repository.startTask(StartGroupTaskCommand(taskId, 0L))

            if (status == "APPLIED") {
                assertEquals(CollaborationMutationResult.Applied, result)
            } else {
                assertTrue(result is CollaborationMutationResult.Conflict)
            }
            assertEquals(0, remote.fetchTaskDetailsCalls)
        }
    }

    @Test
    fun `uncached existing task mutation uses envelope group for authoritative refresh`() = runTest {
        val cache = FakeTaskCache(cachedDetails = emptyList())
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(remoteDetails(title = "Server"))
            taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(
                status = "APPLIED",
                data = kotlinx.serialization.json.buildJsonObject {
                    put("group_id", groupId.value)
                }
            )
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        assertEquals(
            CollaborationMutationResult.Applied,
            repository.startTask(StartGroupTaskCommand(taskId, 0L))
        )
        assertEquals(1, remote.fetchTaskDetailsCalls)
        assertEquals(listOf("Server"), repository.observeTasks(groupId).first().map { it.title })
    }

    @Test
    fun `all G3 task mutations delegate online and refresh the affected group`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Cached")))
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(remoteDetails(title = "Server"))
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val results = listOf(
            repository.createTask(
                CreateGroupTaskCommand(
                    taskId = taskId,
                    groupId = groupId,
                    title = "Created",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L)
                )
            ),
            repository.editTask(
                EditGroupTaskCommand(
                    taskId = taskId,
                    title = "Edited",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L),
                    expectedVersion = 0L
                )
            ),
            repository.reassignTask(ReassignGroupTaskCommand(taskId, assigneeId, 0L)),
            repository.startTask(StartGroupTaskCommand(taskId, 0L)),
            repository.completeTask(CompleteGroupTaskCommand(taskId, 0L)),
            repository.cancelTask(CancelGroupTaskCommand(taskId, 0L)),
            repository.reopenTask(ReopenGroupTaskCommand(taskId, 0L))
        )

        assertTrue(results.all { it == CollaborationMutationResult.Applied })
        assertEquals(
            listOf("createTask", "editTask", "reassignTask", "startTask", "completeTask", "cancelTask", "reopenTask"),
            remote.taskMutationCalls
        )
        assertEquals(7, remote.fetchTaskDetailsCalls)
        assertEquals(7, cache.replaceTasksCalls)
    }

    @Test
    fun `all G3 task mutations are network required offline without cache writes`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Keep")))
        val remote = FakeTaskRemote()
        val repository = DefaultCollaborationRepository(cache, remote) { false }
        val create = CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Created",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(60L)
        )
        val edit = EditGroupTaskCommand(
            taskId = taskId,
            title = "Edited",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(60L),
            expectedVersion = 0L
        )

        val results = listOf(
            repository.createTask(create),
            repository.editTask(edit),
            repository.reassignTask(ReassignGroupTaskCommand(taskId, assigneeId, 0L)),
            repository.startTask(StartGroupTaskCommand(taskId, 0L)),
            repository.completeTask(CompleteGroupTaskCommand(taskId, 0L)),
            repository.cancelTask(CancelGroupTaskCommand(taskId, 0L)),
            repository.reopenTask(ReopenGroupTaskCommand(taskId, 0L))
        )

        assertTrue(results.all { it == CollaborationMutationResult.NetworkRequired })
        assertTrue(remote.taskMutationCalls.isEmpty())
        assertEquals(0, remote.fetchTaskDetailsCalls)
        assertEquals(0, cache.replaceTasksCalls)
        assertEquals(listOf("Keep"), repository.observeTasks(groupId).first().map { it.title })
    }

    @Test
    fun `task transport failure retains cache and maps to typed network error`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Keep")))
        val remote = FakeTaskRemote().apply {
            taskMutationFailure = IllegalStateException("transport down")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.startTask(StartGroupTaskCommand(taskId, 0L))

        assertTrue(result is CollaborationMutationResult.Failure)
        assertTrue(
            (result as CollaborationMutationResult.Failure).error is
                CollaborationError.NetworkUnavailable
        )
        assertEquals(listOf("Keep"), repository.observeTasks(groupId).first().map { it.title })
        assertEquals(0, cache.removeTasksCalls)
    }

    @Test
    fun `task authorization loss redacts the affected task cache`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Private")))
        val remote = FakeTaskRemote().apply {
            taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = "NOT_AUTHORIZED")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.startTask(StartGroupTaskCommand(taskId, 0L))

        assertEquals(
            CollaborationMutationResult.NotAuthorized(CollaborationError.NotAuthorized),
            result
        )
        assertEquals(1, cache.removeTasksCalls)
        assertTrue(repository.observeTasks(groupId).first().isEmpty())
    }

    @Test
    fun `stale task refresh cannot reconcile after same-session authorization redaction`() = runTest {
        val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Private")))
        val remote = FakeTaskRemote().apply {
            taskDetails = listOf(remoteDetails(title = "Stale account task"))
            taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = "NOT_AUTHORIZED")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        remote.blockNextTaskDetails()
        val refresh = async { repository.refreshTasks(groupId) }
        remote.taskDetailsStarted.await()

        assertEquals(
            CollaborationMutationResult.NotAuthorized(CollaborationError.NotAuthorized),
            repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))
        )
        remote.taskDetailsCompletion.complete(Unit)

        assertEquals(CollaborationMutationResult.Applied, refresh.await())
        assertEquals(1, remote.fetchTaskDetailsCalls)
        assertTrue(repository.observeTasks(groupId).first().isEmpty())
    }

    @Test
    fun `late task access loss from an old session cannot evict the new session cache`() = runTest {
        listOf("NOT_AUTHORIZED", "NOT_FOUND").forEach { status ->
            val cache = FakeTaskCache(cachedDetails = listOf(cachedDetails(title = "Session A")))
            val remote = FakeTaskRemote().apply {
                taskMutationEnvelope = CollaborationMutationEnvelopeRemoteDto(status = status)
                blockNextTaskMutation()
            }
            val repository = DefaultCollaborationRepository(cache, remote) { true }

            val mutation = async {
                repository.startTask(StartGroupTaskCommand(taskId, expectedVersion = 0L))
            }
            remote.taskMutationStarted.await()

            repository.clearSessionCache()
            cache.seed(cachedDetails(title = "Session B"))
            remote.taskMutationCompletion.complete(Unit)

            val result = mutation.await()
            if (status == "NOT_AUTHORIZED") {
                assertEquals(
                    CollaborationMutationResult.NotAuthorized(CollaborationError.NotAuthorized),
                    result
                )
            } else {
                assertEquals(
                    CollaborationMutationResult.Failure(CollaborationError.NotFound),
                    result
                )
            }
            assertEquals(listOf("Session B"), repository.observeTasks(groupId).first().map { it.title })
            assertEquals(0, cache.removeTasksCalls)
        }
    }

    private fun cachedDetails(
        title: String,
        taskId: GroupTaskId = this.taskId,
        groupId: CollaborationGroupId = this.groupId
    ): GroupTaskDetails {
        val task = CollaborationRemoteDataSourceTaskFixtures.domainTask(
            id = taskId.value,
            groupId = groupId.value,
            title = title,
            assigneeId = assigneeId.value,
            status = "TODO"
        )
        return GroupTaskDetails(
            task = task,
            reminders = listOf(
                com.smartreminder.domain.model.collaboration.GroupTaskReminder(taskId, 300L),
                com.smartreminder.domain.model.collaboration.GroupTaskReminder(taskId, 60L)
            )
        )
    }

    private fun remoteDetails(
        title: String,
        taskId: GroupTaskId = this.taskId,
        groupId: CollaborationGroupId = this.groupId
    ): CollaborationTaskDetailsRemoteDto =
        CollaborationTaskDetailsRemoteDto(
            task = CollaborationTaskRemoteDto(
                id = taskId.value,
                groupId = groupId.value,
                title = title,
                description = null,
                createdBy = "owner-1",
                assigneeId = assigneeId.value,
                dueAt = dueAt.toString(),
                status = "TODO",
                version = 0L,
                createdAt = "2026-09-10T10:00:00Z",
                updatedAt = "2026-09-10T10:00:00Z"
            ),
            reminders = listOf(
                CollaborationTaskReminderRemoteDto(taskId.value, 300L),
                CollaborationTaskReminderRemoteDto(taskId.value, 60L)
            )
        )
}

private object CollaborationRemoteDataSourceTaskFixtures {
    fun domainTask(
        id: String,
        groupId: String,
        title: String,
        assigneeId: String,
        status: String
    ) = com.smartreminder.domain.model.collaboration.GroupTask(
        id = GroupTaskId(id),
        groupId = CollaborationGroupId(groupId),
        title = title,
        description = null,
        createdBy = UserId("owner-1"),
        assigneeId = UserId(assigneeId),
        dueAt = Instant.parse("2026-09-12T11:00:00Z"),
        status = GroupTaskStatus.valueOf(status),
        version = 0L,
        createdAt = Instant.parse("2026-09-10T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-10T10:00:00Z")
    )
}

private class FakeTaskCache(
    cachedDetails: List<GroupTaskDetails>
) : CollaborationCacheDataSource {
    private val taskFlows = mutableMapOf<String, MutableStateFlow<List<CachedGroupTaskEntity>>>()
    private val taskDetailFlows = mutableMapOf<String, MutableStateFlow<List<GroupTaskWithRemindersEntity>>>()
    var replaceTasksCalls = 0
        private set
    var removeTasksCalls = 0
        private set
    var findTaskGroupFailure: Throwable? = null

    init {
        val details = cachedDetails.map { GroupTaskWithRemindersEntity(
            task = com.smartreminder.data.local.room.mapper.GroupTaskMapper.toEntity(it.task),
            reminders = it.reminders.map(com.smartreminder.data.local.room.mapper.GroupTaskMapper::toEntity)
        ) }
        val groupId = details.firstOrNull()?.task?.groupId ?: "group-1"
        taskDetailFlows[groupId] = MutableStateFlow(details)
        taskFlows[groupId] = MutableStateFlow(details.map(GroupTaskWithRemindersEntity::task))
    }

    override fun observeGroups(): Flow<List<CachedCollaborationGroupEntity>> = MutableStateFlow(emptyList())
    override fun observeGroup(groupId: String): Flow<CachedCollaborationGroupEntity?> = MutableStateFlow(null)
    override fun observeMembers(groupId: String): Flow<List<CachedGroupMemberEntity>> = MutableStateFlow(emptyList())
    override fun observeTasks(groupId: String): Flow<List<CachedGroupTaskEntity>> =
        taskFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }
    override fun observeTaskDetails(groupId: String): Flow<List<GroupTaskWithRemindersEntity>> =
        taskDetailFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }
    override fun observeInvites(): Flow<List<CachedGroupInviteEntity>> = MutableStateFlow(emptyList())

    override suspend fun replaceGroups(groups: List<CachedCollaborationGroupEntity>) = Unit
    override suspend fun replaceGroup(
        group: CachedCollaborationGroupEntity,
        members: List<CachedGroupMemberEntity>
    ) = Unit
    override suspend fun replaceInvites(invites: List<CachedGroupInviteEntity>) = Unit
    override suspend fun removeGroup(groupId: String) = Unit

    override suspend fun replaceTasks(
        groupId: String,
        tasks: List<CachedGroupTaskEntity>,
        reminders: List<CachedGroupTaskReminderEntity>
    ) {
        replaceTasksCalls += 1
        val relations = tasks.map { task ->
            GroupTaskWithRemindersEntity(
                task = task,
                reminders = reminders.filter { it.taskId == task.id }
            )
        }
        taskFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.value = tasks
        taskDetailFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.value = relations
    }

    override suspend fun removeTasksForGroup(groupId: String) {
        removeTasksCalls += 1
        taskFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.value = emptyList()
        taskDetailFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }

    override suspend fun findTaskGroupId(taskId: String): String? = taskFlows.values
        .let { flows ->
            findTaskGroupFailure?.let { throw it }
            flows.asSequence()
                .flatMap { it.value.asSequence() }
                .firstOrNull { it.id == taskId }
                ?.groupId
        }

    suspend fun seed(details: GroupTaskDetails) {
        val relation = GroupTaskMapper.toEntity(details)
        replaceTasks(
            groupId = details.task.groupId.value,
            tasks = listOf(relation.task),
            reminders = relation.reminders
        )
    }

    override suspend fun clearAll() {
        taskFlows.values.forEach { it.value = emptyList() }
        taskDetailFlows.values.forEach { it.value = emptyList() }
    }
}

private class FakeTaskRemote : CollaborationRemoteDataSource {
    var taskDetails: List<CollaborationTaskDetailsRemoteDto> = emptyList()
    var taskDetailsFailure: Throwable? = null
    var taskMutationFailure: Throwable? = null
    var taskMutationEnvelope: CollaborationMutationEnvelopeRemoteDto =
        CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
    var fetchTaskDetailsCalls = 0
        private set
    val fetchTaskDetailsGroupIds = mutableListOf<String>()
    val taskMutationCalls = mutableListOf<String>()
    val taskDetailsStarted = CompletableDeferred<Unit>()
    val taskDetailsCompletion = CompletableDeferred<Unit>()
    val taskMutationStarted = CompletableDeferred<Unit>()
    val taskMutationCompletion = CompletableDeferred<Unit>()
    var taskDetailsByCall: List<List<CollaborationTaskDetailsRemoteDto>>? = null
    private var blockNextTaskDetails = false
    private var blockNextTaskMutation = false

    fun blockNextTaskDetails() {
        blockNextTaskDetails = true
    }

    fun blockNextTaskMutation() {
        blockNextTaskMutation = true
    }

    override suspend fun fetchGroups(): List<CollaborationGroupRemoteDto> = emptyList()
    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? = null
    override suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto> = emptyList()
    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> = emptyList()

    override suspend fun fetchTaskDetails(groupId: String): List<CollaborationTaskDetailsRemoteDto> {
        fetchTaskDetailsCalls += 1
        val callNumber = fetchTaskDetailsCalls
        fetchTaskDetailsGroupIds += groupId
        taskDetailsFailure?.let { throw it }
        if (blockNextTaskDetails) {
            blockNextTaskDetails = false
            taskDetailsStarted.complete(Unit)
            taskDetailsCompletion.await()
        }
        return taskDetailsByCall?.getOrNull(callNumber - 1) ?: taskDetails
    }

    override suspend fun createGroup(command: CreateGroupCommand) = applied()
    override suspend fun updateGroup(command: UpdateGroupCommand) = applied()
    override suspend fun inviteMember(command: InviteMemberCommand) = applied()
    override suspend fun acceptInvite(command: AcceptInviteCommand) = applied()
    override suspend fun declineInvite(command: DeclineInviteCommand) = applied()
    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = applied()
    override suspend fun removeMember(command: RemoveMemberCommand) = applied()
    override suspend fun transferOwnership(command: TransferOwnershipCommand) = applied()
    override suspend fun leaveGroup(command: LeaveGroupCommand) = applied()
    override suspend fun deleteGroup(command: DeleteGroupCommand) = applied()

    override suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "createTask"
        return taskMutationResult()
    }

    override suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "editTask"
        return taskMutationResult()
    }

    override suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "reassignTask"
        return taskMutationResult()
    }

    override suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "startTask"
        return taskMutationResult()
    }

    override suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "completeTask"
        return taskMutationResult()
    }

    override suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "cancelTask"
        return taskMutationResult()
    }

    override suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "reopenTask"
        return taskMutationResult()
    }

    private suspend fun taskMutationResult(): CollaborationMutationEnvelopeRemoteDto {
        taskMutationFailure?.let { throw it }
        if (blockNextTaskMutation) {
            blockNextTaskMutation = false
            taskMutationStarted.complete(Unit)
            taskMutationCompletion.await()
        }
        return taskMutationEnvelope
    }

    private fun applied() = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
}
