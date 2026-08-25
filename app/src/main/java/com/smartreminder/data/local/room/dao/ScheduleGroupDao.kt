package com.smartreminder.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.smartreminder.data.local.room.entity.ScheduleGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleGroupDao {

    @Query("SELECT * FROM schedule_groups WHERE is_archived = 0 ORDER BY sort_order ASC, created_at ASC, id ASC")
    fun observeActiveGroups(): Flow<List<ScheduleGroupEntity>>

    @Query("SELECT * FROM schedule_groups ORDER BY sort_order ASC, created_at ASC, id ASC")
    fun observeAllGroups(): Flow<List<ScheduleGroupEntity>>

    @Query("SELECT * FROM schedule_groups WHERE id = :id")
    suspend fun getGroupById(id: String): ScheduleGroupEntity?

    @Upsert
    suspend fun upsertGroup(group: ScheduleGroupEntity)

    @Query("UPDATE schedule_groups SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun archiveGroup(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM schedule_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)
}
