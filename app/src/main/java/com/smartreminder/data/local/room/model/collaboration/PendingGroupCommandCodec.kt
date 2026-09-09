package com.smartreminder.data.local.room.model.collaboration

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PendingGroupCommandCodec {

    fun encode(payload: PendingGroupCommandPayload): String {
        return when (payload) {
            is PendingGroupCommandPayload.CreateTaskPayload -> Json.encodeToString(payload)
            is PendingGroupCommandPayload.StartTaskPayload -> Json.encodeToString(payload)
            is PendingGroupCommandPayload.CompleteTaskPayload -> Json.encodeToString(payload)
            is PendingGroupCommandPayload.EditOwnTaskContentPayload -> Json.encodeToString(payload)
        }
    }

    fun decode(type: PendingGroupCommandType, json: String): PendingGroupCommandPayload {
        val payload: PendingGroupCommandPayload = try {
            when (type) {
                PendingGroupCommandType.CREATE_TASK -> Json.decodeFromString<PendingGroupCommandPayload.CreateTaskPayload>(json)
                PendingGroupCommandType.START_TASK -> Json.decodeFromString<PendingGroupCommandPayload.StartTaskPayload>(json)
                PendingGroupCommandType.COMPLETE_TASK -> Json.decodeFromString<PendingGroupCommandPayload.CompleteTaskPayload>(json)
                PendingGroupCommandType.EDIT_OWN_TASK_CONTENT -> Json.decodeFromString<PendingGroupCommandPayload.EditOwnTaskContentPayload>(json)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode JSON as type $type", e)
        }
        
        if (payload.schemaVersion != 1) {
            throw IllegalArgumentException("Unsupported schema version: ${payload.schemaVersion}")
        }
        
        return payload
    }
}
