package com.smartreminder.ui.schedules

import androidx.lifecycle.ViewModel
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchedulesViewModelFactoryTest {

    @Test
    fun `given schedules dependencies, when factory creates ViewModel, then returns SchedulesViewModel`() {
        val factory = SchedulesViewModelFactory(
            scheduleGroupRepository = EmptyScheduleGroupRepository,
            routineRepository = EmptyRoutineRepository
        )

        val modelClass: Class<out ViewModel> = SchedulesViewModel::class.java
        val result = factory.create(modelClass)

        assertTrue(result is SchedulesViewModel)
    }
}

private object EmptyScheduleGroupRepository : ScheduleGroupRepository {
    override fun observeGroups(): Flow<List<ScheduleGroup>> = flowOf(emptyList())
    override suspend fun getGroup(id: ScheduleGroupId): ScheduleGroup? = null
    override suspend fun upsert(group: ScheduleGroup) = Unit
    override suspend fun archive(id: ScheduleGroupId) = Unit
}

private object EmptyRoutineRepository : RoutineRepository {
    override fun observeRoutines(): Flow<List<Routine>> = flowOf(emptyList())
    override fun observeRoutineDetails(): Flow<List<RoutineDetails>> = flowOf(emptyList())
    override fun observeRoutinesByGroup(groupId: ScheduleGroupId): Flow<List<Routine>> = flowOf(emptyList())
    override suspend fun getRoutine(id: RoutineId): Routine? = null
    override suspend fun getRoutineDetails(id: RoutineId): RoutineDetails? = null
    override suspend fun upsertRoutine(routine: Routine, items: List<RoutineItem>) = Unit
    override suspend fun getOverride(routineId: RoutineId, date: LocalDate): RoutineOverride? = null
    override suspend fun upsertOverride(override: RoutineOverride) = Unit
    override suspend fun deleteOverride(routineId: RoutineId, date: LocalDate) = Unit
}
