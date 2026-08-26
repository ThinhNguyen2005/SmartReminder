package com.smartreminder.ui.schedules.editor

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
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var groupRepository: EditorFakeScheduleGroupRepository
    private lateinit var routineRepository: EditorFakeRoutineRepository
    private lateinit var idGenerator: FakeRoutineEditorIdGenerator
    private lateinit var viewModel: RoutineEditorViewModel

    private val studyGroup = ScheduleGroup(ScheduleGroupId("study"), "Study", sortOrder = 0)
    private val personalGroup = ScheduleGroup(ScheduleGroupId("personal"), "Personal", sortOrder = 1)
    private val fixedInstant = Instant.parse("2026-08-26T10:15:30Z")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        groupRepository = EditorFakeScheduleGroupRepository(listOf(studyGroup, personalGroup))
        routineRepository = EditorFakeRoutineRepository(emptyList())
        idGenerator = FakeRoutineEditorIdGenerator()
        viewModel = RoutineEditorViewModel(
            scheduleGroupRepository = groupRepository,
            routineRepository = routineRepository,
            idGenerator = idGenerator,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given valid create draft, when saving, then persists normalized aggregate and emits Saved`() = runTest(testDispatcher) {
        val effects = mutableListOf<RoutineEditorEffect>()
        val effectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        initializeCreate()

        viewModel.onAction(RoutineEditorAction.ChangeName("  Morning study  "))
        viewModel.onAction(RoutineEditorAction.SelectGroup(studyGroup.id))
        viewModel.onAction(RoutineEditorAction.ToggleDay(DayOfWeek.MONDAY))
        viewModel.onAction(RoutineEditorAction.ToggleDay(DayOfWeek.WEDNESDAY))
        viewModel.onAction(RoutineEditorAction.SetEnabled(false))
        viewModel.onAction(RoutineEditorAction.OpenAddItem)
        viewModel.onAction(RoutineEditorAction.ChangeItemTitle("Review notes"))
        viewModel.onAction(RoutineEditorAction.ChangeItemTime(LocalTime.of(7, 30)))
        viewModel.onAction(RoutineEditorAction.ConfirmItemEditor)
        viewModel.onAction(RoutineEditorAction.Save)
        advanceUntilIdle()

        val saved = routineRepository.lastUpsert!!
        assertEquals(RoutineId("routine-1"), saved.routine.id)
        assertEquals("Morning study", saved.routine.name)
        assertEquals(studyGroup.id, saved.routine.groupId)
        assertEquals(RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), saved.routine.recurrence)
        assertFalse(saved.routine.enabled)
        assertEquals(fixedInstant, saved.routine.createdAt)
        assertEquals(fixedInstant, saved.routine.updatedAt)
        assertEquals(1, saved.items.size)
        assertEquals(RoutineItemId("item-1"), saved.items.single().id)
        assertEquals(saved.routine.id, saved.items.single().routineId)
        assertEquals("Review notes", saved.items.single().title)
        assertEquals(LocalTime.of(7, 30), saved.items.single().scheduledTime)
        assertEquals(0, saved.items.single().sortOrder)
        assertTrue(saved.items.single().enabled)
        assertEquals(listOf(RoutineEditorEffect.Saved), effects)
        assertFalse(viewModel.uiState.value.isSaving)

        effectJob.cancel()
    }

    @Test
    fun `given existing routine, when editing and saving, then preserves immutable and item metadata`() = runTest(testDispatcher) {
        val routine = Routine(
            id = RoutineId("routine-existing"),
            groupId = studyGroup.id,
            name = "University Day",
            description = "Semester plan",
            iconKey = "book",
            colorKey = "purple",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            enabled = true,
            sortOrder = 6,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-02-01T00:00:00Z")
        )
        val existingItem = RoutineItem(
            id = RoutineItemId("item-existing"),
            routineId = routine.id,
            title = "Class",
            scheduledTime = LocalTime.of(8, 0),
            durationMinutes = 90,
            sortOrder = 4,
            enabled = false
        )
        routineRepository.setDetails(listOf(RoutineDetails(routine, listOf(existingItem))))
        initializeEdit(routine.id)

        viewModel.onAction(RoutineEditorAction.ChangeName("Updated university day"))
        viewModel.onAction(RoutineEditorAction.ToggleDay(DayOfWeek.FRIDAY))
        viewModel.onAction(RoutineEditorAction.OpenEditItem(viewModel.uiState.value.items.single().draftKey))
        viewModel.onAction(RoutineEditorAction.ChangeItemTitle("Lecture"))
        viewModel.onAction(RoutineEditorAction.ChangeItemTime(LocalTime.of(8, 15)))
        viewModel.onAction(RoutineEditorAction.ConfirmItemEditor)
        viewModel.onAction(RoutineEditorAction.OpenAddItem)
        viewModel.onAction(RoutineEditorAction.ChangeItemTitle("Library"))
        viewModel.onAction(RoutineEditorAction.ChangeItemTime(LocalTime.of(11, 0)))
        viewModel.onAction(RoutineEditorAction.ConfirmItemEditor)
        viewModel.onAction(RoutineEditorAction.Save)
        advanceUntilIdle()

        val saved = routineRepository.lastUpsert!!
        assertEquals(routine.id, saved.routine.id)
        assertEquals("Updated university day", saved.routine.name)
        assertEquals(routine.description, saved.routine.description)
        assertEquals(routine.iconKey, saved.routine.iconKey)
        assertEquals(routine.colorKey, saved.routine.colorKey)
        assertEquals(routine.sortOrder, saved.routine.sortOrder)
        assertEquals(routine.createdAt, saved.routine.createdAt)
        assertEquals(fixedInstant, saved.routine.updatedAt)
        assertEquals(RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY)), saved.routine.recurrence)
        assertEquals(2, saved.items.size)
        assertEquals(existingItem.id, saved.items[0].id)
        assertEquals("Lecture", saved.items[0].title)
        assertEquals(LocalTime.of(8, 15), saved.items[0].scheduledTime)
        assertEquals(existingItem.durationMinutes, saved.items[0].durationMinutes)
        assertEquals(existingItem.enabled, saved.items[0].enabled)
        assertEquals(0, saved.items[0].sortOrder)
        assertEquals(RoutineItemId("item-1"), saved.items[1].id)
        assertEquals(1, saved.items[1].sortOrder)
    }

    @Test
    fun `given archived initial group, when edit initializes, then normalizes selection to ungrouped`() = runTest(testDispatcher) {
        val routine = Routine(
            id = RoutineId("archived-group-routine"),
            groupId = ScheduleGroupId("archived"),
            name = "Workout",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.SATURDAY))
        )
        routineRepository.setDetails(listOf(RoutineDetails(routine, emptyList())))
        initializeEdit(routine.id)

        assertEquals(listOf(studyGroup.id, personalGroup.id), viewModel.uiState.value.groups.map { it.id })
        assertNull(viewModel.uiState.value.selectedGroupId)
    }

    @Test
    fun `given selected group becomes inactive, when groups refresh, then normalizes draft to ungrouped`() = runTest(testDispatcher) {
        initializeCreate()
        viewModel.onAction(RoutineEditorAction.SelectGroup(studyGroup.id))

        groupRepository.setGroups(listOf(personalGroup))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedGroupId)
    }

    @Test
    fun `given blank name or no selected day, when saving, then shows field errors without persisting`() = runTest(testDispatcher) {
        initializeCreate()

        viewModel.onAction(RoutineEditorAction.Save)
        advanceUntilIdle()

        assertEquals(RoutineEditorFieldError.NAME_REQUIRED, viewModel.uiState.value.nameError)
        assertEquals(RoutineEditorFieldError.DAYS_REQUIRED, viewModel.uiState.value.daysError)
        assertNull(routineRepository.lastUpsert)

        viewModel.onAction(RoutineEditorAction.ChangeName("Read"))
        viewModel.onAction(RoutineEditorAction.Save)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.nameError)
        assertEquals(RoutineEditorFieldError.DAYS_REQUIRED, viewModel.uiState.value.daysError)
        assertNull(routineRepository.lastUpsert)
    }

    @Test
    fun `given blank timeline title, when confirming item dialog, then keeps dialog open with title error`() = runTest(testDispatcher) {
        initializeCreate()

        viewModel.onAction(RoutineEditorAction.OpenAddItem)
        viewModel.onAction(RoutineEditorAction.ChangeItemTitle("   "))
        viewModel.onAction(RoutineEditorAction.ConfirmItemEditor)

        assertEquals(RoutineEditorFieldError.ITEM_TITLE_REQUIRED, viewModel.uiState.value.itemEditor?.titleError)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `given unchanged edit draft, when back pressed, then emits NavigateBack without confirmation`() = runTest(testDispatcher) {
        val routine = Routine(
            id = RoutineId("unchanged"),
            name = "Walk",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.SUNDAY))
        )
        routineRepository.setDetails(listOf(RoutineDetails(routine, emptyList())))
        val effects = mutableListOf<RoutineEditorEffect>()
        val effectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        initializeEdit(routine.id)

        viewModel.onAction(RoutineEditorAction.BackPressed)

        assertFalse(viewModel.uiState.value.showDiscardConfirmation)
        assertEquals(listOf(RoutineEditorEffect.NavigateBack), effects)
        effectJob.cancel()
    }

    @Test
    fun `given changed draft, when back pressed and discard confirmed, then confirms before navigating`() = runTest(testDispatcher) {
        val effects = mutableListOf<RoutineEditorEffect>()
        val effectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        initializeCreate()
        viewModel.onAction(RoutineEditorAction.ChangeName("New routine"))

        viewModel.onAction(RoutineEditorAction.BackPressed)
        assertTrue(viewModel.uiState.value.showDiscardConfirmation)
        assertTrue(effects.isEmpty())

        viewModel.onAction(RoutineEditorAction.ConfirmDiscard)
        assertEquals(listOf(RoutineEditorEffect.NavigateBack), effects)
        effectJob.cancel()
    }

    @Test
    fun `given active editor entry, when its route re-enters with the same token, then preserves unsaved draft`() = runTest(testDispatcher) {
        val routine = Routine(
            id = RoutineId("tab-return"),
            name = "Walk",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.SUNDAY))
        )
        routineRepository.setDetails(listOf(RoutineDetails(routine, emptyList())))
        initializeEdit(routine.id)
        viewModel.onAction(RoutineEditorAction.ChangeName("Long evening walk"))

        viewModel.onAction(
            RoutineEditorAction.Initialize(
                mode = RoutineEditorMode.Edit(routine.id),
                entryToken = 1L
            )
        )
        advanceUntilIdle()

        assertEquals("Long evening walk", viewModel.uiState.value.name)
    }

    @Test
    fun `given save repository failure, when saving, then exposes typed error and allows dismissal`() = runTest(testDispatcher) {
        routineRepository.throwOnUpsert = true
        initializeCreate()
        viewModel.onAction(RoutineEditorAction.ChangeName("Read"))
        viewModel.onAction(RoutineEditorAction.ToggleDay(DayOfWeek.TUESDAY))

        viewModel.onAction(RoutineEditorAction.Save)
        advanceUntilIdle()

        assertEquals(RoutineEditorError.SAVE_FAILED, viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.isSaving)
        viewModel.onAction(RoutineEditorAction.DismissError)
        assertNull(viewModel.uiState.value.saveError)
    }

    private fun initializeCreate() {
        viewModel.onAction(RoutineEditorAction.Initialize(RoutineEditorMode.Create, entryToken = 1L))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun initializeEdit(routineId: RoutineId) {
        viewModel.onAction(RoutineEditorAction.Initialize(RoutineEditorMode.Edit(routineId), entryToken = 1L))
        testDispatcher.scheduler.advanceUntilIdle()
    }
}

private class FakeRoutineEditorIdGenerator : RoutineEditorIdGenerator {
    private var routineSequence = 0
    private var itemSequence = 0

    override fun newRoutineId(): RoutineId = RoutineId("routine-${++routineSequence}")

    override fun newRoutineItemId(): RoutineItemId = RoutineItemId("item-${++itemSequence}")
}

private class EditorFakeScheduleGroupRepository(
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

private class EditorFakeRoutineRepository(
    initialDetails: List<RoutineDetails>
) : RoutineRepository {
    var throwOnUpsert: Boolean = false
    var lastUpsert: RoutineDetails? = null
    private val detailsFlow = MutableStateFlow(initialDetails)

    fun setDetails(details: List<RoutineDetails>) {
        detailsFlow.value = details
    }

    override fun observeRoutines(): Flow<List<Routine>> =
        detailsFlow.asStateFlow().map { details -> details.map { it.routine } }

    override fun observeRoutineDetails(): Flow<List<RoutineDetails>> = detailsFlow.asStateFlow()

    override fun observeRoutinesByGroup(groupId: ScheduleGroupId): Flow<List<Routine>> =
        detailsFlow.asStateFlow().map { details ->
            details.filter { it.routine.groupId == groupId }.map { it.routine }
        }

    override suspend fun getRoutine(id: RoutineId): Routine? =
        detailsFlow.value.firstOrNull { it.routine.id == id }?.routine

    override suspend fun getRoutineDetails(id: RoutineId): RoutineDetails? =
        detailsFlow.value.firstOrNull { it.routine.id == id }

    override suspend fun upsertRoutine(routine: Routine, items: List<RoutineItem>) {
        if (throwOnUpsert) throw IOException("write failed")
        lastUpsert = RoutineDetails(routine, items)
        detailsFlow.value = detailsFlow.value.filterNot { it.routine.id == routine.id } + lastUpsert!!
    }

    override suspend fun getOverride(routineId: RoutineId, date: LocalDate): RoutineOverride? = null

    override suspend fun upsertOverride(override: RoutineOverride) = Unit

    override suspend fun deleteOverride(routineId: RoutineId, date: LocalDate) = Unit
}
