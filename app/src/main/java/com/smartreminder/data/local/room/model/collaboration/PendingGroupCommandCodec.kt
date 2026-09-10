package com.smartreminder.data.local.room.model.collaboration

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PendingGroupCommandCodec {

    private const val SUPPORTED_SCHEMA_VERSION = 1
    private val json = Json {
        encodeDefaults = true
    }

    fun encode(payload: PendingGroupCommandPayload): String {
        require(payload.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported schema version: ${payload.schemaVersion}"
        }

        return when (payload) {
            is PendingGroupCommandPayload.CreateTaskPayload -> json.encodeToString(payload)
            is PendingGroupCommandPayload.StartTaskPayload -> json.encodeToString(payload)
            is PendingGroupCommandPayload.CompleteTaskPayload -> json.encodeToString(payload)
            is PendingGroupCommandPayload.EditOwnTaskContentPayload -> json.encodeToString(payload)
        }
    }

    fun decode(type: PendingGroupCommandType, encodedJson: String): PendingGroupCommandPayload {
        val jsonObject = try {
            json.parseToJsonElement(encodedJson).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode JSON as type $type", e)
        }

        val schemaVersionPrimitive = jsonObject["schemaVersion"]?.jsonPrimitive
        val schemaVersion = schemaVersionPrimitive
            ?.takeUnless { it.isString }
            ?.content
            ?.toIntOrNull()
            ?: throw IllegalArgumentException("Missing or invalid schema version")

        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema version: $schemaVersion")
        }

        val payload: PendingGroupCommandPayload = try {
            when (type) {
                PendingGroupCommandType.CREATE_TASK -> json.decodeFromJsonElement<PendingGroupCommandPayload.CreateTaskPayload>(jsonObject)
                PendingGroupCommandType.START_TASK -> json.decodeFromJsonElement<PendingGroupCommandPayload.StartTaskPayload>(jsonObject)
                PendingGroupCommandType.COMPLETE_TASK -> json.decodeFromJsonElement<PendingGroupCommandPayload.CompleteTaskPayload>(jsonObject)
                PendingGroupCommandType.EDIT_OWN_TASK_CONTENT -> json.decodeFromJsonElement<PendingGroupCommandPayload.EditOwnTaskContentPayload>(jsonObject)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode JSON as type $type", e)
        }

        if (payload.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema version: ${payload.schemaVersion}")
        }

        return payload
    }
}
