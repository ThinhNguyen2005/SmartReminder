package com.smartreminder.data.local.room.model.collaboration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.IllegalArgumentException

class PendingGroupCommandCodecTest {

    @Test
    fun `test encode and decode CreateTaskPayload`() {
        val payload = PendingGroupCommandPayload.CreateTaskPayload(
            taskId = "task-1",
            groupId = "group-1",
            title = "Title",
            description = "Desc",
            assigneeId = "user-1",
            dueAt = "2023-10-10T10:00:00Z",
            reminderOffsetsSeconds = listOf(60L, 300L)
        )
        
        val json = PendingGroupCommandCodec.encode(payload)
        val decoded = PendingGroupCommandCodec.decode(PendingGroupCommandType.CREATE_TASK, json)
        
        assertEquals(payload, decoded)
    }

    @Test
    fun `test encode and decode StartTaskPayload`() {
        val payload = PendingGroupCommandPayload.StartTaskPayload(taskId = "task-1")
        
        val json = PendingGroupCommandCodec.encode(payload)
        val decoded = PendingGroupCommandCodec.decode(PendingGroupCommandType.START_TASK, json)
        
        assertEquals(payload, decoded)
    }

    @Test
    fun `test encode and decode CompleteTaskPayload`() {
        val payload = PendingGroupCommandPayload.CompleteTaskPayload(taskId = "task-1")
        
        val json = PendingGroupCommandCodec.encode(payload)
        val decoded = PendingGroupCommandCodec.decode(PendingGroupCommandType.COMPLETE_TASK, json)
        
        assertEquals(payload, decoded)
    }

    @Test
    fun `test encode and decode EditOwnTaskContentPayload`() {
        val payload = PendingGroupCommandPayload.EditOwnTaskContentPayload(
            taskId = "task-1",
            title = "New Title",
            description = null,
            expectedVersion = 1L
        )
        
        val json = PendingGroupCommandCodec.encode(payload)
        val decoded = PendingGroupCommandCodec.decode(PendingGroupCommandType.EDIT_OWN_TASK_CONTENT, json)
        
        assertEquals(payload, decoded)
    }

    @Test
    fun `decode with unsupported schema version throws exception`() {
        val json = """{"schemaVersion":999,"taskId":"task-1"}"""
        
        assertThrows(IllegalArgumentException::class.java) {
            PendingGroupCommandCodec.decode(PendingGroupCommandType.START_TASK, json)
        }
    }

    @Test
    fun `decode with wrong type throws exception`() {
        val payload = PendingGroupCommandPayload.CreateTaskPayload(
            taskId = "task-1",
            groupId = "group-1",
            title = "Title",
            description = "Desc",
            assigneeId = "user-1",
            dueAt = "2023-10-10T10:00:00Z",
            reminderOffsetsSeconds = emptyList()
        )
        
        val json = PendingGroupCommandCodec.encode(payload)
        
        assertThrows(IllegalArgumentException::class.java) {
            PendingGroupCommandCodec.decode(PendingGroupCommandType.START_TASK, json)
        }
    }
}
