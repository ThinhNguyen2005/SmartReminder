package com.smartreminder.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartreminder.data.local.room.entity.collaboration.PendingGroupCommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingGroupCommandDao {
    @Query("SELECT * FROM pending_group_commands WHERE state = :state ORDER BY created_at ASC, id ASC")
    fun observeByState(state: String): Flow<List<PendingGroupCommandEntity>>

    @Query("SELECT * FROM pending_group_commands WHERE state = 'PENDING' ORDER BY created_at ASC, id ASC")
    fun observePending(): Flow<List<PendingGroupCommandEntity>>

    @Query("SELECT * FROM pending_group_commands WHERE aggregate_id = :aggregateId ORDER BY created_at ASC, id ASC")
    suspend fun getCommandsForAggregate(aggregateId: String): List<PendingGroupCommandEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: PendingGroupCommandEntity)

    @Query("UPDATE pending_group_commands SET state = :newState, attempt_count = attempt_count + 1, last_error = :error WHERE id = :commandId")
    suspend fun updateState(commandId: String, newState: String, error: String? = null)

    @Query("DELETE FROM pending_group_commands WHERE id = :commandId")
    suspend fun delete(commandId: String)

    @Query("SELECT COUNT(*) FROM pending_group_commands WHERE state = 'PENDING'")
    suspend fun pendingCount(): Int
}
