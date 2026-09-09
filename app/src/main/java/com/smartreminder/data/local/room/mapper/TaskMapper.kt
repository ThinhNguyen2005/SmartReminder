package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.TaskEntity
import com.smartreminder.domain.model.task.Task
import com.smartreminder.domain.model.task.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object TaskMapper {

    fun toDomain(entity: TaskEntity): Task {
        val hour = (entity.scheduledMinute / 60).coerceIn(0, 23)
        val minute = (entity.scheduledMinute % 60).coerceIn(0, 59)
        val priority = runCatching { TaskPriority.valueOf(entity.priority) }.getOrDefault(TaskPriority.NORMAL)

        return Task(
            id = entity.id,
            title = entity.title,
            scheduledDate = LocalDate.ofEpochDay(entity.scheduledDateEpochDay),
            scheduledTime = LocalTime.of(hour, minute),
            durationMinutes = entity.durationMinutes,
            isCompleted = entity.isCompleted,
            priority = priority,
            category = entity.category,
            isVirtual = entity.isVirtual,
            attendees = entity.attendees
        )
    }

    fun toEntity(domain: Task, createdAt: Long = Instant.now().toEpochMilli()): TaskEntity {
        val minuteOfDay = domain.scheduledTime.hour * 60 + domain.scheduledTime.minute

        return TaskEntity(
            id = domain.id,
            title = domain.title,
            scheduledDateEpochDay = domain.scheduledDate.toEpochDay(),
            scheduledMinute = minuteOfDay,
            durationMinutes = domain.durationMinutes,
            isCompleted = domain.isCompleted,
            priority = domain.priority.name,
            category = domain.category,
            isVirtual = domain.isVirtual,
            attendees = domain.attendees,
            createdAt = createdAt
        )
    }
}
