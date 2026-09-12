package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
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

    @Test
    fun `create command accepts past and future absolute deadlines for a typed assignee`() {
        val deadlines = listOf(
            Instant.parse("2026-09-01T12:00:00Z"),
            Instant.parse("2026-10-01T12:00:00Z")
        )

        deadlines.forEach { deadline ->
            val command = CreateGroupTaskCommand(
                taskId = taskId,
                groupId = groupId,
                title = "Task with absolute deadline",
                assigneeId = assigneeId,
                dueAt = deadline,
                reminderOffsetsSeconds = listOf(300L)
            )

            assertEquals(assigneeId, command.assigneeId)
            assertEquals(deadline, command.dueAt)
        }
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

    // --- G3 full task command invariants ---

    @Test
    fun `full edit accepts both past and future absolute deadlines`() {
        val deadlines = listOf(
            Instant.parse("2026-09-01T12:00:00Z"),
            Instant.parse("2026-10-01T12:00:00Z")
        )

        deadlines.forEach { deadline ->
            val command = EditGroupTaskCommand(
                taskId = taskId,
                title = "Updated task",
                description = "Updated details",
                assigneeId = assigneeId,
                dueAt = deadline,
                reminderOffsetsSeconds = listOf(60L, 300L),
                expectedVersion = 4L
            )

            assertEquals(taskId, command.taskId)
            assertEquals(assigneeId, command.assigneeId)
            assertEquals(deadline, command.dueAt)
            assertEquals(4L, command.expectedVersion)
        }
    }

    @Test
    fun `all existing-task commands accept expected version zero`() {
        val versions = listOf<() -> Long>(
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L),
                    expectedVersion = 0L
                ).expectedVersion
            },
            { ReassignGroupTaskCommand(taskId, assigneeId, expectedVersion = 0L).expectedVersion },
            { StartGroupTaskCommand(taskId, expectedVersion = 0L).expectedVersion },
            { CompleteGroupTaskCommand(taskId, expectedVersion = 0L).expectedVersion },
            { CancelGroupTaskCommand(taskId, expectedVersion = 0L).expectedVersion },
            { ReopenGroupTaskCommand(taskId, expectedVersion = 0L).expectedVersion }
        )

        versions.forEach { command ->
            assertEquals(0L, command())
        }
    }

    @Test
    fun `full edit rejects every invalid title, reminder list, or version case`() {
        val invalidCommands = listOf<() -> Unit>(
            {
                EditGroupTaskCommand(
                    taskId,
                    "   ",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L),
                    expectedVersion = 0L
                )
            },
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = emptyList(),
                    expectedVersion = 0L
                )
            },
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L, 120L, 180L, 240L, 300L, 360L),
                    expectedVersion = 0L
                )
            },
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L, 0L),
                    expectedVersion = 0L
                )
            },
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L, -1L),
                    expectedVersion = 0L
                )
            },
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L, 60L),
                    expectedVersion = 0L
                )
            },
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L),
                    expectedVersion = -1L
                )
            }
        )

        invalidCommands.forEachIndexed { index, command ->
            assertIllegalArgument("full edit invalid case #$index", command)
        }
    }

    @Test
    fun `reassign and status commands preserve typed id and expected version`() {
        data class VersionCase(
            val expectedVersion: Long,
            val command: () -> Pair<GroupTaskId, Long>
        )

        val commandFields = listOf(
            VersionCase(2L) {
                ReassignGroupTaskCommand(taskId, assigneeId, expectedVersion = 2L).let {
                    it.taskId to it.expectedVersion
                }
            },
            VersionCase(3L) {
                StartGroupTaskCommand(taskId, expectedVersion = 3L).let {
                    it.taskId to it.expectedVersion
                }
            },
            VersionCase(4L) {
                CompleteGroupTaskCommand(taskId, expectedVersion = 4L).let {
                    it.taskId to it.expectedVersion
                }
            },
            VersionCase(5L) {
                CancelGroupTaskCommand(taskId, expectedVersion = 5L).let {
                    it.taskId to it.expectedVersion
                }
            },
            VersionCase(6L) {
                ReopenGroupTaskCommand(taskId, expectedVersion = 6L).let {
                    it.taskId to it.expectedVersion
                }
            }
        )

        commandFields.forEach { command ->
            val (actualTaskId, actualVersion) = command.command()
            assertEquals(taskId, actualTaskId)
            assertEquals(command.expectedVersion, actualVersion)
        }
        assertEquals(assigneeId, ReassignGroupTaskCommand(taskId, assigneeId, 2L).assigneeId)
    }

    @Test
    fun `all existing-task commands reject negative expected versions`() {
        val invalidCommands = listOf<() -> Unit>(
            {
                EditGroupTaskCommand(
                    taskId,
                    "Valid title",
                    assigneeId = assigneeId,
                    dueAt = dueAt,
                    reminderOffsetsSeconds = listOf(60L),
                    expectedVersion = -1L
                )
            },
            { ReassignGroupTaskCommand(taskId, assigneeId, expectedVersion = -1L) },
            { StartGroupTaskCommand(taskId, expectedVersion = -1L) },
            { CompleteGroupTaskCommand(taskId, expectedVersion = -1L) },
            { CancelGroupTaskCommand(taskId, expectedVersion = -1L) },
            { ReopenGroupTaskCommand(taskId, expectedVersion = -1L) }
        )

        invalidCommands.forEachIndexed { index, command ->
            assertIllegalArgument("negative expectedVersion case #$index", command)
        }
    }

    private fun assertIllegalArgument(label: String, action: () -> Unit) {
        try {
            action()
            fail("Expected IllegalArgumentException for $label")
        } catch (_: IllegalArgumentException) {
            // Expected for the invalid command table row.
        }
    }
}
