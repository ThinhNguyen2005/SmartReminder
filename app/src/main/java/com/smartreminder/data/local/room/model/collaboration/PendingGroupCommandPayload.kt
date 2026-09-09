package com.smartreminder.data.local.room.model.collaboration

import kotlinx.serialization.Serializable

@Serializable
sealed class PendingGroupCommandPayload {
    abstract val schemaVersion: Int

    @Serializable
    data class CreateTaskPayload(
        override val schemaVersion: Int = 1,
        val taskId: String,
        val groupId: String,
        val title: String,
        val description: String?,
        val assigneeId: String,
        val dueAt: String,
        val reminderOffsetsSeconds: List<Long>
    ) : PendingGroupCommandPayload()

    @Serializable
    data class StartTaskPayload(
        override val schemaVersion: Int = 1,
        val taskId: String
    ) : PendingGroupCommandPayload()

    @Serializable
    data class CompleteTaskPayload(
        override val schemaVersion: Int = 1,
        val taskId: String
    ) : PendingGroupCommandPayload()

    @Serializable
    data class EditOwnTaskContentPayload(
        override val schemaVersion: Int = 1,
        val taskId: String,
        val title: String,
        val description: String?,
        val expectedVersion: Long
    ) : PendingGroupCommandPayload()
}
