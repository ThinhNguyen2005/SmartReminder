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
                put("status", "COMPLETED")
                put("version", 8L)
            }
        )

        val response = CollaborationRemoteMapper.toTaskMutationResponse(envelope)
        assertEquals(CollaborationMutationResult.Applied, response.result)
        assertEquals(GroupTaskId("task-1"), response.taskId)
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

        val create = invoker.calls[0].parameters
        assertEquals("task-1", create["p_task_id"]!!.jsonPrimitive.content)
        assertEquals("group-1", create["p_group_id"]!!.jsonPrimitive.content)
        assertEquals("Create report", create["p_title"]!!.jsonPrimitive.content)
        assertEquals("Details", create["p_description"]!!.jsonPrimitive.content)
        assertEquals("member-1", create["p_assignee_id"]!!.jsonPrimitive.content)
        assertEquals(dueAt.toString(), create["p_due_at"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("300", "60"),
            create["p_reminder_offsets_seconds"]!!.jsonArray.map { it.jsonPrimitive.content }
        )

        val edit = invoker.calls[1].parameters
        assertEquals("task-1", edit["p_task_id"]!!.jsonPrimitive.content)
        assertEquals("Edited report", edit["p_title"]!!.jsonPrimitive.content)
        assertEquals("member-2", edit["p_assignee_id"]!!.jsonPrimitive.content)
        assertEquals(dueAt.toString(), edit["p_due_at"]!!.jsonPrimitive.content)
        assertEquals(listOf("900"), edit["p_reminder_offsets_seconds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("7", edit["p_expected_version"]!!.jsonPrimitive.content)
        assertFalse(edit.containsKey("p_description"))

        invoker.calls.drop(2).forEachIndexed { index, call ->
            assertEquals("task-1", call.parameters["p_task_id"]!!.jsonPrimitive.content)
            assertEquals((index + 8).toString(), call.parameters["p_expected_version"]!!.jsonPrimitive.content)
        }
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
}
