package com.smartreminder.ui.groups.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.smartreminder.domain.collaboration.GroupTaskPolicy
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.CancelGroupTaskCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.repository.CompleteGroupTaskCommand
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.EditGroupTaskCommand
import com.smartreminder.domain.repository.ReassignGroupTaskCommand
import com.smartreminder.domain.repository.ReopenGroupTaskCommand
import com.smartreminder.domain.repository.StartGroupTaskCommand
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GroupTasksViewModel(
    private val repository: CollaborationRepository,
    private val clock: Clock,
    private val idGenerator: GroupTaskIdGenerator,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val selectedGroupId = MutableStateFlow(restoredGroupId())
    private val selectedTaskId = MutableStateFlow(restoredTaskId())
    private val taskDetails = MutableStateFlow(emptyList<GroupTaskDetails>())
    private val members = MutableStateFlow(emptyList<GroupMember>())
    private val currentUserId = MutableStateFlow(repository.currentUserId())
    private val loadState = MutableStateFlow(
        if (selectedGroupId.value == null) GroupTasksLoadState.IDLE else GroupTasksLoadState.LOADING
    )
    private val detailLoadState = MutableStateFlow(
        if (selectedTaskId.value == null) GroupTaskDetailLoadState.IDLE else GroupTaskDetailLoadState.LOADING
    )
    private val isRefreshing = MutableStateFlow(false)
    private val isOffline = MutableStateFlow(false)
    private val error = MutableStateFlow<GroupTasksUiError?>(null)
    private val detailError = MutableStateFlow<GroupTasksUiError?>(null)
    private val editor = MutableStateFlow<GroupTaskEditorUiState?>(null)
    private val confirmation = MutableStateFlow<GroupTaskConfirmation?>(null)
    private val pendingMutation = MutableStateFlow<PendingGroupTaskMutation?>(null)
    private val accessRestricted = MutableStateFlow(false)
    private val effectsChannel = Channel<GroupTasksEffect>(Channel.BUFFERED)
    private val _uiState = MutableStateFlow(GroupTasksUiState())

    private var selectedGroupObservationJob: Job? = null
    private var taskDetailsObservationJob: Job? = null
    private var membersObservationJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null
    private var refreshGeneration = 0L
    private var mutationGeneration = 0L
    private var refreshCompletedGroupId: CollaborationGroupId? = null
    private var observedDetailsGroupId: CollaborationGroupId? = null
    private var detailsObservationReady: CompletableDeferred<Unit>? = null
    private var retryRequest: RetryRequest? = null
    private var presentationContextGeneration = 0L
    private var selectionResolutionJob: Job? = null

    val uiState = _uiState.asStateFlow()
    val effects: Flow<GroupTasksEffect> = effectsChannel.receiveAsFlow()

    init {
        if (selectedGroupId.value == null && selectedTaskId.value != null) {
            selectedTaskId.value = null
            savedStateHandle[SELECTED_TASK_ID_KEY] = null
            detailLoadState.value = GroupTaskDetailLoadState.IDLE
        }
        observeIdentity()
        selectedGroupId.value?.let { activateGroup(it, restored = true) } ?: render()
    }

    fun onAction(action: GroupTasksAction) {
        if (pendingMutation.value != null && action.isBlockedDuringMutation()) return
        when (action) {
            is GroupTasksAction.OpenGroup -> activateGroup(action.groupId, restored = false)
            GroupTasksAction.BackToGroups -> backToGroups()
            GroupTasksAction.Refresh -> refreshSelectedGroup()
            is GroupTasksAction.OpenTask -> openTask(action.taskId)
            GroupTasksAction.BackFromTask -> backFromTask()
            GroupTasksAction.OpenCreateTask -> openCreateEditor()
            GroupTasksAction.OpenEditTask -> openEditEditor()
            GroupTasksAction.CancelEditor -> cancelEditor()
            GroupTasksAction.SaveTask -> saveEditor()
            is GroupTasksAction.ChangeTitle -> updateEditor {
                copy(title = action.title, errors = errors - GroupTaskField.TITLE)
            }
            is GroupTasksAction.ChangeDescription -> updateEditor { copy(description = action.description) }
            is GroupTasksAction.ChangeAssignee -> updateEditor {
                copy(assigneeId = action.assigneeId, errors = errors - GroupTaskField.ASSIGNEE)
            }
            is GroupTasksAction.ChangeDeadline -> updateEditor {
                copy(dueAt = action.dueAt, errors = errors - GroupTaskField.DEADLINE)
            }
            is GroupTasksAction.ChangeReminderOffsets -> updateEditor {
                copy(
                    reminderOffsetsSeconds = action.offsetsSeconds,
                    errors = errors - GroupTaskField.REMINDER_OFFSETS
                )
            }
            is GroupTasksAction.ReassignTask -> reassignTask(action.assigneeId)
            GroupTasksAction.StartTask -> startTask()
            GroupTasksAction.CompleteTask -> completeTask()
            GroupTasksAction.OpenCancelConfirmation,
            GroupTasksAction.CancelTask -> openCancelConfirmation()
            GroupTasksAction.OpenReopenConfirmation,
            GroupTasksAction.ReopenTask -> openReopenConfirmation()
            GroupTasksAction.ConfirmCancel -> confirmCancel()
            GroupTasksAction.ConfirmReopen -> confirmReopen()
            GroupTasksAction.DismissConfirmation -> setConfirmation(null)
            GroupTasksAction.Retry -> retry()
            GroupTasksAction.RetryLastMutation -> retryLastMutation()
            GroupTasksAction.DismissError -> clearErrors()
        }
    }

    private fun observeIdentity() {
        viewModelScope.launch {
            repository.observeCurrentUserId()
                .distinctUntilChanged()
                .collect { nextUserId ->
                    if (currentUserId.value == nextUserId) return@collect
                    currentUserId.value = nextUserId
                    resetForSessionBoundary()
                    try {
                        repository.clearSessionCache()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Presentation state is already redacted even if cache clearing is unavailable.
                    }
                }
        }
    }

    private fun resetForSessionBoundary() {
        cancelGroupObservations()
        selectionResolutionJob?.cancel()
        selectionResolutionJob = null
        refreshJob?.cancel()
        refreshJob = null
        mutationJob?.cancel()
        mutationJob = null
        ++refreshGeneration
        ++mutationGeneration
        selectedGroupId.value = null
        selectedTaskId.value = null
        savedStateHandle[SELECTED_GROUP_ID_KEY] = null
        savedStateHandle[SELECTED_TASK_ID_KEY] = null
        taskDetails.value = emptyList()
        members.value = emptyList()
        editor.value = null
        confirmation.value = null
        pendingMutation.value = null
        loadState.value = GroupTasksLoadState.IDLE
        detailLoadState.value = GroupTaskDetailLoadState.IDLE
        error.value = null
        detailError.value = null
        accessRestricted.value = false
        isRefreshing.value = false
        isOffline.value = false
        refreshCompletedGroupId = null
        observedDetailsGroupId = null
        detailsObservationReady = null
        advancePresentationContext()
        while (effectsChannel.tryReceive().isSuccess) {
            // Drop stale navigation/mutation outcomes from the previous account.
        }
        render()
    }

    private fun activateGroup(groupId: CollaborationGroupId, restored: Boolean) {
        if (pendingMutation.value != null) return
        val sameGroup = selectedGroupId.value == groupId
        advancePresentationContext()
        selectionResolutionJob?.cancel()
        selectionResolutionJob = null
        refreshJob?.cancel()
        refreshJob = null
        ++refreshGeneration
        selectedGroupId.value = groupId
        if (!restored || !sameGroup) {
            selectedTaskId.value = if (restored) selectedTaskId.value else null
            savedStateHandle[SELECTED_TASK_ID_KEY] = selectedTaskId.value?.value
        }
        savedStateHandle[SELECTED_GROUP_ID_KEY] = groupId.value
        taskDetails.value = emptyList()
        members.value = emptyList()
        editor.value = null
        confirmation.value = null
        error.value = null
        detailError.value = null
        accessRestricted.value = false
        isOffline.value = false
        isRefreshing.value = false
        refreshCompletedGroupId = null
        observedDetailsGroupId = null
        detailsObservationReady = CompletableDeferred()
        loadState.value = GroupTasksLoadState.LOADING
        detailLoadState.value = if (selectedTaskId.value == null) {
            GroupTaskDetailLoadState.IDLE
        } else {
            GroupTaskDetailLoadState.LOADING
        }
        cancelGroupObservations()
        selectedGroupObservationJob = observeGroup(groupId)
        refresh(groupId)
        render()
    }

    private fun observeGroup(groupId: CollaborationGroupId): Job = viewModelScope.launch {
        val ready = detailsObservationReady
        val detailsJob = launch {
            try {
                repository.observeTaskDetails(groupId)
                    .catch { throwable -> handleObservationError(groupId, throwable) }
                    .collect { details ->
                        if (selectedGroupId.value != groupId) return@collect
                        taskDetails.value = details.filter { it.task.groupId == groupId }
                        ready?.complete(Unit)
                        observedDetailsGroupId = groupId
                        updateDetailStateFromObservation()
                        if (
                            refreshCompletedGroupId == groupId &&
                            selectedTaskId.value != null &&
                            taskDetails.value.none { it.task.id == selectedTaskId.value }
                        ) {
                            fallbackMissingSelection(groupId)
                        }
                        render()
                    }
            } finally {
                if (detailsObservationReady === ready) {
                    ready?.complete(Unit)
                }
                if (taskDetailsObservationJob === currentCoroutineContext()[Job]) {
                    taskDetailsObservationJob = null
                }
            }
        }
        taskDetailsObservationJob = detailsJob
        val membersJob = launch {
            try {
                repository.observeMembers(groupId)
                    .catch { throwable -> handleObservationError(groupId, throwable) }
                    .collect { observedMembers ->
                        if (selectedGroupId.value != groupId) return@collect
                        members.value = observedMembers.filter { it.groupId == groupId }
                        render()
                    }
            } finally {
                if (membersObservationJob === currentCoroutineContext()[Job]) {
                    membersObservationJob = null
                }
            }
        }
        membersObservationJob = membersJob
        joinAll(detailsJob, membersJob)
    }

    private fun cancelGroupObservations() {
        selectedGroupObservationJob?.cancel()
        selectedGroupObservationJob = null
        taskDetailsObservationJob?.cancel()
        taskDetailsObservationJob = null
        membersObservationJob?.cancel()
        membersObservationJob = null
    }

    private fun handleObservationError(groupId: CollaborationGroupId, throwable: Throwable) {
        if (selectedGroupId.value != groupId) return
        val mapped = mapThrowable(throwable)
        if (mapped == GroupTasksUiError.NotAuthorized || mapped == GroupTasksUiError.NotFound) {
            accessRestricted.value = true
        }
        error.value = mapped
        detailError.value = selectedTaskId.value?.let { mapped }
        isOffline.value = mapped == GroupTasksUiError.Offline
        updateLoadStateForError()
        render()
    }

    private fun refreshSelectedGroup() {
        selectedGroupId.value?.let { groupId ->
            if (
                taskDetailsObservationJob?.isActive != true ||
                membersObservationJob?.isActive != true
            ) {
                cancelGroupObservations()
                observedDetailsGroupId = null
                detailsObservationReady = CompletableDeferred()
                selectedGroupObservationJob = observeGroup(groupId)
            }
            refresh(groupId)
        }
    }

    private fun refresh(groupId: CollaborationGroupId) {
        if (pendingMutation.value != null || refreshJob?.isActive == true) return
        invalidateRetry()
        val requestGeneration = ++refreshGeneration
        refreshCompletedGroupId = null
        isRefreshing.value = true
        error.value = null
        detailError.value = null
        isOffline.value = false
        updateLoadStateForRefresh()
        render()
        val detailsReady = detailsObservationReady
        refreshJob = viewModelScope.launch {
            try {
                detailsReady?.await()
                val result = try {
                    repository.refreshTasks(groupId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Exception) {
                    CollaborationMutationResult.Failure(mapThrowableToDomain(throwable))
                }
                if (requestGeneration != refreshGeneration || selectedGroupId.value != groupId) return@launch
                applyRefreshResult(groupId, result)
            } finally {
                if (requestGeneration == refreshGeneration) {
                    isRefreshing.value = false
                    refreshJob = null
                    updateLoadStateAfterRefresh()
                    render()
                }
            }
        }
    }

    private fun applyRefreshResult(groupId: CollaborationGroupId, result: CollaborationMutationResult) {
        when (result) {
            CollaborationMutationResult.Applied,
            is CollaborationMutationResult.Created -> {
                refreshCompletedGroupId = groupId
                error.value = null
                detailError.value = null
                isOffline.value = false
                accessRestricted.value = false
                retryRequest = null
            }
            CollaborationMutationResult.Queued -> applyLoadError(CollaborationError.InvalidState())
            CollaborationMutationResult.NetworkRequired -> applyLoadError(CollaborationError.NetworkUnavailable())
            is CollaborationMutationResult.Failure -> applyRefreshError(groupId, result.error)
            is CollaborationMutationResult.Conflict -> applyLoadError(result.error)
            is CollaborationMutationResult.NotAuthorized -> applyLoadError(result.error)
            is CollaborationMutationResult.InvalidState -> applyLoadError(result.error)
        }
    }

    private fun applyRefreshError(groupId: CollaborationGroupId, domainError: CollaborationError) {
        if (domainError == CollaborationError.NotFound) {
            resolveNotFoundSelection(groupId)
        } else {
            applyLoadError(domainError)
        }
    }

    private fun applyLoadError(domainError: CollaborationError) {
        if (domainError == CollaborationError.NotAuthorized || domainError == CollaborationError.NotFound) {
            accessRestricted.value = true
        }
        val mapped = mapError(domainError)
        error.value = mapped
        detailError.value = selectedTaskId.value?.let { mapped }
        isOffline.value = mapped == GroupTasksUiError.Offline
        updateLoadStateForError()
        render()
    }

    private fun resolveNotFoundSelection(groupId: CollaborationGroupId) {
        selectionResolutionJob?.cancel()
        selectionResolutionJob = viewModelScope.launch {
            try {
                val groupExists = try {
                    repository.observeGroup(groupId).first()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (selectedGroupId.value != groupId) return@launch
                if (groupExists == null) {
                    clearGroupSelection()
                    error.value = GroupTasksUiError.NotFound
                    render()
                    effectsChannel.trySend(GroupTasksEffect.NavigateBack)
                } else {
                    fallbackMissingSelection(groupId)
                }
            } finally {
                if (selectionResolutionJob === currentCoroutineContext()[Job]) {
                    selectionResolutionJob = null
                }
            }
        }
    }

    private fun fallbackMissingSelection(groupId: CollaborationGroupId) {
        if (selectedTaskId.value == null) {
            applyLoadError(CollaborationError.NotFound)
            return
        }
        advancePresentationContext()
        selectedTaskId.value = null
        savedStateHandle[SELECTED_TASK_ID_KEY] = null
        editor.value = null
        confirmation.value = null
        detailLoadState.value = GroupTaskDetailLoadState.IDLE
        detailError.value = GroupTasksUiError.NotFound
        error.value = GroupTasksUiError.NotFound
        render()
        effectsChannel.trySend(GroupTasksEffect.NavigateToGroupTasks(groupId))
    }

    private fun openTask(taskId: GroupTaskId) {
        val groupId = selectedGroupId.value ?: return setError(GroupTasksUiError.NotFound)
        advancePresentationContext()
        selectedTaskId.value = taskId
        savedStateHandle[SELECTED_TASK_ID_KEY] = taskId.value
        editor.value = null
        confirmation.value = null
        error.value = if (isOffline.value) GroupTasksUiError.Offline else null
        detailError.value = if (isOffline.value) GroupTasksUiError.Offline else null
        detailLoadState.value = if (findDetails(taskId) == null) {
            GroupTaskDetailLoadState.LOADING
        } else if (isRefreshing.value) {
            GroupTaskDetailLoadState.OFFLINE_REFRESHING
        } else if (detailError.value == GroupTasksUiError.Offline) {
            GroupTaskDetailLoadState.CACHED_OFFLINE
        } else if (detailError.value != null) {
            GroupTaskDetailLoadState.ERROR
        } else {
            GroupTaskDetailLoadState.CONTENT
        }
        render()
        if (findDetails(taskId) != null) {
            effectsChannel.trySend(GroupTasksEffect.NavigateToTask(groupId, taskId))
        }
    }

    private fun backFromTask() {
        advancePresentationContext()
        selectedTaskId.value = null
        savedStateHandle[SELECTED_TASK_ID_KEY] = null
        editor.value = null
        confirmation.value = null
        detailError.value = null
        detailLoadState.value = GroupTaskDetailLoadState.IDLE
        render()
    }

    private fun backToGroups() {
        if (selectedGroupId.value == null) return
        clearGroupSelection()
        render()
        effectsChannel.trySend(GroupTasksEffect.NavigateBack)
    }

    private fun clearGroupSelection() {
        cancelGroupObservations()
        selectionResolutionJob?.cancel()
        selectionResolutionJob = null
        refreshJob?.cancel()
        refreshJob = null
        ++refreshGeneration
        detailsObservationReady = null
        selectedGroupId.value = null
        selectedTaskId.value = null
        savedStateHandle[SELECTED_GROUP_ID_KEY] = null
        savedStateHandle[SELECTED_TASK_ID_KEY] = null
        taskDetails.value = emptyList()
        members.value = emptyList()
        editor.value = null
        confirmation.value = null
        detailLoadState.value = GroupTaskDetailLoadState.IDLE
        loadState.value = GroupTasksLoadState.IDLE
        error.value = null
        detailError.value = null
        accessRestricted.value = false
        isRefreshing.value = false
        isOffline.value = false
        refreshCompletedGroupId = null
        advancePresentationContext()
        observedDetailsGroupId = null
    }

    private fun openCreateEditor() {
        if (selectedGroupId.value == null) return setError(GroupTasksUiError.NotFound)
        advancePresentationContext()
        error.value = null
        detailError.value = null
        editor.value = GroupTaskEditorUiState(mode = GroupTaskEditorMode.Create)
        confirmation.value = null
        render()
    }

    private fun openEditEditor() {
        val details = selectedTaskId.value?.let(::findDetails) ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(details.task).canEdit) return deny()
        advancePresentationContext()
        val task = details.task
        error.value = null
        detailError.value = null
        editor.value = GroupTaskEditorUiState(
            mode = GroupTaskEditorMode.Edit(task.id),
            taskId = task.id,
            title = task.title,
            description = task.description.orEmpty(),
            assigneeId = task.assigneeId,
            dueAt = task.dueAt,
            reminderOffsetsSeconds = details.reminders.sortedBy { it.offsetSeconds }.map { it.offsetSeconds },
            expectedVersion = task.version
        )
        confirmation.value = null
        render()
    }

    private fun cancelEditor() {
        advancePresentationContext()
        editor.value = null
        error.value = null
        detailError.value = null
        render()
    }

    private fun saveEditor() {
        val draft = editor.value ?: return
        val validation = validate(draft)
        if (validation.isNotEmpty()) {
            val first = validation.entries.first()
            editor.value = draft.copy(errors = validation)
            error.value = GroupTasksUiError.Validation(first.key, first.value)
            detailError.value = error.value
            render()
            return
        }
        val groupId = selectedGroupId.value ?: return setError(GroupTasksUiError.NotFound)
        val assigneeId = draft.assigneeId ?: return
        val dueAt = draft.dueAt ?: return
        when (val mode = draft.mode) {
            GroupTaskEditorMode.Create -> {
                val taskId = draft.clientTaskId ?: idGenerator.nextTaskId(groupId)
                val effectiveDraft = draft.copy(clientTaskId = taskId)
                if (draft.clientTaskId == null) {
                    editor.value = effectiveDraft
                }
                val command = try {
                    CreateGroupTaskCommand(
                        taskId = taskId,
                        groupId = groupId,
                        title = effectiveDraft.title.trim(),
                        description = effectiveDraft.description.trim().takeIf(String::isNotEmpty),
                        assigneeId = assigneeId,
                        dueAt = dueAt,
                        reminderOffsetsSeconds = effectiveDraft.reminderOffsetsSeconds
                    )
                } catch (_: IllegalArgumentException) {
                    return rejectValidation(effectiveDraft)
                }
                runMutation(
                    pending = PendingGroupTaskMutation(GroupTasksMutation.CREATE, groupId, taskId),
                    retryable = true
                ) { repository.createTask(command) }
            }
            is GroupTaskEditorMode.Edit -> {
                val expectedVersion = draft.expectedVersion ?: return setError(GroupTasksUiError.InvalidState)
                val command = try {
                    EditGroupTaskCommand(
                        taskId = mode.taskId,
                        title = draft.title.trim(),
                        description = draft.description.trim().takeIf(String::isNotEmpty),
                        assigneeId = assigneeId,
                        dueAt = dueAt,
                        reminderOffsetsSeconds = draft.reminderOffsetsSeconds,
                        expectedVersion = expectedVersion
                    )
                } catch (_: IllegalArgumentException) {
                    return rejectValidation(draft)
                }
                runMutation(
                    pending = PendingGroupTaskMutation(GroupTasksMutation.EDIT, groupId, mode.taskId),
                    retryable = true
                ) { repository.editTask(command) }
            }
        }
    }

    private fun rejectValidation(draft: GroupTaskEditorUiState) {
        val errors = validate(draft)
        val first = errors.entries.firstOrNull() ?: return setError(GroupTasksUiError.InvalidState)
        editor.value = draft.copy(errors = errors)
        error.value = GroupTasksUiError.Validation(first.key, first.value)
        render()
    }

    private fun reassignTask(assigneeId: UserId) {
        val task = selectedTaskOrNull() ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(task.task).canReassign) return deny()
        if (members.value.none { it.userId == assigneeId }) return setError(GroupTasksUiError.NotFound)
        val groupId = selectedGroupId.value ?: return setError(GroupTasksUiError.NotFound)
        val command = ReassignGroupTaskCommand(task.task.id, assigneeId, task.task.version)
        runMutation(PendingGroupTaskMutation(GroupTasksMutation.REASSIGN, groupId, task.task.id), retryable = true) {
            repository.reassignTask(command)
        }
    }

    private fun startTask() {
        val task = selectedTaskOrNull() ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(task.task).canStart) return deny()
        val groupId = selectedGroupId.value ?: return setError(GroupTasksUiError.NotFound)
        val command = StartGroupTaskCommand(task.task.id, task.task.version)
        runMutation(PendingGroupTaskMutation(GroupTasksMutation.START, groupId, task.task.id), retryable = true) {
            repository.startTask(command)
        }
    }

    private fun completeTask() {
        val task = selectedTaskOrNull() ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(task.task).canComplete) return deny()
        val groupId = selectedGroupId.value ?: return setError(GroupTasksUiError.NotFound)
        val command = CompleteGroupTaskCommand(task.task.id, task.task.version)
        runMutation(PendingGroupTaskMutation(GroupTasksMutation.COMPLETE, groupId, task.task.id), retryable = true) {
            repository.completeTask(command)
        }
    }

    private fun openCancelConfirmation() {
        val task = selectedTaskOrNull() ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(task.task).canCancel) return deny()
        confirmation.value = GroupTaskConfirmation.Cancel(task.task.id)
        error.value = null
        render()
    }

    private fun openReopenConfirmation() {
        val task = selectedTaskOrNull() ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(task.task).canReopen) return deny()
        confirmation.value = GroupTaskConfirmation.Reopen(task.task.id)
        error.value = null
        render()
    }

    private fun confirmCancel() {
        val confirmation = confirmation.value as? GroupTaskConfirmation.Cancel ?: return
        val task = findDetails(confirmation.taskId) ?: return setError(GroupTasksUiError.NotFound)
        val groupId = selectedGroupId.value ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(task.task).canCancel) return deny()
        val command = CancelGroupTaskCommand(task.task.id, task.task.version)
        runMutation(PendingGroupTaskMutation(GroupTasksMutation.CANCEL, groupId, task.task.id), retryable = true) {
            repository.cancelTask(command)
        }
    }

    private fun confirmReopen() {
        val confirmation = confirmation.value as? GroupTaskConfirmation.Reopen ?: return
        val task = findDetails(confirmation.taskId) ?: return setError(GroupTasksUiError.NotFound)
        val groupId = selectedGroupId.value ?: return setError(GroupTasksUiError.NotFound)
        if (!permissionsFor(task.task).canReopen) return deny()
        val command = ReopenGroupTaskCommand(task.task.id, task.task.version)
        runMutation(PendingGroupTaskMutation(GroupTasksMutation.REOPEN, groupId, task.task.id), retryable = true) {
            repository.reopenTask(command)
        }
    }

    private fun runMutation(
        pending: PendingGroupTaskMutation,
        retryable: Boolean,
        operation: suspend () -> CollaborationMutationResult
    ) {
        if (pendingMutation.value != null || mutationJob?.isActive == true) return
        invalidateRetry()
        refreshJob?.cancel()
        refreshJob = null
        ++refreshGeneration
        isRefreshing.value = false
        pendingMutation.value = pending
        error.value = null
        detailError.value = null
        confirmation.value = null
        render()
        val requestGeneration = ++mutationGeneration
        mutationJob = viewModelScope.launch {
            try {
                val result = try {
                    operation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Exception) {
                    CollaborationMutationResult.Failure(mapThrowableToDomain(throwable))
                }
                if (requestGeneration != mutationGeneration || pendingMutation.value != pending) return@launch
                pendingMutation.value = null
                when (result) {
                    CollaborationMutationResult.Applied,
                    is CollaborationMutationResult.Created -> applyMutationSuccess(pending)
                    CollaborationMutationResult.Queued -> applyMutationFailure(
                        pending,
                        CollaborationError.InvalidState(),
                        retryAction = null
                    )
                    CollaborationMutationResult.NetworkRequired -> applyRetryableMutationFailure(
                        pending,
                        CollaborationError.NetworkUnavailable(),
                        operation,
                        retryable
                    )
                    is CollaborationMutationResult.Failure -> applyMutationResultFailure(
                        pending,
                        result.error,
                        operation,
                        retryable
                    )
                    is CollaborationMutationResult.Conflict -> applyMutationFailure(
                        pending,
                        result.error,
                        retryAction = GroupTasksAction.Refresh
                    )
                    is CollaborationMutationResult.NotAuthorized -> applyMutationFailure(
                        pending,
                        result.error,
                        retryAction = null
                    )
                    is CollaborationMutationResult.InvalidState -> applyMutationFailure(
                        pending,
                        result.error,
                        retryAction = null
                    )
                }
            } catch (cancelled: CancellationException) {
                if (requestGeneration == mutationGeneration && pendingMutation.value == pending) {
                    pendingMutation.value = null
                    render()
                }
                throw cancelled
            } finally {
                if (requestGeneration == mutationGeneration) {
                    mutationJob = null
                    render()
                }
            }
        }
    }

    private fun applyMutationSuccess(pending: PendingGroupTaskMutation) {
        retryRequest = null
        error.value = null
        detailError.value = null
        isOffline.value = false
        editor.value = null
        confirmation.value = null
        effectsChannel.trySend(GroupTasksEffect.MutationCompleted(pending.mutation))
        if (pending.mutation == GroupTasksMutation.CREATE) {
            selectedTaskId.value = pending.taskId
            savedStateHandle[SELECTED_TASK_ID_KEY] = pending.taskId.value
            detailLoadState.value = if (findDetails(pending.taskId) == null) {
                GroupTaskDetailLoadState.LOADING
            } else {
                GroupTaskDetailLoadState.CONTENT
            }
            render()
            effectsChannel.trySend(GroupTasksEffect.NavigateToTask(pending.groupId, pending.taskId))
        }
    }

    private fun applyMutationResultFailure(
        pending: PendingGroupTaskMutation,
        domainError: CollaborationError,
        operation: suspend () -> CollaborationMutationResult,
        retryable: Boolean
    ) {
        if (domainError is CollaborationError.NetworkUnavailable) {
            applyRetryableMutationFailure(pending, domainError, operation, retryable)
        } else {
            applyMutationFailure(
                pending,
                domainError,
                retryAction = if (domainError is CollaborationError.Conflict) GroupTasksAction.Refresh else null
            )
        }
    }

    private fun applyRetryableMutationFailure(
        pending: PendingGroupTaskMutation,
        domainError: CollaborationError,
        operation: suspend () -> CollaborationMutationResult,
        retryable: Boolean
    ) {
        if (retryable) {
            retryRequest = RetryRequest(
                pending = pending,
                operation = operation,
                contextGeneration = presentationContextGeneration
            )
        }
        applyMutationFailure(
            pending,
            domainError,
            retryAction = if (retryable) GroupTasksAction.RetryLastMutation else null,
            preserveRetry = retryable
        )
    }

    private fun applyMutationFailure(
        pending: PendingGroupTaskMutation,
        domainError: CollaborationError,
        retryAction: GroupTasksAction?,
        preserveRetry: Boolean = false
    ) {
        if (!preserveRetry) invalidateRetry()
        if (domainError == CollaborationError.NotAuthorized || domainError == CollaborationError.NotFound) {
            accessRestricted.value = true
        }
        val mapped = mapError(domainError)
        error.value = mapped
        detailError.value = mapped
        isOffline.value = mapped == GroupTasksUiError.Offline
        effectsChannel.trySend(
            GroupTasksEffect.MutationFailed(
                mutation = pending.mutation,
                error = mapped,
                retryAction = retryAction,
                showSnackbar = true
            )
        )
        render()
    }

    private fun retry() {
        if (retryRequest != null) {
            retryLastMutation()
        } else {
            refreshSelectedGroup()
        }
    }

    private fun retryLastMutation() {
        val request = retryRequest ?: return refreshSelectedGroup()
        val taskSelectionChanged = request.pending.mutation != GroupTasksMutation.CREATE &&
            selectedTaskId.value != request.pending.taskId
        if (
            request.contextGeneration != presentationContextGeneration ||
            selectedGroupId.value != request.pending.groupId ||
            taskSelectionChanged
        ) {
            retryRequest = null
            return refreshSelectedGroup()
        }
        retryRequest = null
        runMutation(request.pending, retryable = true, operation = request.operation)
    }

    private fun selectedTaskOrNull(): GroupTaskDetailUiModel? =
        selectedTaskId.value?.let { id ->
            findDetails(id)?.let { details ->
                GroupTaskDetailUiModel(
                    details = details,
                    assignee = members.value.firstOrNull { it.userId == details.task.assigneeId },
                    isOverdue = GroupTaskPolicy.isOverdue(details.task, clock.instant()),
                    permissions = permissionsFor(details.task)
                )
            }
        }

    private fun findDetails(taskId: GroupTaskId): GroupTaskDetails? =
        taskDetails.value.firstOrNull { it.task.id == taskId }

    private fun permissionsFor(task: com.smartreminder.domain.model.collaboration.GroupTask): GroupTaskPermissions {
        if (accessRestricted.value) return GroupTaskPermissions()
        val actorId = currentUserId.value ?: return GroupTaskPermissions()
        val actorRole = members.value.firstOrNull { it.userId == actorId }?.role
            ?: return GroupTaskPermissions()
        return GroupTaskPermissions(
            canEdit = GroupTaskPolicy.canEdit(actorId, actorRole, task),
            canReassign = GroupTaskPolicy.canReassign(actorId, actorRole, task),
            canStart = GroupTaskPolicy.canStart(actorId, task),
            canComplete = GroupTaskPolicy.canComplete(actorId, task),
            canCancel = GroupTaskPolicy.canCancel(actorId, actorRole, task),
            canReopen = GroupTaskPolicy.canReopen(actorId, actorRole, task)
        )
    }

    private fun updateDetailStateFromObservation() {
        val selectedId = selectedTaskId.value ?: run {
            detailLoadState.value = GroupTaskDetailLoadState.IDLE
            return
        }
        detailLoadState.value = when {
            taskDetails.value.any { it.task.id == selectedId } && isRefreshing.value ->
                GroupTaskDetailLoadState.OFFLINE_REFRESHING
            taskDetails.value.any { it.task.id == selectedId } && detailError.value == GroupTasksUiError.Offline ->
                GroupTaskDetailLoadState.CACHED_OFFLINE
            taskDetails.value.any { it.task.id == selectedId } && detailError.value != null ->
                GroupTaskDetailLoadState.ERROR
            taskDetails.value.any { it.task.id == selectedId } -> GroupTaskDetailLoadState.CONTENT
            isRefreshing.value -> GroupTaskDetailLoadState.LOADING
            detailError.value != null -> GroupTaskDetailLoadState.ERROR
            else -> GroupTaskDetailLoadState.LOADING
        }
    }

    private fun updateLoadStateForRefresh() {
        loadState.value = if (taskDetails.value.isEmpty()) {
            GroupTasksLoadState.LOADING
        } else {
            GroupTasksLoadState.OFFLINE_REFRESHING
        }
        updateDetailStateFromObservation()
    }

    private fun updateLoadStateAfterRefresh() {
        val hasCache = taskDetails.value.isNotEmpty()
        loadState.value = when {
            error.value == GroupTasksUiError.Offline && hasCache -> GroupTasksLoadState.CACHED_OFFLINE
            error.value != null && !hasCache -> GroupTasksLoadState.ERROR
            error.value != null -> GroupTasksLoadState.ERROR
            hasCache -> GroupTasksLoadState.CONTENT
            else -> GroupTasksLoadState.EMPTY
        }
        updateDetailStateFromObservation()
    }

    private fun updateLoadStateForError() {
        loadState.value = if (taskDetails.value.isNotEmpty() && error.value == GroupTasksUiError.Offline) {
            GroupTasksLoadState.CACHED_OFFLINE
        } else {
            GroupTasksLoadState.ERROR
        }
        updateDetailStateFromObservation()
    }

    private fun validate(editor: GroupTaskEditorUiState): Map<GroupTaskField, GroupTaskFieldError> {
        val errors = linkedMapOf<GroupTaskField, GroupTaskFieldError>()
        if (editor.title.isBlank()) errors[GroupTaskField.TITLE] = GroupTaskFieldError.TITLE_REQUIRED
        if (editor.assigneeId == null || editor.assigneeId.value.isBlank()) {
            errors[GroupTaskField.ASSIGNEE] = GroupTaskFieldError.ASSIGNEE_REQUIRED
        } else if (members.value.none { it.userId == editor.assigneeId }) {
            errors[GroupTaskField.ASSIGNEE] = GroupTaskFieldError.ASSIGNEE_NOT_MEMBER
        }
        if (editor.dueAt == null) errors[GroupTaskField.DEADLINE] = GroupTaskFieldError.DEADLINE_REQUIRED
        val offsets = editor.reminderOffsetsSeconds
        when {
            offsets.isEmpty() -> errors[GroupTaskField.REMINDER_OFFSETS] =
                GroupTaskFieldError.REMINDER_OFFSETS_REQUIRED
            offsets.size > MAX_REMINDER_OFFSETS -> errors[GroupTaskField.REMINDER_OFFSETS] =
                GroupTaskFieldError.REMINDER_OFFSETS_TOO_MANY
            offsets.any { it <= 0L } -> errors[GroupTaskField.REMINDER_OFFSETS] =
                GroupTaskFieldError.REMINDER_OFFSETS_NON_POSITIVE
            offsets.distinct().size != offsets.size -> errors[GroupTaskField.REMINDER_OFFSETS] =
                GroupTaskFieldError.REMINDER_OFFSETS_DUPLICATE
        }
        return errors
    }

    private fun updateEditor(update: GroupTaskEditorUiState.() -> GroupTaskEditorUiState) {
        val currentEditor = editor.value ?: return
        invalidateRetry()
        editor.value = update(currentEditor)
        if (editor.value != null) {
            error.value = null
            detailError.value = null
            render()
        }
    }

    private fun advancePresentationContext() {
        presentationContextGeneration += 1L
        invalidateRetry()
    }

    private fun invalidateRetry() {
        retryRequest = null
    }

    private fun setConfirmation(next: GroupTaskConfirmation?) {
        confirmation.value = next
        render()
    }

    private fun deny() {
        error.value = GroupTasksUiError.NotAuthorized
        detailError.value = GroupTasksUiError.NotAuthorized
        render()
    }

    private fun setError(nextError: GroupTasksUiError) {
        error.value = nextError
        render()
    }

    private fun clearErrors() {
        error.value = null
        detailError.value = null
        render()
    }

    private fun render() {
        val selectedId = selectedTaskId.value
        val taskModels = taskDetails.value.map { details ->
            GroupTaskListItemUiModel(
                task = details.task,
                assignee = members.value.firstOrNull { it.userId == details.task.assigneeId },
                isOverdue = GroupTaskPolicy.isOverdue(details.task, clock.instant()),
                permissions = permissionsFor(details.task)
            )
        }
        val selectedTask = selectedTaskOrNull()
        val screen = when {
            editor.value != null -> GroupTasksScreen.EDITOR
            selectedId != null -> GroupTasksScreen.DETAIL
            else -> GroupTasksScreen.LIST
        }
        val hasCache = taskModels.isNotEmpty()
        val renderedLoadState = when {
            selectedGroupId.value == null -> GroupTasksLoadState.IDLE
            isRefreshing.value && hasCache -> GroupTasksLoadState.OFFLINE_REFRESHING
            isRefreshing.value -> GroupTasksLoadState.LOADING
            error.value == GroupTasksUiError.Offline && hasCache -> GroupTasksLoadState.CACHED_OFFLINE
            error.value != null -> GroupTasksLoadState.ERROR
            hasCache -> GroupTasksLoadState.CONTENT
            else -> GroupTasksLoadState.EMPTY
        }
        loadState.value = renderedLoadState
        _uiState.value = GroupTasksUiState(
            loadState = renderedLoadState,
            detailLoadState = detailLoadState.value,
            screen = screen,
            selectedGroupId = selectedGroupId.value,
            selectedTaskId = selectedId,
            tasks = taskModels,
            members = members.value,
            selectedTask = selectedTask,
            editor = editor.value,
            confirmation = confirmation.value,
            pendingMutation = pendingMutation.value,
            error = error.value,
            detailError = detailError.value,
            isCached = hasCache,
            isOffline = isOffline.value,
            isRefreshing = isRefreshing.value,
            isDetailAccessRestricted = accessRestricted.value
        )
    }

    private fun GroupTasksAction.isBlockedDuringMutation(): Boolean = when (this) {
        is GroupTasksAction.OpenGroup,
        GroupTasksAction.BackToGroups,
        GroupTasksAction.Refresh,
        is GroupTasksAction.OpenTask,
        GroupTasksAction.BackFromTask,
        GroupTasksAction.OpenCreateTask,
        GroupTasksAction.OpenEditTask,
        GroupTasksAction.CancelEditor,
        GroupTasksAction.SaveTask,
        is GroupTasksAction.ChangeTitle,
        is GroupTasksAction.ChangeDescription,
        is GroupTasksAction.ChangeAssignee,
        is GroupTasksAction.ChangeDeadline,
        is GroupTasksAction.ChangeReminderOffsets,
        is GroupTasksAction.ReassignTask,
        GroupTasksAction.StartTask,
        GroupTasksAction.CompleteTask,
        GroupTasksAction.OpenCancelConfirmation,
        GroupTasksAction.OpenReopenConfirmation,
        GroupTasksAction.ConfirmCancel,
        GroupTasksAction.ConfirmReopen,
        GroupTasksAction.CancelTask,
        GroupTasksAction.ReopenTask,
        GroupTasksAction.DismissConfirmation -> true
        else -> false
    }

    private fun mapThrowableToDomain(throwable: Throwable): CollaborationError = when (throwable) {
        is IOException -> CollaborationError.NetworkUnavailable(throwable)
        else -> CollaborationError.Unknown(throwable)
    }

    private fun mapThrowable(throwable: Throwable): GroupTasksUiError = mapError(mapThrowableToDomain(throwable))

    private fun mapError(domainError: CollaborationError): GroupTasksUiError = when (domainError) {
        CollaborationError.ConfigurationMissing -> GroupTasksUiError.MissingConfiguration
        is CollaborationError.NetworkUnavailable -> GroupTasksUiError.Offline
        CollaborationError.NotAuthorized -> GroupTasksUiError.NotAuthorized
        CollaborationError.NotFound -> GroupTasksUiError.NotFound
        is CollaborationError.Conflict -> GroupTasksUiError.Conflict
        is CollaborationError.InvalidState -> GroupTasksUiError.InvalidState
        is CollaborationError.Validation -> GroupTasksUiError.ValidationRejected
        CollaborationError.MemberNotFound,
        CollaborationError.AlreadyMember,
        CollaborationError.InviteAlreadyPending -> GroupTasksUiError.InvalidState
        is CollaborationError.MappingFailure,
        is CollaborationError.Unknown -> GroupTasksUiError.Unknown
        is CollaborationError.SyncRejected -> GroupTasksUiError.InvalidState
    }

    private fun restoredGroupId(): CollaborationGroupId? = runCatching {
        savedStateHandle.get<String>(SELECTED_GROUP_ID_KEY)
            ?.takeIf(String::isNotBlank)
            ?.let(::CollaborationGroupId)
    }.getOrNull()

    private fun restoredTaskId(): GroupTaskId? = runCatching {
        savedStateHandle.get<String>(SELECTED_TASK_ID_KEY)
            ?.takeIf(String::isNotBlank)
            ?.let(::GroupTaskId)
    }.getOrNull()

    override fun onCleared() {
        cancelGroupObservations()
        selectionResolutionJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
        effectsChannel.close()
        super.onCleared()
    }

    private data class RetryRequest(
        val pending: PendingGroupTaskMutation,
        val operation: suspend () -> CollaborationMutationResult,
        val contextGeneration: Long
    )

    companion object {
        const val SELECTED_GROUP_ID_KEY = "groupTasks.selectedGroupId"
        const val SELECTED_TASK_ID_KEY = "groupTasks.selectedTaskId"
        private const val MAX_REMINDER_OFFSETS = 5
    }
}
