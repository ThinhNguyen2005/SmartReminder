package com.smartreminder.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SchedulesViewModel(
    private val scheduleGroupRepository: ScheduleGroupRepository,
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<ScheduleGroupId?>(null)
    private val _error = MutableStateFlow<SchedulesError?>(null)

    private val _effects = Channel<SchedulesEffect>(Channel.BUFFERED)
    val effects: Flow<SchedulesEffect> = _effects.receiveAsFlow()

    private val routineUpdateMutex = Mutex()

    private val groupsFlow = scheduleGroupRepository
        .observeGroups()
        .onEach { groups ->
            // Normalize selection: if selected group was archived/deleted, fallback to null (All)
            _selectedGroupId.update { selected ->
                selected?.takeIf { selectedId ->
                    groups.any { it.id == selectedId }
                }
            }
        }

    val uiState: StateFlow<SchedulesUiState> = combine(
        groupsFlow,
        routineRepository.observeRoutineDetails(),
        _selectedGroupId,
        _error
    ) { groups, routineDetailsList, selectedGroupId, error ->
        val activeGroupMap = groups.associateBy { it.id }
        val groupFilters = groups.map { GroupFilterUiModel(it.id, it.name) }

        val sections = if (selectedGroupId != null) {
            val filteredDetails = routineDetailsList.filter { it.routine.groupId == selectedGroupId }
            val cards = filteredDetails.map { it.toCardUiModel() }
            if (cards.isNotEmpty()) {
                listOf(
                    RoutineSectionUiModel(
                        groupId = selectedGroupId,
                        label = null, // Single group filter view does not need redundant header
                        routines = cards
                    )
                )
            } else {
                emptyList()
            }
        } else {
            // Group routines by active group ID or Ungrouped (null)
            val grouped = routineDetailsList.groupBy { details ->
                val groupId = details.routine.groupId
                if (groupId != null && activeGroupMap.containsKey(groupId)) {
                    groupId
                } else {
                    null // Ungrouped (including orphaned/archived group routines)
                }
            }

            val resultSections = mutableListOf<RoutineSectionUiModel>()

            // 1. Add active group sections in deterministic sort order
            groups.forEach { group ->
                val itemsInGroup = grouped[group.id]?.map { it.toCardUiModel() } ?: emptyList()
                if (itemsInGroup.isNotEmpty()) {
                    resultSections.add(
                        RoutineSectionUiModel(
                            groupId = group.id,
                            label = RoutineSectionLabelUiModel.Group(group.name),
                            routines = itemsInGroup
                        )
                    )
                }
            }

            // 2. Add Ungrouped section if any exist
            val ungroupedItems = grouped[null]?.map { it.toCardUiModel() } ?: emptyList()
            if (ungroupedItems.isNotEmpty()) {
                resultSections.add(
                    RoutineSectionUiModel(
                        groupId = null,
                        label = RoutineSectionLabelUiModel.Ungrouped,
                        routines = ungroupedItems
                    )
                )
            }

            resultSections
        }

        SchedulesUiState(
            isLoading = false,
            selectedGroupId = selectedGroupId,
            groupFilters = groupFilters,
            sections = sections,
            error = error
        )
    }.catch {
        emit(SchedulesUiState(isLoading = false, error = SchedulesError.LOAD_FAILED))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SchedulesUiState(isLoading = true)
    )

    fun onAction(action: SchedulesAction) {
        when (action) {
            is SchedulesAction.SelectGroup -> {
                _selectedGroupId.value = action.groupId
            }
            is SchedulesAction.SetRoutineEnabled -> {
                setRoutineEnabled(action.routineId, action.enabled)
            }
            is SchedulesAction.OpenRoutine -> {
                _effects.trySend(SchedulesEffect.OpenRoutine(action.routineId))
            }
            SchedulesAction.CreateRoutine -> {
                _effects.trySend(SchedulesEffect.CreateRoutine)
            }
            SchedulesAction.ManageGroups -> {
                _effects.trySend(SchedulesEffect.ManageGroups)
            }
            SchedulesAction.DismissError -> {
                _error.value = null
            }
        }
    }

    private fun setRoutineEnabled(routineId: RoutineId, desiredEnabled: Boolean) {
        viewModelScope.launch {
            routineUpdateMutex.withLock {
                try {
                    val details = routineRepository.getRoutineDetails(routineId) ?: return@withLock
                    if (details.routine.enabled != desiredEnabled) {
                        routineRepository.upsertRoutine(
                            routine = details.routine.copy(enabled = desiredEnabled),
                            items = details.items
                        )
                    }
                } catch (e: Exception) {
                    _error.value = SchedulesError.UPDATE_ROUTINE_FAILED
                }
            }
        }
    }

    private fun RoutineDetails.toCardUiModel(): RoutineCardUiModel {
        val days = when (val recurrence = routine.recurrence) {
            is RecurrenceRule.Weekly -> recurrence.days
        }
        return RoutineCardUiModel(
            id = routine.id,
            name = routine.name,
            recurrence = RecurrenceUiModel(days),
            itemCount = items.size,
            enabled = routine.enabled
        )
    }
}
