package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class CollaborationRepositoryContractTest {

    private val taskId = GroupTaskId("task_1")
    private val groupId = CollaborationGroupId("group_1")
    private val assigneeId = UserId("user_1")
    private val dueAt = Instant.parse("2026-09-10T12:00:00Z")

    // --- CreateGroupTaskCommand Invariants ---

    @Test(expected = IllegalArgumentException::class)
    fun `create command rejects blank title`() {
        CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "   ",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(300L)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create command rejects empty reminders list`() {
        CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Valid Title",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = emptyList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create command rejects more than 5 reminders`() {
        CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Valid Title",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(60L, 300L, 600L, 1800L, 3600L, 7200L)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create command rejects zero offset`() {
        CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Valid Title",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(0L)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create command rejects negative offset`() {
        CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Valid Title",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(-60L)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create command rejects duplicate offsets`() {
        CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Valid Title",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(300L, 600L, 300L)
        )
    }

    @Test
    fun `create command with 1 valid reminder succeeds`() {
        val command = CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Buy groceries",
            description = "Milk, eggs, bread",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = listOf(1800L)
        )
        assertEquals(taskId, command.taskId)
        assertEquals(groupId, command.groupId)
        assertEquals("Buy groceries", command.title)
        assertEquals("Milk, eggs, bread", command.description)
        assertEquals(assigneeId, command.assigneeId)
        assertEquals(dueAt, command.dueAt)
        assertEquals(listOf(1800L), command.reminderOffsetsSeconds)
    }

    @Test
    fun `create command with 5 valid reminders succeeds`() {
        val offsets = listOf(60L, 300L, 600L, 1800L, 3600L)
        val command = CreateGroupTaskCommand(
            taskId = taskId,
            groupId = groupId,
            title = "Team meeting",
            assigneeId = assigneeId,
            dueAt = dueAt,
            reminderOffsetsSeconds = offsets
        )
        assertEquals(5, command.reminderOffsetsSeconds.size)
        assertNotNull(command)
    }

    // --- EditOwnGroupTaskContentCommand Invariants ---

    @Test(expected = IllegalArgumentException::class)
    fun `edit command rejects blank title`() {
        EditOwnGroupTaskContentCommand(
            taskId = taskId,
            title = "   ",
            expectedVersion = 0L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `edit command rejects negative expectedVersion`() {
        EditOwnGroupTaskContentCommand(
            taskId = taskId,
            title = "Valid Title",
            expectedVersion = -1L
        )
    }

    @Test
    fun `edit command with version zero succeeds`() {
        val command = EditOwnGroupTaskContentCommand(
            taskId = taskId,
            title = "Updated title",
            expectedVersion = 0L
        )
        assertEquals(taskId, command.taskId)
        assertEquals("Updated title", command.title)
        assertEquals(0L, command.expectedVersion)
    }

    @Test
    fun `edit command with description and positive version succeeds`() {
        val command = EditOwnGroupTaskContentCommand(
            taskId = taskId,
            title = "Final title",
            description = "Added details",
            expectedVersion = 5L
        )
        assertEquals("Final title", command.title)
        assertEquals("Added details", command.description)
        assertEquals(5L, command.expectedVersion)
    }
}
