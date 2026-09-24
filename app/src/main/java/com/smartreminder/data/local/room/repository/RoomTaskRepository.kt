package com.smartreminder.data.local.room.repository

import com.smartreminder.data.local.room.dao.TaskDao
import com.smartreminder.data.local.room.mapper.TaskMapper
import com.smartreminder.domain.model.task.Task
import com.smartreminder.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomTaskRepository(
    private val dao: TaskDao
) : TaskRepository {

    override fun observeTasksForDate(date: LocalDate): Flow<List<Task>> {
        return dao.observeTasksForDate(date.toEpochDay()).map { list ->
            list.map { TaskMapper.toDomain(it) }
        }
    }

    override fun observeAllTasks(): Flow<List<Task>> {
        return dao.observeAllTasks().map { list ->
            list.map { TaskMapper.toDomain(it) }
        }
    }

    override suspend fun getTask(id: String): Task? {
        return dao.getTaskById(id)?.let { TaskMapper.toDomain(it) }
    }

    override suspend fun upsertTask(task: Task) {
        dao.upsertTask(TaskMapper.toEntity(task))
    }

    override suspend fun toggleTaskCompletion(id: String, isCompleted: Boolean) {
        dao.updateTaskCompletion(id, isCompleted)
    }

    override suspend fun deleteTask(id: String) {
        dao.deleteTask(id)
    }
}
