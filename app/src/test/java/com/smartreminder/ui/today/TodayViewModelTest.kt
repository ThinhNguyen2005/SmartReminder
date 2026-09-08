package com.smartreminder.ui.today

import com.smartreminder.domain.model.preferences.OnboardingPreferencesSnapshot
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.model.preferences.UserPreferences
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.domain.model.task.Task
import com.smartreminder.domain.model.task.TaskPriority
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.TaskRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var preferencesRepo: FakeUserPreferencesRepository
    private lateinit var routineRepo: FakeRoutineRepository
    private lateinit var taskRepo: FakeTaskRepository
    private lateinit var viewModel: TodayViewModel

    private val monday = LocalDate.of(2026, 8, 24)
    private val fixedTime = LocalTime.of(10, 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferencesRepo = FakeUserPreferencesRepository()
        routineRepo = FakeRoutineRepository()
        taskRepo = FakeTaskRepository()

        viewModel = TodayViewModel(
            userPreferencesRepository = preferencesRepo,
            routineRepository = routineRepo,
            taskRepository = taskRepo,
            timeProvider = { fixedTime },
            dateProvider = { monday },
            ticker = kotlinx.coroutines.flow.flowOf(fixedTime)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given empty routines and tasks, when uiState is collected, then emits Success with empty progress and free slot`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        val success = state as TodayUiState.Success
        assertEquals(0, success.progress.totalCount)
        assertEquals(0, success.progress.completedCount)
        assertTrue(success.timelineItems.isNotEmpty()) // Has at least 1 free slot covering the day

        collectJob.cancel()
    }

    @Test
    fun `given initial state, when user changes view mode, then selectedViewMode updates`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(TodayUiAction.ChangeViewMode(TodayViewMode.OVERVIEW))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(TodayViewMode.OVERVIEW, state.selectedViewMode)

        collectJob.cancel()
    }

    @Test
    fun `given existing task, when toggling completion, then repository is updated`() = testScope.runTest {
        val task = Task(
            id = "task_1",
            title = "Android assignment",
            scheduledDate = monday,
            scheduledTime = LocalTime.of(9, 0),
            isCompleted = false
        )
        taskRepo.upsertTask(task)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(TodayUiAction.ToggleTaskCompletion("task_1", true))
        advanceUntilIdle()

        val updatedTask = taskRepo.getTask("task_1")
        assertTrue(updatedTask?.isCompleted == true)

        collectJob.cancel()
    }

    @Test
    fun `given focus block suggestion, when user accepts it, then new task is scheduled`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(TodayUiAction.AcceptFocusBlock("Android assignment", LocalTime.of(10, 30)))
        advanceUntilIdle()

        val tasks = taskRepo.observeAllTasks().first()
        assertTrue(tasks.any { it.title == "Android assignment" && it.scheduledTime == LocalTime.of(10, 30) })

        collectJob.cancel()
    }

    @Test
    fun `given initial state, when user changes to MINIMAL_STREAM mode, then selectedViewMode updates`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(TodayUiAction.ChangeViewMode(TodayViewMode.MINIMAL_STREAM))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(TodayViewMode.MINIMAL_STREAM, state.selectedViewMode)

        collectJob.cancel()
    }

    @Test
    fun `given conflicting events in schedule, when resolving today state, then auto-selects BUSY_CONFLICT mode`() = testScope.runTest {
        val event1 = Task(
            id = "event_1",
            title = "Project Meeting",
            scheduledDate = monday,
            scheduledTime = LocalTime.of(14, 30),
            durationMinutes = 60
        )
        val event2 = Task(
            id = "event_2",
            title = "Study Session",
            scheduledDate = monday,
            scheduledTime = LocalTime.of(14, 30),
            durationMinutes = 60
        )
        taskRepo.upsertTask(event1)
        taskRepo.upsertTask(event2)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(TodayViewMode.BUSY_CONFLICT, state.selectedViewMode)
        assertTrue(state.activeConflict != null)
        assertEquals("Project Meeting", state.activeConflict?.primaryTitle)
        assertEquals("Study Session", state.activeConflict?.conflictingTitle)

        collectJob.cancel()
    }

    @Test
    fun `given FAB quick create action, when triggered, then opens add task sheet with default time`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(TodayUiAction.AddEventClick(LocalTime.of(16, 0)))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertTrue(state.showAddTaskSheet)
        assertEquals(LocalTime.of(16, 0), state.addTaskDefaultTime)

        viewModel.onAction(TodayUiAction.DismissAddTask)
        advanceUntilIdle()
        val dismissedState = viewModel.uiState.value as TodayUiState.Success
        assertTrue(!dismissedState.showAddTaskSheet)

        collectJob.cancel()
    }

    @Test
    fun `given custom task parameters, when CreateCustomTask is triggered, then new task is persisted`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(
            TodayUiAction.CreateCustomTask(
                title = "Học bài Kotlin",
                startTime = LocalTime.of(15, 30),
                durationMinutes = 45,
                priority = TaskPriority.HIGH,
                category = "Study"
            )
        )
        advanceUntilIdle()

        val tasks = taskRepo.observeAllTasks().first()
        val created = tasks.firstOrNull { it.title == "Học bài Kotlin" }
        assertTrue(created != null)
        assertEquals(LocalTime.of(15, 30), created?.scheduledTime)
        assertEquals(45, created?.durationMinutes)
        assertEquals(TaskPriority.HIGH, created?.priority)

        collectJob.cancel()
    }

    @Test
    fun `given active conflict, when ResolveConflict is triggered, then conflicting event is rescheduled and viewMode changes to TIMELINE`() = testScope.runTest {
        val event1 = Task(
            id = "task_event_1",
            title = "Project Meeting",
            scheduledDate = monday,
            scheduledTime = LocalTime.of(14, 30),
            durationMinutes = 60
        )
        val event2 = Task(
            id = "task_event_2",
            title = "Study Session",
            scheduledDate = monday,
            scheduledTime = LocalTime.of(14, 30),
            durationMinutes = 60
        )
        taskRepo.upsertTask(event1)
        taskRepo.upsertTask(event2)

        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value as TodayUiState.Success
        val conflict = stateBefore.activeConflict
        assertTrue(conflict != null)

        viewModel.onAction(TodayUiAction.ResolveConflict(conflict!!))
        advanceUntilIdle()

        val stateAfter = viewModel.uiState.value as TodayUiState.Success
        assertEquals(TodayViewMode.TIMELINE, stateAfter.selectedViewMode)

        val rescheduledEvent = taskRepo.getTask("task_event_2")
        assertTrue(rescheduledEvent != null)
        assertEquals(conflict.suggestedMoveTime, rescheduledEvent?.scheduledTime)

        collectJob.cancel()
    }
}

// ============================================================================
// FAKES
// ============================================================================

private class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val _prefs = MutableStateFlow(
        UserPreferences(
            wakeUpTime = LocalTime.of(7, 0),
            sleepTime = LocalTime.of(23, 0)
        )
    )
    override val preferences: Flow<UserPreferences> = _prefs.asStateFlow()

    override suspend fun completeOnboarding(wakeUpTime: LocalTime, sleepTime: LocalTime, goals: Set<UserGoal>) {
        _prefs.value = _prefs.value.copy(wakeUpTime = wakeUpTime, sleepTime = sleepTime, goals = goals, onboardingCompleted = true)
    }

    override suspend fun updateRhythm(wakeUpTime: LocalTime, sleepTime: LocalTime) {
        _prefs.value = _prefs.value.copy(wakeUpTime = wakeUpTime, sleepTime = sleepTime)
    }

    override suspend fun updateGoals(goals: Set<UserGoal>) {}
    override suspend fun updateThemeMode(mode: ThemeMode) {}
    override suspend fun resetOnboarding() {}
    override suspend fun replaceOnboardingPreferences(snapshot: OnboardingPreferencesSnapshot) {}
    override suspend fun clearOnboardingPreferences() {}
}

private class FakeRoutineRepository : RoutineRepository {
    private val routines = MutableStateFlow<List<RoutineDetails>>(emptyList())

    override fun observeRoutines(): Flow<List<Routine>> = routines.map { it.map { d -> d.routine } }
    override fun observeRoutineDetails(): Flow<List<RoutineDetails>> = routines.asStateFlow()
    override fun observeRoutinesByGroup(groupId: ScheduleGroupId): Flow<List<Routine>> = routines.map { emptyList() }
    override suspend fun getRoutine(id: RoutineId): Routine? = null
    override suspend fun getRoutineDetails(id: RoutineId): RoutineDetails? = null
    override suspend fun upsertRoutine(routine: Routine, items: List<RoutineItem>) {}
    override suspend fun getOverride(routineId: RoutineId, date: LocalDate): RoutineOverride? = null
    override suspend fun upsertOverride(override: RoutineOverride) {}
    override suspend fun deleteOverride(routineId: RoutineId, date: LocalDate) {}
}

private class FakeTaskRepository : TaskRepository {
    private val tasksMap = mutableMapOf<String, Task>()
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())

    override fun observeTasksForDate(date: LocalDate): Flow<List<Task>> {
        return tasksFlow.map { list -> list.filter { it.scheduledDate == date } }
    }

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow.asStateFlow()

    override suspend fun getTask(id: String): Task? = tasksMap[id]

    override suspend fun upsertTask(task: Task) {
        tasksMap[task.id] = task
        tasksFlow.value = tasksMap.values.toList()
    }

    override suspend fun toggleTaskCompletion(id: String, isCompleted: Boolean) {
        tasksMap[id]?.let {
            tasksMap[id] = it.copy(isCompleted = isCompleted)
            tasksFlow.value = tasksMap.values.toList()
        }
    }

    override suspend fun deleteTask(id: String) {
        tasksMap.remove(id)
        tasksFlow.value = tasksMap.values.toList()
    }
}
