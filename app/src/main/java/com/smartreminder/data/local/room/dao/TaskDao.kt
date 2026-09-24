package com.smartreminder.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.smartreminder.data.local.room.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE scheduled_date_epoch_day = :epochDay ORDER BY scheduled_minute ASC, id ASC")
    fun observeTasksForDate(epochDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY scheduled_date_epoch_day ASC, scheduled_minute ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Upsert
    suspend fun upsertTask(task: TaskEntity)

    @Query("UPDATE tasks SET is_completed = :isCompleted WHERE id = :id")
    suspend fun updateTaskCompletion(id: String, isCompleted: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)
}
