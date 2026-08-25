package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.RoutineItemEntity
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import java.time.LocalTime

object RoutineItemMapper {

    fun toDomain(entity: RoutineItemEntity): RoutineItem {
        if (entity.scheduledMinute !in 0..1439) {
            throw IllegalStateException("Corrupt scheduled_minute ${entity.scheduledMinute} in routine_item ${entity.id}")
        }
        val hour = entity.scheduledMinute / 60
        val minute = entity.scheduledMinute % 60

        return RoutineItem(
            id = RoutineItemId(entity.id),
            routineId = RoutineId(entity.routineId),
            title = entity.title,
            scheduledTime = LocalTime.of(hour, minute),
            durationMinutes = entity.durationMinutes,
            sortOrder = entity.sortOrder,
            enabled = entity.enabled
        )
    }

    fun toEntity(domain: RoutineItem): RoutineItemEntity {
        val minuteOfDay = domain.scheduledTime.hour * 60 + domain.scheduledTime.minute

        return RoutineItemEntity(
            id = domain.id.value,
            routineId = domain.routineId.value,
            title = domain.title,
            scheduledMinute = minuteOfDay,
            durationMinutes = domain.durationMinutes,
            sortOrder = domain.sortOrder,
            enabled = domain.enabled
        )
    }
}
