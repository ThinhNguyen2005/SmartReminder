package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.RoutineEntity
import com.smartreminder.data.local.room.entity.RoutineWeeklyDayEntity
import com.smartreminder.data.local.room.relation.RoutineWithDetailsEntity
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import java.time.DayOfWeek
import java.time.Instant

object RoutineMapper {

    fun toDomain(
        entity: RoutineEntity,
        weeklyDays: List<RoutineWeeklyDayEntity>
    ): Routine {
        val days = weeklyDays.map { dayEntity ->
            if (dayEntity.dayOfWeek !in 1..7) {
                throw IllegalStateException("Corrupt day_of_week ${dayEntity.dayOfWeek} in routine ${entity.id}")
            }
            DayOfWeek.of(dayEntity.dayOfWeek)
        }.toSet()

        val recurrence = if (days.isNotEmpty()) {
            RecurrenceRule.Weekly(days)
        } else {
            throw IllegalStateException("Routine ${entity.id} has no weekly days in database")
        }

        return Routine(
            id = RoutineId(entity.id),
            groupId = entity.groupId?.let { ScheduleGroupId(it) },
            name = entity.name,
            description = entity.description,
            iconKey = entity.iconKey,
            colorKey = entity.colorKey,
            recurrence = recurrence,
            enabled = entity.enabled,
            sortOrder = entity.sortOrder,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt)
        )
    }

    fun toEntity(domain: Routine): RoutineEntity {
        return RoutineEntity(
            id = domain.id.value,
            groupId = domain.groupId?.value,
            name = domain.name,
            description = domain.description,
            iconKey = domain.iconKey,
            colorKey = domain.colorKey,
            enabled = domain.enabled,
            sortOrder = domain.sortOrder,
            createdAt = domain.createdAt.toEpochMilli(),
            updatedAt = domain.updatedAt.toEpochMilli()
        )
    }

    fun toWeeklyDayEntities(domain: Routine): List<RoutineWeeklyDayEntity> {
        return when (val recurrence = domain.recurrence) {
            is RecurrenceRule.Weekly -> {
                recurrence.days.map { day ->
                    RoutineWeeklyDayEntity(
                        routineId = domain.id.value,
                        dayOfWeek = day.value
                    )
                }
            }
        }
    }

    fun toRoutine(withDetails: RoutineWithDetailsEntity): Routine {
        return toDomain(withDetails.routine, withDetails.weeklyDays)
    }

    fun toDetailsDomain(withDetails: RoutineWithDetailsEntity): RoutineDetails {
        val routine = toDomain(withDetails.routine, withDetails.weeklyDays)
        val items = withDetails.items
            .sortedWith(compareBy({ it.sortOrder }, { it.scheduledMinute }, { it.id }))
            .map { RoutineItemMapper.toDomain(it) }

        return RoutineDetails(
            routine = routine,
            items = items
        )
    }
}
