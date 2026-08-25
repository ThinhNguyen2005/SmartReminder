package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.RoutineOverrideEntity
import com.smartreminder.domain.model.schedule.OverrideType
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ids.RoutineId
import java.time.LocalDate

object RoutineOverrideMapper {

    fun toDomain(entity: RoutineOverrideEntity): RoutineOverride {
        val overrideType = OverrideType.fromStorageKey(entity.overrideType)
            ?: throw IllegalStateException("Corrupt override_type '${entity.overrideType}' in routine_overrides for routine ${entity.routineId}")

        return RoutineOverride(
            routineId = RoutineId(entity.routineId),
            date = LocalDate.ofEpochDay(entity.overrideDateEpochDay),
            type = overrideType
        )
    }

    fun toEntity(domain: RoutineOverride): RoutineOverrideEntity {
        return RoutineOverrideEntity(
            routineId = domain.routineId.value,
            overrideDateEpochDay = domain.date.toEpochDay(),
            overrideType = domain.type.storageKey
        )
    }
}
