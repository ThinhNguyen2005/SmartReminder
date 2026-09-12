package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.relation.GroupTaskWithRemindersEntity
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class GroupTaskRoomMapperTest {

    @Test
    fun `task domain round trip keeps primitive cache fields and typed values`() {
        val task = task()

        val entity = GroupTaskMapper.toEntity(task)
        val mapped = GroupTaskMapper.toDomain(entity)

        assertEquals(task, mapped)
        assertEquals("task-1", entity.id)
        assertEquals("group-1", entity.groupId)
        assertEquals(task.dueAt.toEpochMilli(), entity.dueAt)
        assertEquals("IN_PROGRESS", entity.status)
    }

    @Test
    fun `task details mapping sorts reminder offsets and preserves task ownership`() {
        val relation = GroupTaskWithRemindersEntity(
            task = GroupTaskMapper.toEntity(task()),
            reminders = listOf(
                CachedGroupTaskReminderEntity(taskId = "task-1", offsetSeconds = 300L),
                CachedGroupTaskReminderEntity(taskId = "task-1", offsetSeconds = 60L)
            )
        )

        val details = GroupTaskMapper.toDetailsDomain(relation)
        val roundTrip = GroupTaskMapper.toEntity(details)

        assertEquals(listOf(60L, 300L), details.reminders.map { it.offsetSeconds })
        assertEquals(listOf(60L, 300L), roundTrip.reminders.map { it.offsetSeconds })
        assertEquals(details.task, GroupTaskMapper.toDomain(roundTrip.task))
        assertEquals(
            listOf(GroupTaskId("task-1"), GroupTaskId("task-1")),
            details.reminders.map { it.taskId }
        )
    }

    @Test
    fun `task details list mapping sorts tasks by creation time and stable id`() {
        val older = task().copy(
            id = GroupTaskId("task-a"),
            createdAt = Instant.parse("2026-09-09T10:00:00Z")
        )
        val newer = task().copy(
            id = GroupTaskId("task-b"),
            createdAt = Instant.parse("2026-09-10T10:00:00Z")
        )

        val mapped = GroupTaskMapper.toDetailsDomain(
            listOf(
                GroupTaskWithRemindersEntity(GroupTaskMapper.toEntity(newer), emptyList()),
                GroupTaskWithRemindersEntity(GroupTaskMapper.toEntity(older), emptyList())
            )
        )

        assertEquals(listOf("task-a", "task-b"), mapped.map { it.task.id.value })
    }

    @Test(expected = IllegalStateException::class)
    fun `task details mapping rejects a reminder belonging to another task`() {
        GroupTaskMapper.toDetailsDomain(
            GroupTaskWithRemindersEntity(
                task = GroupTaskMapper.toEntity(task()),
                reminders = listOf(
                    CachedGroupTaskReminderEntity(taskId = "other-task", offsetSeconds = 60L)
                )
            )
        )
    }

    private fun task() = GroupTask(
        id = GroupTaskId("task-1"),
        groupId = CollaborationGroupId("group-1"),
        title = "Implement cache",
        description = "Keep the authoritative snapshot",
        createdBy = UserId("owner-1"),
        assigneeId = UserId("member-1"),
        dueAt = Instant.parse("2026-09-12T18:00:00Z"),
        status = GroupTaskStatus.IN_PROGRESS,
        version = 4L,
        createdAt = Instant.parse("2026-09-10T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-11T10:00:00Z")
    )
}
