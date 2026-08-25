package com.smartreminder.domain.repository

import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    fun observeRoutineDetails(): Flow<List<RoutineDetails>>
    fun observeRoutinesByGroup(groupId: ScheduleGroupId): Flow<List<Routine>>
    suspend fun getRoutine(id: RoutineId): Routine?
    suspend fun getRoutineDetails(id: RoutineId): RoutineDetails?
    suspend fun upsertRoutine(routine: Routine, items: List<RoutineItem> = emptyList())
    suspend fun getOverride(routineId: RoutineId, date: LocalDate): RoutineOverride?
    suspend fun upsertOverride(override: RoutineOverride)
    suspend fun deleteOverride(routineId: RoutineId, date: LocalDate)
}
