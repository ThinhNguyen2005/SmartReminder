package com.smartreminder.data.local.room.entity.collaboration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_group_commands",
    indices = [
        Index(value = ["state"]),
        Index(value = ["created_at"])
    ]
)
data class PendingGroupCommandEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "command_type")
    val commandType: String,
    @ColumnInfo(name = "aggregate_id")
    val aggregateId: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "payload_version")
    val payloadVersion: Int,
    @ColumnInfo(name = "expected_version")
    val expectedVersion: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "last_error")
    val lastError: String?
)
