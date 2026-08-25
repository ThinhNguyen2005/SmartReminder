package com.smartreminder.ui.schedules

import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var groupRepository: FakeScheduleGroupRepository
    private lateinit var routineRepository: FakeRoutineRepository
    private lateinit var viewModel: SchedulesViewModel

    private val groupStudy = ScheduleGroup(ScheduleGroupId("group_study"), "Study", sortOrder = 0)
    private val groupPersonal = ScheduleGroup(ScheduleGroupId("group_personal"), "Personal", sortOrder = 1)

    private val routineUniv = Routine(
        id = RoutineId("univ"),
        groupId = groupStudy.id,
        name = "University Day",
        recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        enabled = true
    )
    private val itemUniv1 = RoutineItem(RoutineItemId("i1"), routineUniv.id, "Class", LocalTime.of(8, 0))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        groupRepository = FakeScheduleGroupRepository(listOf(groupStudy, groupPersonal))
        routineRepository = FakeRoutineRepository(listOf(RoutineDetails(routineUniv, listOf(itemUniv1))))
        viewModel = SchedulesViewModel(groupRepository, routineRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given repository initial state, when observing uiState, then populates groups and sections`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.selectedGroupId)
        assertEquals(2, state.groupFilters.size)
        assertEquals(1, state.sections.size)
        assertEquals("Study", (state.sections[0].label as RoutineSectionLabelUiModel.Group).name)
        assertEquals(1, state.sections[0].routines.size)
        assertEquals("University Day", state.sections[0].routines[0].name)
        assertEquals(1, state.sections[0].routines[0].itemCount)

        collectJob.cancel()
    }

    @Test
    fun `given group selected, when observing uiState, then filters routines to selected group with null header`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onAction(SchedulesAction.SelectGroup(groupStudy.id))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(groupStudy.id, state.selectedGroupId)
        assertEquals(1, state.sections.size)
        assertNull(state.sections[0].label) // Filtered group has null label to avoid redundant header
        assertEquals("University Day", state.sections[0].routines[0].name)

        collectJob.cancel()
    }

    @Test
    fun `given routine without group, when observing All, then places routine under Ungrouped section`() = runTest(testDispatcher) {
        val ungroupedRoutine = Routine(
            id = RoutineId("walk"),
            groupId = null,
            name = "Evening Walk",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.SUNDAY))
        )
        routineRepository.setDetails(listOf(
            RoutineDetails(routineUniv, listOf(itemUniv1)),
            RoutineDetails(ungroupedRoutine, emptyList())
        ))

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.sections.size)
        assertEquals(RoutineSectionLabelUiModel.Ungrouped, state.sections[1].label)
        assertEquals("Evening Walk", state.sections[1].routines[0].name)

        collectJob.cancel()
    }

    @Test
    fun `given routine referencing archived or missing group, when observing All, then preserves routine under Ungrouped section`() = runTest(testDispatcher) {
        val archivedGroupRoutine = Routine(
            id = RoutineId("gym"),
            groupId = ScheduleGroupId("archived_group"),
            name = "Gym Session",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.TUESDAY))
        )
        routineRepository.setDetails(listOf(
            RoutineDetails(routineUniv, listOf(itemUniv1)),
            RoutineDetails(archivedGroupRoutine, emptyList())
        ))

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.sections.size)
        assertEquals(RoutineSectionLabelUiModel.Ungrouped, state.sections[1].label)
        assertEquals("Gym Session", state.sections[1].routines[0].name)

        collectJob.cancel()
    }

    @Test
    fun `given selected group is archived, when groups emit, then selectedGroupId automatically falls back to null`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onAction(SchedulesAction.SelectGroup(groupPersonal.id))
        advanceUntilIdle()
        assertEquals(groupPersonal.id, viewModel.uiState.value.selectedGroupId)

        // When groupPersonal is archived / removed from active groups
        groupRepository.setGroups(listOf(groupStudy))
        advanceUntilIdle()

        // Then selectedGroupId falls back to null (All)
        assertNull(viewModel.uiState.value.selectedGroupId)

        collectJob.cancel()
    }

    @Test
    fun `given routine items change in repository, then itemCount updates reactively`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.sections[0].routines[0].itemCount)

        // When item is added
        val itemUniv2 = RoutineItem(RoutineItemId("i2"), routineUniv.id, "Lab", LocalTime.of(10, 0))
        routineRepository.setDetails(listOf(RoutineDetails(routineUniv, listOf(itemUniv1, itemUniv2))))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.sections[0].routines[0].itemCount)

        collectJob.cancel()
    }

    @Test
    fun `given routine enabled, when SetRoutineEnabled false, then upserts routine with enabled false and preserves items`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onAction(SchedulesAction.SetRoutineEnabled(routineUniv.id, false))
        advanceUntilIdle()

        val updated = routineRepository.getRoutine(routineUniv.id)
        assertNotNull(updated)
        assertFalse(updated!!.enabled)
        assertEquals(1, routineRepository.getRoutineDetails(routineUniv.id)?.items?.size)

        collectJob.cancel()
    }

    @Test
    fun `given rapid SetRoutineEnabled calls, when executed, then updates serialize and final state matches last call`() = runTest(testDispatcher) {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Rapid dispatch: true then false
        viewModel.onAction(SchedulesAction.SetRoutineEnabled(routineUniv.id, false))
        viewModel.onAction(SchedulesAction.SetRoutineEnabled(routineUniv.id, true))
        viewModel.onAction(SchedulesAction.SetRoutineEnabled(routineUniv.id, false))
        advanceUntilIdle()

        val finalRoutine = routineRepository.getRoutine(routineUniv.id)
        assertFalse(finalRoutine!!.enabled)

        collectJob.cancel()
    }

    @Test
    fun `given repository write throws exception, when SetRoutineEnabled called, then error is UPDATE_ROUTINE_FAILED`() = runTest(testDispatcher) {
        routineRepository.throwOnUpsert = true
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onAction(SchedulesAction.SetRoutineEnabled(routineUniv.id, false))
        advanceUntilIdle()

        assertEquals(SchedulesError.UPDATE_ROUTINE_FAILED, viewModel.uiState.value.error)

        // Dismiss error
        viewModel.onAction(SchedulesAction.DismissError)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)

        collectJob.cancel()
    }

    @Test
    fun `given actions dispatched, when open, create, manage actions sent, then emits corresponding effects`() = runTest(testDispatcher) {
        var lastEffect: SchedulesEffect? = null
        val effectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { lastEffect = it }
        }

        viewModel.onAction(SchedulesAction.OpenRoutine(routineUniv.id))
        assertEquals(SchedulesEffect.OpenRoutine(routineUniv.id), lastEffect)

        viewModel.onAction(SchedulesAction.CreateRoutine)
        assertEquals(SchedulesEffect.CreateRoutine, lastEffect)

        viewModel.onAction(SchedulesAction.ManageGroups)
        assertEquals(SchedulesEffect.ManageGroups, lastEffect)

        effectJob.cancel()
    }

    @Test
    fun `given repository observation throws exception, when uiState observed, then error is LOAD_FAILED and isLoading is false`() = runTest(testDispatcher) {
        val failingGroupRepo = object : ScheduleGroupRepository {
            override fun observeGroups(): Flow<List<ScheduleGroup>> = flow { throw IOException("DB Read Error") }
            override suspend fun getGroup(id: ScheduleGroupId): ScheduleGroup? = null
            override suspend fun upsert(group: ScheduleGroup) {}
            override suspend fun archive(id: ScheduleGroupId) {}
        }
        val failingVm = SchedulesViewModel(failingGroupRepo, routineRepository)

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            failingVm.uiState.collect {}
        }
        advanceUntilIdle()

        assertFalse(failingVm.uiState.value.isLoading)
        assertEquals(SchedulesError.LOAD_FAILED, failingVm.uiState.value.error)

        collectJob.cancel()
    }
}

private class FakeScheduleGroupRepository(
    initialGroups: List<ScheduleGroup>
) : ScheduleGroupRepository {
    private val groupsFlow = MutableStateFlow(initialGroups)

    fun setGroups(groups: List<ScheduleGroup>) {
        groupsFlow.value = groups
    }

    override fun observeGroups(): Flow<List<ScheduleGroup>> = groupsFlow.asStateFlow()

    override suspend fun getGroup(id: ScheduleGroupId): ScheduleGroup? =
        groupsFlow.value.firstOrNull { it.id == id }

    override suspend fun upsert(group: ScheduleGroup) {
        groupsFlow.value = groupsFlow.value.filterNot { it.id == group.id } + group
    }

    override suspend fun archive(id: ScheduleGroupId) {
        groupsFlow.value = groupsFlow.value.filterNot { it.id == id }
    }
}

private class FakeRoutineRepository(
    initialDetails: List<RoutineDetails>
) : RoutineRepository {
    var throwOnUpsert: Boolean = false
    private val detailsFlow = MutableStateFlow(initialDetails)

    fun setDetails(details: List<RoutineDetails>) {
        detailsFlow.value = details
    }

    override fun observeRoutines(): Flow<List<Routine>> =
        detailsFlow.asStateFlow().map { list -> list.map { it.routine } }

    override fun observeRoutineDetails(): Flow<List<RoutineDetails>> = detailsFlow.asStateFlow()

    override fun observeRoutinesByGroup(groupId: ScheduleGroupId): Flow<List<Routine>> =
        detailsFlow.asStateFlow().map { list ->
            list.filter { it.routine.groupId == groupId }.map { it.routine }
        }

    override suspend fun getRoutine(id: RoutineId): Routine? =
        detailsFlow.value.firstOrNull { it.routine.id == id }?.routine

    override suspend fun getRoutineDetails(id: RoutineId): RoutineDetails? =
        detailsFlow.value.firstOrNull { it.routine.id == id }

    override suspend fun upsertRoutine(routine: Routine, items: List<RoutineItem>) {
        if (throwOnUpsert) throw IOException("Database write failed")
        val updatedList = detailsFlow.value.filterNot { it.routine.id == routine.id } +
                RoutineDetails(routine, items)
        detailsFlow.value = updatedList
    }

    override suspend fun getOverride(routineId: RoutineId, date: LocalDate): RoutineOverride? = null
    override suspend fun upsertOverride(override: RoutineOverride) {}
    override suspend fun deleteOverride(routineId: RoutineId, date: LocalDate) {}
}
