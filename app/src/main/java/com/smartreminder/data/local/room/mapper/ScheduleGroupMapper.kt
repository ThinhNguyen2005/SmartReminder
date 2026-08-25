package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.ScheduleGroupEntity
import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import java.time.Instant

object ScheduleGroupMapper {

    fun toDomain(entity: ScheduleGroupEntity): ScheduleGroup {
        return ScheduleGroup(
            id = ScheduleGroupId(entity.id),
            name = entity.name,
            iconKey = entity.iconKey,
            colorKey = entity.colorKey,
            sortOrder = entity.sortOrder,
            isArchived = entity.isArchived,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt)
        )
    }

    fun toEntity(domain: ScheduleGroup): ScheduleGroupEntity {
        return ScheduleGroupEntity(
            id = domain.id.value,
            name = domain.name,
            iconKey = domain.iconKey,
            colorKey = domain.colorKey,
            sortOrder = domain.sortOrder,
            isArchived = domain.isArchived,
            createdAt = domain.createdAt.toEpochMilli(),
            updatedAt = domain.updatedAt.toEpochMilli()
        )
    }
}
