package com.smartreminder.data.remote.collaboration

import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.CancelGroupTaskCommand
import com.smartreminder.domain.repository.CompleteGroupTaskCommand
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.EditGroupTaskCommand
import com.smartreminder.domain.repository.ReassignGroupTaskCommand
import com.smartreminder.domain.repository.ReopenGroupTaskCommand
import com.smartreminder.domain.repository.StartGroupTaskCommand
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class G3TaskRemoteContractTest {

    @Test
    fun `task and reminder DTOs deserialize PostgREST snake case with nullable description`() {
        val task = Json.decodeFromString<CollaborationTaskRemoteDto>(
            """
            {
              "id":"task-1",
              "group_id":"group-1",
              "title":"Prepare report",
              "description":null,
              "created_by":"owner-1",
              "assignee_id":"member-1",
              "due_at":"2026-09-12T18:00:00+07:00",
              "status":"IN_PROGRESS",
              "version":7,
              "created_at":"2026-09-10T10:00:00Z",
              "updated_at":"2026-09-11T10:00:00Z"
            }
            """.trimIndent()
        )
        val reminder = Json.decodeFromString<CollaborationTaskReminderRemoteDto>(
            """{"task_id":"task-1","offset_seconds":300}"""
        )

        assertEquals("task-1", task.id)
        assertEquals("group-1", task.groupId)
        assertNull(task.description)
        assertEquals(Instant.parse("2026-09-12T11:00:00Z"), Instant.parse(task.dueAt))
        assertEquals(7L, task.version)
        assertEquals("IN_PROGRESS", task.status)
        assertEquals("task-1", reminder.taskId)
        assertEquals(300L, reminder.offsetSeconds)

        val embedded = Json.decodeFromString<CollaborationTaskWithRemindersRemoteDto>(
            """
            {
              "id":"task-1",
              "group_id":"group-1",
              "title":"Prepare report",
              "description":null,
              "created_by":"owner-1",
              "assignee_id":"member-1",
              "due_at":"2026-09-12T11:00:00Z",
              "status":"TODO",
              "version":7,
              "created_at":"2026-09-10T10:00:00Z",
              "updated_at":"2026-09-11T10:00:00Z",
              "group_task_reminders":[
                {"task_id":"task-1","offset_seconds":300},
                {"task_id":"task-1","offset_seconds":60}
              ]
            }
            """.trimIndent()
        )
        assertEquals(listOf(300L, 60L), embedded.reminders.map { it.offsetSeconds })
    }

    @Test
    fun `task mapper keeps instant version status and sorts offsets`() {
        val task = CollaborationTaskRemoteDto(
            id = "task-1",
            groupId = "group-1",
            title = "Prepare report",
            description = null,
            createdBy = "owner-1",
            assigneeId = "member-1",
            dueAt = "2026-09-12T11:00:00Z",
            status = "in_progress",
            version = 7L,
            createdAt = "2026-09-10T10:00:00Z",
            updatedAt = "2026-09-11T10:00:00Z"
        )
        val details = CollaborationRemoteMapper.toDetailsDomain(
            task,
            listOf(
                CollaborationTaskReminderRemoteDto("task-1", 300L),
                CollaborationTaskReminderRemoteDto("task-1", 60L)
            )
        )

        assertEquals(GroupTaskId("task-1"), details.task.id)
        assertEquals(CollaborationGroupId("group-1"), details.task.groupId)
        assertNull(details.task.description)
        assertEquals(Instant.parse("2026-09-12T11:00:00Z"), details.task.dueAt)
        assertEquals(GroupTaskStatus.IN_PROGRESS, details.task.status)
        assertEquals(7L, details.task.version)
        assertEquals(listOf(60L, 300L), details.reminders.map { it.offsetSeconds })

        val cacheTask = CollaborationRemoteMapper.toCache(task)
        val cacheReminder = CollaborationRemoteMapper.toCache(
            CollaborationTaskReminderRemoteDto("task-1", 60L)
        )
        assertEquals(task.dueAt, Instant.ofEpochMilli(cacheTask.dueAt).toString())
        assertEquals("IN_PROGRESS", cacheTask.status)
        assertEquals(7L, cacheTask.version)
        assertNull(cacheTask.description)
        assertEquals("task-1", cacheReminder.taskId)
        assertEquals(60L, cacheReminder.offsetSeconds)
    }

    @Test
    fun `task mutation envelope maps typed response data and not found`() {
        val envelope = CollaborationMutationEnvelopeRemoteDto(
            status = "APPLIED",
            data = buildJsonObject {
                put("task_id", "task-1")
                put("group_id", "group-1")
                put("status", "COMPLETED")
                put("version", 8L)
            }
        )

        val response = CollaborationRemoteMapper.toTaskMutationResponse(envelope)
        assertEquals(CollaborationMutationResult.Applied, response.result)
        assertEquals(GroupTaskId("task-1"), response.taskId)
        assertEquals(CollaborationGroupId("group-1"), response.groupId)
        assertEquals(GroupTaskStatus.COMPLETED, response.status)
        assertEquals(8L, response.version)

        val missing = CollaborationRemoteMapper.toTaskMutationResponse(
            CollaborationMutationEnvelopeRemoteDto(
                status = "NOT_FOUND",
                error = CollaborationErrorRemoteDto(code = "NOT_FOUND")
            )
        )
        assertTrue(missing.result is CollaborationMutationResult.Failure)
        assertEquals(
            com.smartreminder.domain.repository.CollaborationError.NotFound,
            (missing.result as CollaborationMutationResult.Failure).error
        )

        val queued = CollaborationRemoteMapper.toTaskMutationResult(
            CollaborationMutationEnvelopeRemoteDto(status = "QUEUED")
        )
        assertTrue(queued is CollaborationMutationResult.Failure)
        assertTrue(
            (queued as CollaborationMutationResult.Failure).error is
                com.smartreminder.domain.repository.CollaborationError.InvalidState
        )
    }

    @Test
    fun `all G3 task mutations use exact RPC names and typed parameters`() = runTest {
        val invoker = TaskRecordingRpcInvoker()
        val remote = SupabaseCollaborationRemoteDataSource(invoker)
        val taskId = GroupTaskId("task-1")
        val groupId = CollaborationGroupId("group-1")
        val assigneeId = UserId("member-2")
        val dueAt = Instant.parse("2026-09-12T11:00:00Z")

        remote.createTask(
            CreateGroupTaskCommand(
                taskId = taskId,
                groupId = groupId,
                title = "Create report",
                description = "Details",
                assigneeId = UserId("member-1"),
                dueAt = dueAt,
                reminderOffsetsSeconds = listOf(300L, 60L)
            )
        )
        remote.editTask(
            EditGroupTaskCommand(
                taskId = taskId,
                title = "Edited report",
                description = null,
                assigneeId = assigneeId,
                dueAt = dueAt,
                reminderOffsetsSeconds = listOf(900L),
                expectedVersion = 7L
            )
        )
        remote.reassignTask(ReassignGroupTaskCommand(taskId, assigneeId, 8L))
        remote.startTask(StartGroupTaskCommand(taskId, 9L))
        remote.completeTask(CompleteGroupTaskCommand(taskId, 10L))
        remote.cancelTask(CancelGroupTaskCommand(taskId, 11L))
        remote.reopenTask(ReopenGroupTaskCommand(taskId, 12L))

        assertEquals(
            listOf(
                "create_group_task",
                "edit_group_task",
                "reassign_group_task",
                "start_group_task",
                "complete_group_task",
                "cancel_group_task",
                "reopen_group_task"
            ),
            invoker.calls.map(TaskRecordingRpcInvoker.Call::function)
        )

        assertEquals(
            buildJsonObject {
                put("p_task_id", "task-1")
                put("p_group_id", "group-1")
                put("p_title", "Create report")
                put("p_description", "Details")
                put("p_assignee_id", "member-1")
                put("p_due_at", dueAt.toString())
                put("p_reminder_offsets_seconds", JsonArray(listOf(JsonPrimitive(300L), JsonPrimitive(60L))))
            },
            invoker.calls[0].parameters
        )
        assertFalse(
            invoker.calls[0].parameters["p_reminder_offsets_seconds"]!!.jsonArray
                .any { it.jsonPrimitive.isString }
        )

        assertEquals(
            buildJsonObject {
                put("p_task_id", "task-1")
                put("p_title", "Edited report")
                put("p_assignee_id", "member-2")
                put("p_due_at", dueAt.toString())
                put("p_reminder_offsets_seconds", JsonArray(listOf(JsonPrimitive(900L))))
                put("p_expected_version", 7L)
            },
            invoker.calls[1].parameters
        )
        assertFalse(invoker.calls[1].parameters["p_expected_version"]!!.jsonPrimitive.isString)

        assertEquals(
            buildJsonObject {
                put("p_task_id", "task-1")
                put("p_assignee_id", "member-2")
                put("p_expected_version", 8L)
            },
            invoker.calls[2].parameters
        )
        assertEquals("member-2", invoker.calls[2].parameters["p_assignee_id"]!!.jsonPrimitive.content)

        listOf(
            "start_group_task" to 9L,
            "complete_group_task" to 10L,
            "cancel_group_task" to 11L,
            "reopen_group_task" to 12L
        ).forEachIndexed { index, (function, expectedVersion) ->
            assertEquals(
                buildJsonObject {
                    put("p_task_id", "task-1")
                    put("p_expected_version", expectedVersion)
                },
                invoker.calls[index + 3].parameters
            )
            assertEquals(function, invoker.calls[index + 3].function)
            assertFalse(invoker.calls[index + 3].parameters["p_expected_version"]!!.jsonPrimitive.isString)
        }
    }

    @Test
    fun `task details use one group scoped embedded read and sort reminder offsets`() = runTest {
        val invoker = TaskRecordingReadInvoker(
            rows = listOf(
                embeddedTask(
                    reminders = listOf(
                        CollaborationTaskReminderRemoteDto("task-1", 300L),
                        CollaborationTaskReminderRemoteDto("task-1", 60L)
                    )
                )
            )
        )
        val remote = SupabaseCollaborationRemoteDataSource(
            taskDetailsReadInvoker = invoker,
            rpcInvoker = TaskRecordingRpcInvoker()
        )

        val details = remote.fetchTaskDetails("group-1", "task-1")

        assertEquals(
            listOf(
                CollaborationPostgrestSelectRequest(
                    table = "group_tasks",
                    columns = "*,group_task_reminders(*)",
                    filters = mapOf("group_id" to "group-1", "id" to "task-1")
                )
            ),
            invoker.requests
        )
        assertEquals(1, invoker.requests.size)
        assertEquals(listOf(60L, 300L), details!!.reminders.map { it.offsetSeconds })
        assertEquals("group-1", details.task.groupId)
    }

    @Test
    fun `group task details read traverses every page`() = runTest {
        val firstPage = (0 until 100).map { index ->
            embeddedTask(reminders = emptyList()).copy(id = "task-$index")
        }
        val secondPage = listOf(
            embeddedTask(
                reminders = listOf(CollaborationTaskReminderRemoteDto("task-100", 42L))
            ).copy(id = "task-100")
        )
        val invoker = TaskPagedReadInvoker(listOf(firstPage, secondPage))
        val remote = SupabaseCollaborationRemoteDataSource(
            taskDetailsReadInvoker = invoker,
            rpcInvoker = TaskRecordingRpcInvoker()
        )

        val details = remote.fetchTaskDetails("group-1")

        assertEquals(101, details.size)
        assertEquals(2, invoker.requests.size)
        assertEquals(101, details.map { it.task.id }.toSet().size)
        assertEquals(listOf(42L), details.last().reminders.map { it.offsetSeconds })
        assertEquals(listOf("created_at", "id"), invoker.requests[0].orderBy)
        assertEquals(0L, invoker.requests[0].rangeStart)
        assertEquals(99L, invoker.requests[0].rangeEnd)
        assertEquals(100L, invoker.requests[1].rangeStart)
        assertEquals(199L, invoker.requests[1].rangeEnd)
    }

    @Test
    fun `group task details read fails without returning a partial aggregate`() = runTest {
        val firstPage = (0 until 100).map { index ->
            embeddedTask(reminders = emptyList()).copy(id = "task-$index")
        }
        val invoker = TaskPagedReadInvoker(
            pages = listOf(firstPage),
            failureOnRequest = 2
        )
        val remote = SupabaseCollaborationRemoteDataSource(
            taskDetailsReadInvoker = invoker,
            rpcInvoker = TaskRecordingRpcInvoker()
        )

        var failure: Throwable? = null
        try {
            remote.fetchTaskDetails("group-1")
        } catch (throwable: Throwable) {
            failure = throwable
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(2, invoker.requests.size)
    }

    @Test
    fun `duplicate task rows across pages are rejected`() = runTest {
        val firstPage = (0 until 100).map { index ->
            embeddedTask(reminders = emptyList()).copy(id = "task-$index")
        }
        val secondPage = listOf(embeddedTask(reminders = emptyList()).copy(id = "task-99"))
        val remote = SupabaseCollaborationRemoteDataSource(
            taskDetailsReadInvoker = TaskPagedReadInvoker(listOf(firstPage, secondPage)),
            rpcInvoker = TaskRecordingRpcInvoker()
        )

        var failure: Throwable? = null
        try {
            remote.fetchTaskDetails("group-1")
        } catch (throwable: Throwable) {
            failure = throwable
        }
        assertTrue(failure is CollaborationMappingException)
    }

    @Test
    fun `missing embedded task row returns null without a second read`() = runTest {
        val invoker = TaskRecordingReadInvoker(rows = emptyList())
        val remote = SupabaseCollaborationRemoteDataSource(
            taskDetailsReadInvoker = invoker,
            rpcInvoker = TaskRecordingRpcInvoker()
        )

        assertNull(remote.fetchTaskDetails("group-1", "missing-task"))
        assertEquals(1, invoker.requests.size)
    }

    @Test
    fun `cross task reminder cannot be mapped into task details`() {
        val task = CollaborationTaskRemoteDto(
            id = "task-1",
            groupId = "group-1",
            title = "Task",
            description = "Description",
            createdBy = "owner-1",
            assigneeId = "member-1",
            dueAt = "2026-09-12T11:00:00Z",
            status = "TODO",
            version = 0L,
            createdAt = "2026-09-10T10:00:00Z",
            updatedAt = "2026-09-10T10:00:00Z"
        )

        assertThrows(CollaborationMappingException::class.java) {
            CollaborationRemoteMapper.toDetailsDomain(
                task,
                listOf(CollaborationTaskReminderRemoteDto("other-task", 60L))
            )
        }
    }

    private class TaskRecordingRpcInvoker : CollaborationRpcInvoker {
        data class Call(
            val function: String,
            val parameters: kotlinx.serialization.json.JsonObject
        )

        val calls = mutableListOf<Call>()

        override suspend fun invoke(
            function: String,
            parameters: kotlinx.serialization.json.JsonObject
        ): CollaborationMutationEnvelopeRemoteDto {
            calls += Call(function, parameters)
            return CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
        }
    }

    private fun embeddedTask(
        reminders: List<CollaborationTaskReminderRemoteDto>
    ): CollaborationTaskWithRemindersRemoteDto = CollaborationTaskWithRemindersRemoteDto(
        id = "task-1",
        groupId = "group-1",
        title = "Prepare report",
        description = null,
        createdBy = "owner-1",
        assigneeId = "member-1",
        dueAt = "2026-09-12T11:00:00Z",
        status = "TODO",
        version = 7L,
        createdAt = "2026-09-10T10:00:00Z",
        updatedAt = "2026-09-11T10:00:00Z",
        reminders = reminders
    )

    private class TaskRecordingReadInvoker(
        private val rows: List<CollaborationTaskWithRemindersRemoteDto>
    ) : CollaborationPostgrestReadInvoker {
        val requests = mutableListOf<CollaborationPostgrestSelectRequest>()

        override suspend fun select(
            request: CollaborationPostgrestSelectRequest
        ): List<CollaborationTaskWithRemindersRemoteDto> {
            requests += request
            return rows
        }
    }

    private class TaskPagedReadInvoker(
        private val pages: List<List<CollaborationTaskWithRemindersRemoteDto>>,
        private val failureOnRequest: Int? = null
    ) : CollaborationPostgrestReadInvoker {
        val requests = mutableListOf<CollaborationPostgrestSelectRequest>()

        override suspend fun select(
            request: CollaborationPostgrestSelectRequest
        ): List<CollaborationTaskWithRemindersRemoteDto> {
            requests += request
            if (requests.size == failureOnRequest) {
                throw IllegalStateException("page unavailable")
            }
            return pages.getOrElse(requests.size - 1) { emptyList() }
        }
    }
}
