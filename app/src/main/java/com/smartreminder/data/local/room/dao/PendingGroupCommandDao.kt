package com.smartreminder.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.smartreminder.data.local.room.entity.collaboration.PendingGroupCommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingGroupCommandDao {
    @Query("SELECT * FROM pending_group_commands WHERE state = :state ORDER BY enqueue_sequence ASC, id ASC")
    fun observeByState(state: String): Flow<List<PendingGroupCommandEntity>>

    @Query("SELECT * FROM pending_group_commands WHERE state = 'PENDING' ORDER BY enqueue_sequence ASC, id ASC")
    fun observePending(): Flow<List<PendingGroupCommandEntity>>

    @Query("SELECT * FROM pending_group_commands WHERE aggregate_id = :aggregateId ORDER BY enqueue_sequence ASC, id ASC")
    suspend fun getCommandsForAggregate(aggregateId: String): List<PendingGroupCommandEntity>

    @Query(
        """
        INSERT INTO pending_group_commands (
            id,
            command_type,
            aggregate_id,
            payload_json,
            payload_version,
            expected_version,
            created_at,
            enqueue_sequence,
            attempt_count,
            state,
            last_error
        )
        SELECT
            :id,
            :commandType,
            :aggregateId,
            :payloadJson,
            :payloadVersion,
            :expectedVersion,
            :createdAt,
            COALESCE(MAX(enqueue_sequence), 0) + 1,
            :attemptCount,
            :state,
            :lastError
        FROM pending_group_commands
        """
    )
    suspend fun insertWithNextSequence(
        id: String,
        commandType: String,
        aggregateId: String,
        payloadJson: String,
        payloadVersion: Int,
        expectedVersion: Long?,
        createdAt: Long,
        attemptCount: Int,
        state: String,
        lastError: String?
    )

    suspend fun enqueue(command: PendingGroupCommandEntity) {
        insertWithNextSequence(
            id = command.id,
            commandType = command.commandType,
            aggregateId = command.aggregateId,
            payloadJson = command.payloadJson,
            payloadVersion = command.payloadVersion,
            expectedVersion = command.expectedVersion,
            createdAt = command.createdAt,
            attemptCount = command.attemptCount,
            state = command.state,
            lastError = command.lastError
        )
    }

    @Query("UPDATE pending_group_commands SET state = :newState, attempt_count = attempt_count + 1, last_error = :error WHERE id = :commandId")
    suspend fun updateState(commandId: String, newState: String, error: String? = null)

    @Query("DELETE FROM pending_group_commands WHERE id = :commandId")
    suspend fun delete(commandId: String)

    @Query("SELECT COUNT(*) FROM pending_group_commands WHERE state = 'PENDING'")
    suspend fun pendingCount(): Int
}
