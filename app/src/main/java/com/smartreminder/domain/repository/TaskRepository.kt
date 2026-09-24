package com.smartreminder.domain.repository

import com.smartreminder.domain.model.task.Task
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    fun observeTasksForDate(date: LocalDate): Flow<List<Task>>
    fun observeAllTasks(): Flow<List<Task>>
    suspend fun getTask(id: String): Task?
    suspend fun upsertTask(task: Task)
    suspend fun toggleTaskCompletion(id: String, isCompleted: Boolean)
    suspend fun deleteTask(id: String)
}
