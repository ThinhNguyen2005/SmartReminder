package com.smartreminder.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.collaboration.GroupPermissionEvaluator
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModel(
    private val repository: CollaborationRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val groups = MutableStateFlow(emptyList<CollaborationGroup>())
    private val invites = MutableStateFlow(emptyList<GroupInvite>())
    private val members = MutableStateFlow(emptyList<GroupMember>())
    private val selectedGroupId = MutableStateFlow(restoredGroupId())
    private val pendingMutation = MutableStateFlow<PendingGroupsMutation?>(null)
    private val dialog = MutableStateFlow<GroupsDialog?>(null)
    private val error = MutableStateFlow<GroupsUiError?>(null)
    private val isRefreshing = MutableStateFlow(true)
    private val refreshCompleted = MutableStateFlow(false)
    private val isOffline = MutableStateFlow(false)
    private val effectsChannel = Channel<GroupsEffect>(Channel.BUFFERED)
    private val _uiState = MutableStateFlow(GroupsUiState(isRefreshing = true))

    val uiState = _uiState.asStateFlow()
    val effects: Flow<GroupsEffect> = effectsChannel.receiveAsFlow()

    init {
        observeGroups()
        observeInvites()
        observeSelectedMembers()
        refresh()
    }

    fun onAction(action: GroupsAction) {
        when (action) {
            is GroupsAction.OpenGroup -> openGroup(action.groupId)
            GroupsAction.Back -> clearSelection()
            GroupsAction.Refresh -> refresh()
            GroupsAction.OpenCreateGroupDialog -> setDialog(GroupsDialog.CreateGroup)
            GroupsAction.OpenUpdateGroupDialog -> selectedDialog { GroupsDialog.UpdateGroup(it) }
            GroupsAction.OpenInviteMemberDialog -> selectedDialog { GroupsDialog.InviteMember(it) }
            is GroupsAction.OpenChangeMemberRoleDialog -> selectedDialog {
                GroupsDialog.ChangeMemberRole(it, action.memberId)
            }
            is GroupsAction.OpenRemoveMemberDialog -> selectedDialog {
                GroupsDialog.RemoveMember(it, action.memberId)
            }
            is GroupsAction.OpenTransferOwnershipDialog -> selectedDialog {
                GroupsDialog.TransferOwnership(it, action.memberId)
            }
            GroupsAction.OpenLeaveGroupDialog -> {
                if (selectedGroupId.value != null) setDialog(GroupsDialog.LeaveGroup)
                else setError(GroupsUiError.NotFound)
            }
            GroupsAction.OpenDeleteGroupDialog -> {
                if (selectedGroupId.value != null) setDialog(GroupsDialog.DeleteGroup)
                else setError(GroupsUiError.NotFound)
            }
            is GroupsAction.OpenInviteResponseDialog -> setDialog(
                GroupsDialog.RespondToInvite(action.inviteId)
            )
            GroupsAction.DismissDialog -> setDialog(null)
            is GroupsAction.CreateGroup -> createGroup(action)
            is GroupsAction.UpdateGroup -> updateGroup(action)
            is GroupsAction.InviteMember -> inviteMember(action)
            is GroupsAction.AcceptInvite -> acceptInvite(action.inviteId)
            is GroupsAction.DeclineInvite -> declineInvite(action.inviteId)
            is GroupsAction.ChangeMemberRole -> changeMemberRole(action)
            is GroupsAction.RemoveMember -> removeMember(action.memberId)
            is GroupsAction.TransferOwnership -> transferOwnership(action.newOwnerId)
            GroupsAction.LeaveGroup -> leaveGroup()
            GroupsAction.DeleteGroup -> deleteGroup()
            GroupsAction.DismissError -> setError(null)
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            repository.observeGroups()
                .catch { throwable ->
                    setError(mapThrowable(throwable))
                }
                .collect { observedGroups ->
                    groups.value = observedGroups
                    normalizeSelectionIfLoaded()
                    render()
                }
        }
    }

    private fun observeInvites() {
        viewModelScope.launch {
            repository.observeInvites()
                .catch { throwable -> setError(mapThrowable(throwable)) }
                .collect { observedInvites ->
                    invites.value = observedInvites
                    render()
                }
        }
    }

    private fun observeSelectedMembers() {
        viewModelScope.launch {
            selectedGroupId
                .flatMapLatest { groupId ->
                    groupId?.let(repository::observeMembers) ?: flowOf(emptyList())
                }
                .catch { throwable -> setError(mapThrowable(throwable)) }
                .collect { observedMembers ->
                    members.value = observedMembers
                    render()
                }
        }
    }

    private fun refresh() {
        if (pendingMutation.value != null) return
        isRefreshing.value = true
        error.value = null
        isOffline.value = false
        render()
        viewModelScope.launch {
            val result = try {
                repository.refreshGroups()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Exception) {
                CollaborationMutationResult.Failure(mapThrowableToDomain(throwable))
            }
            refreshCompleted.value = true
            isRefreshing.value = false
            applyRefreshResult(result)
            normalizeSelectionIfLoaded()
            tryRefreshInvites()
            render()
        }
    }

    private suspend fun tryRefreshInvites() {
        try {
            repository.refreshInvites()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Group content remains usable when an invite refresh is unavailable.
        }
    }

    private fun applyRefreshResult(result: CollaborationMutationResult) {
        when (result) {
            CollaborationMutationResult.Applied,
            CollaborationMutationResult.Queued -> {
                error.value = null
                isOffline.value = false
            }
            CollaborationMutationResult.NetworkRequired -> {
                error.value = GroupsUiError.Offline
                isOffline.value = true
            }
            is CollaborationMutationResult.Failure -> applyError(result.error)
            is CollaborationMutationResult.Conflict -> applyError(result.error)
            is CollaborationMutationResult.NotAuthorized -> applyError(result.error)
            is CollaborationMutationResult.InvalidState -> applyError(result.error)
        }
    }

    private fun openGroup(groupId: CollaborationGroupId) {
        if (groups.value.none { it.id == groupId }) {
            setError(GroupsUiError.NotFound)
            return
        }
        selectGroup(groupId)
        viewModelScope.launch {
            try {
                val result = repository.refreshGroup(groupId)
                when (result) {
                    CollaborationMutationResult.Applied,
                    CollaborationMutationResult.Queued -> Unit
                    CollaborationMutationResult.NetworkRequired -> {
                        applyError(CollaborationError.NetworkUnavailable())
                    }
                    is CollaborationMutationResult.Failure -> applyError(result.error)
                    is CollaborationMutationResult.Conflict -> applyError(result.error)
                    is CollaborationMutationResult.NotAuthorized -> applyError(result.error)
                    is CollaborationMutationResult.InvalidState -> applyError(result.error)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Exception) {
                setError(mapThrowable(throwable))
            }
            render()
        }
    }

    private fun selectedDialog(builder: (CollaborationGroupId) -> GroupsDialog) {
        selectedGroupId.value?.let { setDialog(builder(it)) } ?: setError(GroupsUiError.NotFound)
    }

    private fun createGroup(action: GroupsAction.CreateGroup) {
        if (dialog.value == null) setDialog(GroupsDialog.CreateGroup)
        val command = try {
            CreateGroupCommand(action.name, action.description)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(validation.message ?: "Invalid group"))
            return
        }
        mutate(PendingGroupsMutation(GroupsMutation.CREATE_GROUP)) {
            repository.createGroup(command)
        }
    }

    private fun updateGroup(action: GroupsAction.UpdateGroup) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val command = try {
            UpdateGroupCommand(groupId, action.name, action.description)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(validation.message ?: "Invalid group"))
            return
        }
        mutate(PendingGroupsMutation(GroupsMutation.UPDATE_GROUP, groupId)) {
            repository.updateGroup(command)
        }
    }

    private fun inviteMember(action: GroupsAction.InviteMember) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val command = try {
            InviteMemberCommand(groupId, action.email)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(validation.message ?: "Invalid email"))
            return
        }
        mutate(PendingGroupsMutation(GroupsMutation.INVITE_MEMBER, groupId)) {
            repository.inviteMember(command)
        }
    }

    private fun acceptInvite(inviteId: GroupInviteId) {
        mutate(PendingGroupsMutation(GroupsMutation.ACCEPT_INVITE, inviteId = inviteId)) {
            repository.acceptInvite(AcceptInviteCommand(inviteId))
        }
    }

    private fun declineInvite(inviteId: GroupInviteId) {
        mutate(PendingGroupsMutation(GroupsMutation.DECLINE_INVITE, inviteId = inviteId)) {
            repository.declineInvite(DeclineInviteCommand(inviteId))
        }
    }

    private fun changeMemberRole(action: GroupsAction.ChangeMemberRole) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val command = try {
            ChangeMemberRoleCommand(groupId, action.memberId, action.targetRole)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(validation.message ?: "Invalid role"))
            return
        }
        mutate(PendingGroupsMutation(GroupsMutation.CHANGE_MEMBER_ROLE, groupId, action.memberId)) {
            repository.changeMemberRole(command)
        }
    }

    private fun removeMember(memberId: UserId) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        mutate(PendingGroupsMutation(GroupsMutation.REMOVE_MEMBER, groupId, memberId)) {
            repository.removeMember(RemoveMemberCommand(groupId, memberId))
        }
    }

    private fun transferOwnership(newOwnerId: UserId) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        mutate(PendingGroupsMutation(GroupsMutation.TRANSFER_OWNERSHIP, groupId, newOwnerId)) {
            repository.transferOwnership(TransferOwnershipCommand(groupId, newOwnerId))
        }
    }

    private fun leaveGroup() {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        mutate(PendingGroupsMutation(GroupsMutation.LEAVE_GROUP, groupId)) {
            repository.leaveGroup(LeaveGroupCommand(groupId))
        }
    }

    private fun deleteGroup() {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        mutate(PendingGroupsMutation(GroupsMutation.DELETE_GROUP, groupId)) {
            repository.deleteGroup(DeleteGroupCommand(groupId))
        }
    }

    private fun mutate(
        mutation: PendingGroupsMutation,
        action: suspend () -> CollaborationMutationResult
    ) {
        if (pendingMutation.value != null) return
        pendingMutation.value = mutation
        error.value = null
        render()
        viewModelScope.launch {
            val result = try {
                action()
            } catch (cancelled: CancellationException) {
                pendingMutation.value = null
                render()
                throw cancelled
            } catch (throwable: Exception) {
                CollaborationMutationResult.Failure(mapThrowableToDomain(throwable))
            }
            pendingMutation.value = null
            when (result) {
                CollaborationMutationResult.Applied,
                CollaborationMutationResult.Queued -> {
                    dialog.value = null
                    error.value = null
                    isOffline.value = false
                    effectsChannel.trySend(GroupsEffect.MutationCompleted(mutation.mutation))
                    if (mutation.mutation == GroupsMutation.LEAVE_GROUP ||
                        mutation.mutation == GroupsMutation.DELETE_GROUP
                    ) {
                        clearSelection(emitEffect = false)
                        effectsChannel.trySend(GroupsEffect.NavigateToList)
                    }
                }
                CollaborationMutationResult.NetworkRequired -> {
                    applyError(CollaborationError.NetworkUnavailable())
                    isOffline.value = true
                }
                is CollaborationMutationResult.Failure -> applyError(result.error)
                is CollaborationMutationResult.Conflict -> applyError(result.error)
                is CollaborationMutationResult.NotAuthorized -> applyError(result.error)
                is CollaborationMutationResult.InvalidState -> applyError(result.error)
            }
            render()
        }
    }

    private fun selectGroup(groupId: CollaborationGroupId?) {
        selectedGroupId.value = groupId
        savedStateHandle[SELECTED_GROUP_ID_KEY] = groupId?.value
        members.value = emptyList()
        render()
    }

    private fun clearSelection(emitEffect: Boolean = true) {
        if (selectedGroupId.value == null) return
        selectGroup(null)
        dialog.value = null
        if (emitEffect) effectsChannel.trySend(GroupsEffect.NavigateToList)
        render()
    }

    private fun setDialog(nextDialog: GroupsDialog?) {
        dialog.value = nextDialog
        render()
    }

    private fun setError(nextError: GroupsUiError?) {
        error.value = nextError
        render()
    }

    private fun applyError(domainError: CollaborationError) {
        error.value = mapError(domainError)
        isOffline.value = domainError is CollaborationError.NetworkUnavailable
    }

    private fun normalizeSelectionIfLoaded() {
        val selected = selectedGroupId.value ?: return
        if (!refreshCompleted.value) return
        if (groups.value.none { it.id == selected }) selectGroup(null)
    }

    private fun render() {
        val selectedId = selectedGroupId.value
        val selected = groups.value.firstOrNull { it.id == selectedId }
        val detail = selected?.let {
            GroupDetailUiModel(
                group = it,
                members = members.value,
                permissionsByMemberId = members.value.associate { member ->
                    member.userId to GroupPermissionEvaluator.permissionsFor(member.userId, member.role)
                }
            )
        }
        val hasCachedData = groups.value.isNotEmpty()
        val loadState = when {
            isRefreshing.value && hasCachedData -> GroupsLoadState.OFFLINE_REFRESHING
            isRefreshing.value -> GroupsLoadState.LOADING
            error.value != null && !hasCachedData -> GroupsLoadState.ERROR
            isOffline.value && hasCachedData -> GroupsLoadState.CACHED_OFFLINE
            error.value != null -> GroupsLoadState.ERROR
            groups.value.isEmpty() -> GroupsLoadState.EMPTY
            else -> GroupsLoadState.CONTENT
        }
        _uiState.value = GroupsUiState(
            loadState = loadState,
            screen = if (detail == null) GroupsScreen.LIST else GroupsScreen.DETAIL,
            groups = groups.value,
            pendingInvites = invites.value.filter { it.status == GroupInviteStatus.PENDING },
            selectedGroupId = selectedId,
            selectedGroup = detail,
            dialog = dialog.value,
            pendingMutation = pendingMutation.value,
            error = error.value,
            isCached = hasCachedData,
            isOffline = isOffline.value,
            isRefreshing = isRefreshing.value
        )
    }

    private fun restoredGroupId(): CollaborationGroupId? =
        savedStateHandle.get<String>(SELECTED_GROUP_ID_KEY)
            ?.takeIf(String::isNotBlank)
            ?.let(::CollaborationGroupId)

    private fun mapThrowableToDomain(throwable: Throwable): CollaborationError =
        CollaborationError.Unknown(throwable)

    private fun mapThrowable(throwable: Throwable): GroupsUiError =
        mapError(mapThrowableToDomain(throwable))

    private fun mapError(domainError: CollaborationError): GroupsUiError = when (domainError) {
        is CollaborationError.ConfigurationMissing -> GroupsUiError.MissingConfiguration
        is CollaborationError.NetworkUnavailable -> GroupsUiError.Offline
        CollaborationError.NotAuthorized -> GroupsUiError.NotAuthorized
        CollaborationError.NotFound -> GroupsUiError.NotFound
        CollaborationError.MemberNotFound -> GroupsUiError.MemberNotFound
        CollaborationError.AlreadyMember -> GroupsUiError.AlreadyMember
        CollaborationError.InviteAlreadyPending -> GroupsUiError.InviteAlreadyPending
        is CollaborationError.Validation -> GroupsUiError.Validation(domainError.message)
        is CollaborationError.Conflict -> GroupsUiError.Conflict(domainError.message)
        is CollaborationError.InvalidState -> GroupsUiError.InvalidState(domainError.message)
        is CollaborationError.MappingFailure -> GroupsUiError.MappingFailure(domainError.message)
        is CollaborationError.SyncRejected -> GroupsUiError.InvalidState(domainError.message)
        is CollaborationError.Unknown -> GroupsUiError.Unknown(domainError.cause)
    }

    companion object {
        const val SELECTED_GROUP_ID_KEY = "groups.selectedGroupId"
    }
}
