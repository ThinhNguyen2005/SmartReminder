package com.smartreminder.ui.schedules.editor

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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEditorViewModelFactoryTest {

    @Test
    fun `given editor dependencies, when factory creates ViewModel, then returns RoutineEditorViewModel`() {
        val factory = RoutineEditorViewModelFactory(
            scheduleGroupRepository = EmptyEditorScheduleGroupRepository,
            routineRepository = EmptyEditorRoutineRepository,
            idGenerator = FactoryRoutineEditorIdGenerator,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        )

        val modelClass: Class<out ViewModel> = RoutineEditorViewModel::class.java
        val result = factory.create(modelClass)

        assertTrue(result is RoutineEditorViewModel)
    }

    @Test
    fun `given unsupported class, when factory creates ViewModel, then throws IllegalArgumentException`() {
        val factory = RoutineEditorViewModelFactory(
            scheduleGroupRepository = EmptyEditorScheduleGroupRepository,
            routineRepository = EmptyEditorRoutineRepository,
            idGenerator = FactoryRoutineEditorIdGenerator,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        )

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnsupportedEditorViewModel::class.java)
        }
    }
}

private class UnsupportedEditorViewModel : ViewModel()

private object FactoryRoutineEditorIdGenerator : RoutineEditorIdGenerator {
    override fun newRoutineId(): RoutineId = RoutineId("routine")

    override fun newRoutineItemId() = com.smartreminder.domain.model.schedule.ids.RoutineItemId("item")
}

private object EmptyEditorScheduleGroupRepository : ScheduleGroupRepository {
    override fun observeGroups(): Flow<List<ScheduleGroup>> = flowOf(emptyList())
    override suspend fun getGroup(id: ScheduleGroupId): ScheduleGroup? = null
    override suspend fun upsert(group: ScheduleGroup) = Unit
    override suspend fun archive(id: ScheduleGroupId) = Unit
}

private object EmptyEditorRoutineRepository : RoutineRepository {
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
