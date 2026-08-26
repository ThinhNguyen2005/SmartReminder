package com.smartreminder.ui.schedules.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineDetails
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import java.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutineEditorViewModel(
    private val scheduleGroupRepository: ScheduleGroupRepository,
    private val routineRepository: RoutineRepository,
    private val idGenerator: RoutineEditorIdGenerator,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutineEditorUiState())
    val uiState: StateFlow<RoutineEditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<RoutineEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var initializedEntryToken: Long? = null
    private var originalDetails: RoutineDetails? = null
    private var originalSnapshot: RoutineEditorEditableSnapshot? = null
    private var nextDraftKey: Long = 1L

    init {
        viewModelScope.launch {
            scheduleGroupRepository.observeGroups()
                .catch {
                    _uiState.update { state ->
                        state.copy(isLoading = false, saveError = RoutineEditorError.LOAD_FAILED)
                    }
                }
                .collect { groups ->
                    val groupModels = groups.map { RoutineEditorGroupUiModel(it.id, it.name) }
                    _uiState.update { state ->
                        state.copy(
                            groups = groupModels,
                            selectedGroupId = state.selectedGroupId.takeIf { selectedId ->
                                groupModels.any { it.id == selectedId }
                            }
                        )
                    }
                }
        }
    }

    fun onAction(action: RoutineEditorAction) {
        when (action) {
            is RoutineEditorAction.Initialize -> initialize(action.mode, action.entryToken)
            is RoutineEditorAction.ChangeName -> _uiState.update {
                it.copy(name = action.name, nameError = null, saveError = null)
            }
            is RoutineEditorAction.SelectGroup -> _uiState.update {
                it.copy(selectedGroupId = action.groupId, saveError = null)
            }
            is RoutineEditorAction.ToggleDay -> _uiState.update { state ->
                val updatedDays = state.selectedDays.toMutableSet().apply {
                    if (!add(action.day)) remove(action.day)
                }
                state.copy(selectedDays = updatedDays, daysError = null, saveError = null)
            }
            is RoutineEditorAction.SetEnabled -> _uiState.update {
                it.copy(enabled = action.enabled, saveError = null)
            }
            RoutineEditorAction.OpenAddItem -> openNewItemEditor()
            is RoutineEditorAction.OpenEditItem -> openExistingItemEditor(action.draftKey)
            is RoutineEditorAction.ChangeItemTitle -> _uiState.update { state ->
                state.copy(itemEditor = state.itemEditor?.copy(title = action.title, titleError = null))
            }
            is RoutineEditorAction.ChangeItemTime -> _uiState.update { state ->
                state.copy(itemEditor = state.itemEditor?.copy(scheduledTime = action.scheduledTime))
            }
            RoutineEditorAction.ConfirmItemEditor -> confirmItemEditor()
            RoutineEditorAction.DismissItemEditor -> _uiState.update { it.copy(itemEditor = null) }
            is RoutineEditorAction.RemoveItem -> _uiState.update { state ->
                state.copy(items = state.items.filterNot { it.draftKey == action.draftKey })
            }
            RoutineEditorAction.Save -> save()
            RoutineEditorAction.BackPressed -> handleBackPressed()
            RoutineEditorAction.ConfirmDiscard -> {
                _uiState.update { it.copy(showDiscardConfirmation = false) }
                emitEffect(RoutineEditorEffect.NavigateBack)
            }
            RoutineEditorAction.CancelDiscard -> _uiState.update { it.copy(showDiscardConfirmation = false) }
            RoutineEditorAction.DismissError -> _uiState.update { it.copy(saveError = null) }
        }
    }

    private fun initialize(mode: RoutineEditorMode, entryToken: Long) {
        if (initializedEntryToken == entryToken) return
        initializedEntryToken = entryToken
        originalDetails = null
        originalSnapshot = null
        nextDraftKey = 1L

        when (mode) {
            RoutineEditorMode.Create -> {
                _uiState.update { state ->
                    state.copy(
                        mode = mode,
                        isLoading = false,
                        name = "",
                        selectedGroupId = null,
                        selectedDays = emptySet(),
                        enabled = true,
                        items = emptyList(),
                        itemEditor = null,
                        nameError = null,
                        daysError = null,
                        isSaving = false,
                        saveError = null,
                        showDiscardConfirmation = false
                    )
                }
                originalSnapshot = _uiState.value.editableSnapshot()
            }
            is RoutineEditorMode.Edit -> loadForEdit(mode)
        }
    }

    private fun loadForEdit(mode: RoutineEditorMode.Edit) {
        _uiState.update { state ->
            state.copy(
                mode = mode,
                isLoading = true,
                itemEditor = null,
                nameError = null,
                daysError = null,
                isSaving = false,
                saveError = null,
                showDiscardConfirmation = false
            )
        }
        viewModelScope.launch {
            try {
                val details = routineRepository.getRoutineDetails(mode.routineId)
                if (details == null) {
                    _uiState.update { it.copy(isLoading = false, saveError = RoutineEditorError.LOAD_FAILED) }
                    return@launch
                }
                originalDetails = details
                val weeklyRule = details.routine.recurrence as? RecurrenceRule.Weekly
                val draftItems = details.items.mapIndexed { index, item ->
                    RoutineItemDraftUiModel(
                        draftKey = index + 1L,
                        existingItemId = item.id,
                        title = item.title,
                        scheduledTime = item.scheduledTime,
                        durationMinutes = item.durationMinutes,
                        enabled = item.enabled
                    )
                }
                nextDraftKey = draftItems.size + 1L
                _uiState.update { state ->
                    state.copy(
                        mode = mode,
                        isLoading = false,
                        name = details.routine.name,
                        selectedGroupId = details.routine.groupId.takeIf { routineGroupId ->
                            state.groups.any { it.id == routineGroupId }
                        },
                        selectedDays = weeklyRule?.days ?: emptySet(),
                        enabled = details.routine.enabled,
                        items = draftItems
                    )
                }
                originalSnapshot = _uiState.value.editableSnapshot()
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, saveError = RoutineEditorError.LOAD_FAILED) }
            }
        }
    }

    private fun openNewItemEditor() {
        _uiState.update {
            it.copy(itemEditor = RoutineItemEditorUiState(draftKey = null))
        }
    }

    private fun openExistingItemEditor(draftKey: Long) {
        val item = _uiState.value.items.firstOrNull { it.draftKey == draftKey } ?: return
        _uiState.update {
            it.copy(
                itemEditor = RoutineItemEditorUiState(
                    draftKey = item.draftKey,
                    title = item.title,
                    scheduledTime = item.scheduledTime
                )
            )
        }
    }

    private fun confirmItemEditor() {
        val editor = _uiState.value.itemEditor ?: return
        val normalizedTitle = editor.title.trim()
        if (normalizedTitle.isBlank()) {
            _uiState.update { state ->
                state.copy(itemEditor = editor.copy(titleError = RoutineEditorFieldError.ITEM_TITLE_REQUIRED))
            }
            return
        }

        _uiState.update { state ->
            val items = if (editor.draftKey == null) {
                state.items + RoutineItemDraftUiModel(
                    draftKey = nextDraftKey++,
                    existingItemId = null,
                    title = normalizedTitle,
                    scheduledTime = editor.scheduledTime,
                    durationMinutes = null,
                    enabled = true
                )
            } else {
                state.items.map { item ->
                    if (item.draftKey == editor.draftKey) {
                        item.copy(title = normalizedTitle, scheduledTime = editor.scheduledTime)
                    } else {
                        item
                    }
                }
            }
            state.copy(items = items, itemEditor = null)
        }
    }

    private fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        val normalizedName = state.name.trim()
        val nameError = if (normalizedName.isBlank()) RoutineEditorFieldError.NAME_REQUIRED else null
        val daysError = if (state.selectedDays.isEmpty()) RoutineEditorFieldError.DAYS_REQUIRED else null
        if (nameError != null || daysError != null) {
            _uiState.update {
                it.copy(nameError = nameError, daysError = daysError, saveError = null)
            }
            return
        }

        _uiState.update { it.copy(name = normalizedName, isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val savedDetails = buildSavedDetails(_uiState.value)
                routineRepository.upsertRoutine(savedDetails.routine, savedDetails.items)
                originalDetails = savedDetails
                _uiState.update { it.copy(isSaving = false) }
                originalSnapshot = _uiState.value.editableSnapshot()
                emitEffect(RoutineEditorEffect.Saved)
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = RoutineEditorError.SAVE_FAILED) }
            }
        }
    }

    private fun buildSavedDetails(state: RoutineEditorUiState): RoutineDetails {
        val recurrence = RecurrenceRule.Weekly(state.selectedDays)
        val existing = originalDetails
        val routine = when (val mode = state.mode) {
            RoutineEditorMode.Create -> Routine(
                id = idGenerator.newRoutineId(),
                groupId = state.selectedGroupId,
                name = state.name,
                recurrence = recurrence,
                enabled = state.enabled,
                createdAt = clock.instant(),
                updatedAt = clock.instant()
            )
            is RoutineEditorMode.Edit -> {
                val original = existing?.routine ?: error("Missing original routine for ${mode.routineId}")
                original.copy(
                    groupId = state.selectedGroupId,
                    name = state.name,
                    recurrence = recurrence,
                    enabled = state.enabled,
                    updatedAt = clock.instant()
                )
            }
            null -> error("Editor has not been initialized")
        }
        val originalItemsById = existing?.items?.associateBy { it.id }.orEmpty()
        val items = state.items.mapIndexed { index, draft ->
            val originalItem = draft.existingItemId?.let(originalItemsById::get)
            if (originalItem == null) {
                RoutineItem(
                    id = idGenerator.newRoutineItemId(),
                    routineId = routine.id,
                    title = draft.title,
                    scheduledTime = draft.scheduledTime,
                    durationMinutes = draft.durationMinutes,
                    sortOrder = index,
                    enabled = draft.enabled
                )
            } else {
                originalItem.copy(
                    routineId = routine.id,
                    title = draft.title,
                    scheduledTime = draft.scheduledTime,
                    sortOrder = index
                )
            }
        }
        return RoutineDetails(routine, items)
    }

    private fun handleBackPressed() {
        if (_uiState.value.editableSnapshot() == originalSnapshot) {
            emitEffect(RoutineEditorEffect.NavigateBack)
        } else {
            _uiState.update { it.copy(showDiscardConfirmation = true) }
        }
    }

    private fun emitEffect(effect: RoutineEditorEffect) {
        _effects.trySend(effect)
    }
}

private data class RoutineEditorEditableSnapshot(
    val name: String,
    val groupId: com.smartreminder.domain.model.schedule.ids.ScheduleGroupId?,
    val days: Set<java.time.DayOfWeek>,
    val enabled: Boolean,
    val items: List<RoutineItemEditableSnapshot>
)

private data class RoutineItemEditableSnapshot(
    val existingItemId: com.smartreminder.domain.model.schedule.ids.RoutineItemId?,
    val title: String,
    val scheduledTime: java.time.LocalTime
)

private fun RoutineEditorUiState.editableSnapshot(): RoutineEditorEditableSnapshot =
    RoutineEditorEditableSnapshot(
        name = name,
        groupId = selectedGroupId,
        days = selectedDays,
        enabled = enabled,
        items = items.map {
            RoutineItemEditableSnapshot(
                existingItemId = it.existingItemId,
                title = it.title,
                scheduledTime = it.scheduledTime
            )
        }
    )
