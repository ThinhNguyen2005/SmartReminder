package com.smartreminder.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.domain.model.task.Task
import com.smartreminder.domain.model.task.TaskPriority
import com.smartreminder.domain.model.today.ScheduleConflict
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.TaskRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.today.TodayTimelineResolver
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class TodayViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository,
    private val timeProvider: () -> LocalTime = { LocalTime.now() },
    private val dateProvider: () -> LocalDate = { LocalDate.now() },
    ticker: Flow<LocalTime>? = null
) : ViewModel() {

    private val selectedViewModeFlow = MutableStateFlow(TodayViewMode.TIMELINE)
    private val userManuallySelectedMode = MutableStateFlow(false)

    // Minute ticker to update "Now" indicator in real-time
    private val tickerFlow: Flow<LocalTime> = ticker ?: flow {
        while (true) {
            emit(timeProvider())
            delay(30_000) // update every 30s
        }
    }

    private val userProfileName: String
        get() {
            val user = try {
                SupabaseManager.client.auth.currentUserOrNull()
            } catch (e: Exception) {
                null
            }
            val meta = user?.userMetadata
            return meta?.get("full_name")?.jsonPrimitive?.contentOrNull
                ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
                ?: user?.email?.substringBefore("@")
                ?: "Alex"
        }

    private val userAvatarUrl: String?
        get() {
            val user = try {
                SupabaseManager.client.auth.currentUserOrNull()
            } catch (e: Exception) {
                null
            }
            return user?.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                ?: user?.userMetadata?.get("picture")?.jsonPrimitive?.contentOrNull
        }

    private data class AddTaskSheetState(val isShowing: Boolean = false, val defaultTime: LocalTime? = null)
    private val addTaskSheetFlow = MutableStateFlow(AddTaskSheetState())

    val uiState: StateFlow<TodayUiState> = combine(
        combine(
            userPreferencesRepository.preferences,
            routineRepository.observeRoutineDetails(),
            taskRepository.observeTasksForDate(dateProvider())
        ) { prefs, routines, tasks -> Triple(prefs, routines, tasks) },
        combine(
            selectedViewModeFlow,
            tickerFlow,
            addTaskSheetFlow
        ) { mode, ticker, sheetState -> Triple(mode, ticker, sheetState) }
    ) { (preferences, routines, tasks), (currentMode, currentTime, sheetState) ->
        val currentDate = dateProvider()

        val timelineResult = TodayTimelineResolver.resolve(
            currentDate = currentDate,
            currentTime = currentTime,
            wakeUpTime = preferences.wakeUpTime,
            sleepTime = preferences.sleepTime,
            routinesWithDetails = routines,
            routineOverrides = emptyList(),
            tasks = tasks
        )

        val effectiveMode = if (!userManuallySelectedMode.value && timelineResult.activeConflict != null) {
            TodayViewMode.BUSY_CONFLICT
        } else {
            currentMode
        }

        TodayUiState.Success(
            userName = userProfileName,
            userAvatarUrl = userAvatarUrl,
            currentDate = currentDate,
            currentTime = currentTime,
            progress = timelineResult.progress,
            aiSuggestion = timelineResult.aiSuggestion,
            timelineItems = timelineResult.items,
            activeConflict = timelineResult.activeConflict,
            selectedViewMode = effectiveMode,
            showAddTaskSheet = sheetState.isShowing,
            addTaskDefaultTime = sheetState.defaultTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState.Loading
    )

    fun onAction(action: TodayUiAction) {
        when (action) {
            is TodayUiAction.ChangeViewMode -> {
                userManuallySelectedMode.value = true
                selectedViewModeFlow.value = action.mode
            }
            is TodayUiAction.ToggleTaskCompletion -> {
                viewModelScope.launch {
                    taskRepository.toggleTaskCompletion(action.taskId, action.isCompleted)
                }
            }
            is TodayUiAction.AcceptFocusBlock -> {
                viewModelScope.launch {
                    val newTask = Task(
                        id = UUID.randomUUID().toString(),
                        title = action.taskTitle,
                        scheduledDate = dateProvider(),
                        scheduledTime = action.startTime,
                        durationMinutes = 90,
                        priority = TaskPriority.HIGH,
                        category = "Study"
                    )
                    taskRepository.upsertTask(newTask)
                }
            }
            is TodayUiAction.ResolveConflict -> {
                viewModelScope.launch {
                    val conflict = action.conflict
                    val targetTime = conflict.suggestedMoveTime ?: LocalTime.of(17, 0)
                    val eventId = conflict.conflictingEventId

                    if (eventId != null && eventId.startsWith("task_")) {
                        val realTaskId = eventId.removePrefix("task_")
                        val existingTask = taskRepository.getTask(realTaskId)
                        if (existingTask != null) {
                            taskRepository.upsertTask(existingTask.copy(scheduledTime = targetTime))
                        }
                    } else if (eventId == "sample_study_session" || conflict.conflictingTitle == "Study Session") {
                        val studyTask = Task(
                            id = UUID.randomUUID().toString(),
                            title = conflict.conflictingTitle,
                            scheduledDate = dateProvider(),
                            scheduledTime = targetTime,
                            durationMinutes = 60,
                            priority = TaskPriority.NORMAL,
                            category = "Study"
                        )
                        taskRepository.upsertTask(studyTask)
                    } else {
                        val allTasks = taskRepository.observeAllTasks().first()
                        val matching = allTasks.firstOrNull { it.title == conflict.suggestedItemTitle || it.title == conflict.conflictingTitle }
                        if (matching != null) {
                            taskRepository.upsertTask(matching.copy(scheduledTime = targetTime))
                        }
                    }

                    userManuallySelectedMode.value = true
                    selectedViewModeFlow.value = TodayViewMode.TIMELINE
                }
            }
            is TodayUiAction.AddEventClick -> {
                val defaultTime = action.defaultStartTime ?: timeProvider().plusMinutes(15)
                addTaskSheetFlow.value = AddTaskSheetState(isShowing = true, defaultTime = defaultTime)
            }
            TodayUiAction.DismissAddTask -> {
                addTaskSheetFlow.value = AddTaskSheetState(isShowing = false, defaultTime = null)
            }
            is TodayUiAction.CreateCustomTask -> {
                viewModelScope.launch {
                    val newTask = Task(
                        id = UUID.randomUUID().toString(),
                        title = action.title,
                        scheduledDate = dateProvider(),
                        scheduledTime = action.startTime,
                        durationMinutes = action.durationMinutes,
                        priority = action.priority,
                        category = action.category ?: "General"
                    )
                    taskRepository.upsertTask(newTask)
                    addTaskSheetFlow.value = AddTaskSheetState(isShowing = false, defaultTime = null)
                }
            }
            TodayUiAction.NotificationClick -> {
                // Handled in UI navigation or toast
            }
            TodayUiAction.ProfileAvatarClick -> {
                // Handled in UI navigation
            }
            TodayUiAction.Refresh -> {
                // Flow automatically refreshes on data change
            }
        }
    }
}
