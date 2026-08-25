package com.smartreminder.data.local.room.repository

import com.smartreminder.data.local.room.dao.RoutineDao
import com.smartreminder.data.local.room.mapper.RoutineItemMapper
import com.smartreminder.data.local.room.mapper.RoutineMapper
import com.smartreminder.data.local.room.mapper.RoutineOverrideMapper
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomRoutineRepository(
    private val dao: RoutineDao
) : RoutineRepository {

    override fun observeRoutines(): Flow<List<Routine>> {
        return dao.observeRoutines().map { list ->
            list.map { RoutineMapper.toRoutine(it) }
        }
    }

    override fun observeRoutinesByGroup(groupId: ScheduleGroupId): Flow<List<Routine>> {
        return dao.observeRoutinesByGroup(groupId.value).map { list ->
            list.map { RoutineMapper.toRoutine(it) }
        }
    }

    override suspend fun getRoutine(id: RoutineId): Routine? {
        return dao.getRoutineWithDetails(id.value)?.let { RoutineMapper.toRoutine(it) }
    }

    override suspend fun getRoutineDetails(id: RoutineId): RoutineDetails? {
        return dao.getRoutineWithDetails(id.value)?.let { RoutineMapper.toDetailsDomain(it) }
    }

    override suspend fun upsertRoutine(routine: Routine, items: List<RoutineItem>) {
        val routineEntity = RoutineMapper.toEntity(routine)
        val weeklyDayEntities = RoutineMapper.toWeeklyDayEntities(routine)
        val itemEntities = items.map { RoutineItemMapper.toEntity(it) }

        dao.upsertRoutineWithDetails(routineEntity, weeklyDayEntities, itemEntities)
    }

    override suspend fun getOverride(routineId: RoutineId, date: LocalDate): RoutineOverride? {
        return dao.getOverride(routineId.value, date.toEpochDay())?.let { RoutineOverrideMapper.toDomain(it) }
    }

    override suspend fun upsertOverride(override: RoutineOverride) {
        dao.upsertOverride(RoutineOverrideMapper.toEntity(override))
    }

    override suspend fun deleteOverride(routineId: RoutineId, date: LocalDate) {
        dao.deleteOverride(routineId.value, date.toEpochDay())
    }
}
