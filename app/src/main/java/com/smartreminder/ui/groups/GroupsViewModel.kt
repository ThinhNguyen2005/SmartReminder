package com.smartreminder.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.collaboration.GroupPermissionEvaluator
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val memberCacheByGroupId = mutableMapOf<CollaborationGroupId, List<GroupMember>>()
    private val detailLoadState = MutableStateFlow(
        if (selectedGroupId.value == null) {
            GroupsDetailLoadState.IDLE
        } else {
            GroupsDetailLoadState.LOADING
        }
    )
    private val detailError = MutableStateFlow<GroupsUiError?>(null)
    /**
     * Tracks whether detail actions are restricted while a transient state is being rendered.
     * Explicit access loss is redacted before this state can expose cached permissions.
     */
    private val detailAccessRestricted = MutableStateFlow(false)
    private val currentUserId = MutableStateFlow(repository.currentUserId())
    private val pendingMutation = MutableStateFlow<PendingGroupsMutation?>(null)
    private val dialog = MutableStateFlow<GroupsDialog?>(null)
    private val error = MutableStateFlow<GroupsUiError?>(null)
    private val isRefreshing = MutableStateFlow(true)
    private val refreshCompleted = MutableStateFlow(false)
    private val isOffline = MutableStateFlow(false)
    private val effectsChannel = Channel<GroupsEffect>(Channel.BUFFERED)
    private val _uiState = MutableStateFlow(GroupsUiState(isRefreshing = true))

    /**
     * A successful create can precede its cache refresh. Keep the created id as
     * an explicit navigation intent until a later observation contains it; a
     * process-restored id has no such grace period and is normalized away when
     * the loaded cache proves it no longer exists.
     */
    private var pendingCreatedGroupId: CollaborationGroupId? = null

    private var refreshJob: Job? = null
    private var detailRefreshJob: Job? = null
    private var mutationJob: Job? = null
    private var refreshGeneration = 0L
    private var detailGeneration = 0L
    private var mutationGeneration = 0L

    val uiState = _uiState.asStateFlow()
    val effects: Flow<GroupsEffect> = effectsChannel.receiveAsFlow()

    init {
        observeIdentity()
        observeGroups()
        observeInvites()
        observeSelectedMembers()
        refresh()
    }

    private fun observeIdentity() {
        viewModelScope.launch {
            repository.observeCurrentUserId()
                .distinctUntilChanged()
                .collect { nextUserId ->
                    if (currentUserId.value == nextUserId) return@collect
                    currentUserId.value = nextUserId
                    repository.clearSessionCache()
                    resetForSessionBoundary()
                    if (nextUserId != null) {
                        refresh()
                    }
                }
        }
    }

    private fun resetForSessionBoundary() {
        refreshJob?.cancel()
        detailRefreshJob?.cancel()
        mutationJob?.cancel()
        refreshJob = null
        detailRefreshJob = null
        mutationJob = null
        ++refreshGeneration
        ++detailGeneration
        ++mutationGeneration
        groups.value = emptyList()
        invites.value = emptyList()
        members.value = emptyList()
        memberCacheByGroupId.clear()
        selectedGroupId.value = null
        savedStateHandle[SELECTED_GROUP_ID_KEY] = null
        pendingCreatedGroupId = null
        detailLoadState.value = GroupsDetailLoadState.IDLE
        detailError.value = null
        detailAccessRestricted.value = false
        pendingMutation.value = null
        dialog.value = null
        error.value = null
        refreshCompleted.value = false
        isOffline.value = false
        isRefreshing.value = currentUserId.value != null
        render()
    }

    fun onAction(action: GroupsAction) {
        if (pendingMutation.value != null && action.isBlockedDuringMutation()) return
        when (action) {
            is GroupsAction.OpenGroup -> openGroup(action.groupId)
            GroupsAction.Back -> clearSelection()
            GroupsAction.Refresh -> refresh()
            GroupsAction.OpenCreateGroupDialog -> setDialog(GroupsDialog.CreateGroup)
            GroupsAction.OpenUpdateGroupDialog -> openUpdateDialog()
            GroupsAction.OpenInviteMemberDialog -> openInviteDialog()
            is GroupsAction.OpenChangeMemberRoleDialog -> openChangeRoleDialog(action.memberId)
            is GroupsAction.OpenRemoveMemberDialog -> openRemoveMemberDialog(action.memberId)
            is GroupsAction.OpenTransferOwnershipDialog -> openTransferDialog(action.memberId)
            GroupsAction.OpenLeaveGroupDialog -> openLeaveDialog()
            GroupsAction.OpenDeleteGroupDialog -> openDeleteDialog()
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

    private fun GroupsAction.isBlockedDuringMutation(): Boolean = when (this) {
        is GroupsAction.OpenGroup,
        GroupsAction.Back,
        GroupsAction.Refresh,
        GroupsAction.OpenCreateGroupDialog,
        GroupsAction.OpenUpdateGroupDialog,
        GroupsAction.OpenInviteMemberDialog,
        is GroupsAction.OpenChangeMemberRoleDialog,
        is GroupsAction.OpenRemoveMemberDialog,
        is GroupsAction.OpenTransferOwnershipDialog,
        GroupsAction.OpenLeaveGroupDialog,
        GroupsAction.OpenDeleteGroupDialog,
        is GroupsAction.OpenInviteResponseDialog,
        GroupsAction.DismissDialog -> true
        else -> false
    }

    private fun observeGroups() {
        viewModelScope.launch {
            repository.observeGroups()
                .catch { throwable ->
                    setError(mapThrowable(throwable))
                }
                .collect { observedGroups ->
                    groups.value = observedGroups
                    reconcilePendingCreatedGroup()
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
                .catch { throwable ->
                    if (selectedGroupId.value != null) {
                        setDetailError(mapThrowable(throwable))
                    } else {
                        setError(mapThrowable(throwable))
                    }
                }
                .collect { observedMembers ->
                    members.value = observedMembers
                    selectedGroupId.value?.let { groupId ->
                        memberCacheByGroupId[groupId] = observedMembers
                        if (observedMembers.isNotEmpty() && detailLoadState.value == GroupsDetailLoadState.LOADING) {
                            detailLoadState.value = if (detailRefreshJob?.isActive == true) {
                                GroupsDetailLoadState.OFFLINE_REFRESHING
                            } else {
                                GroupsDetailLoadState.CONTENT
                            }
                        }
                    }
                    render()
                }
        }
    }

    private fun refresh() {
        if (pendingMutation.value != null || refreshJob?.isActive == true) return
        val requestGeneration = ++refreshGeneration
        isRefreshing.value = true
        error.value = null
        isOffline.value = false
        render()
        refreshJob = viewModelScope.launch {
            try {
                val result = try {
                    repository.refreshGroups()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Exception) {
                    CollaborationMutationResult.Failure(mapThrowableToDomain(throwable))
                }
                if (requestGeneration != refreshGeneration || pendingMutation.value != null) return@launch
                refreshCompleted.value = true
                applyRefreshResult(result)
                normalizeSelectionIfLoaded()
                tryRefreshInvites()
            } finally {
                if (requestGeneration == refreshGeneration) {
                    isRefreshing.value = false
                    refreshJob = null
                    render()
                }
            }
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
            is CollaborationMutationResult.Created -> {
                error.value = null
                isOffline.value = false
            }
            CollaborationMutationResult.Queued ->
                applyError(CollaborationError.InvalidState("Queued membership response is unsupported"))
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
        if (groups.value.none { it.id == groupId } && pendingCreatedGroupId != groupId) {
            setError(GroupsUiError.NotFound)
            return
        }
        if (selectedGroupId.value == groupId && detailRefreshJob?.isActive == true) return
        detailRefreshJob?.cancel()
        selectGroup(groupId)
        startDetailRefresh(groupId)
    }

    private fun startDetailRefresh(groupId: CollaborationGroupId) {
        if (selectedGroupId.value != groupId) selectGroup(groupId)
        detailRefreshJob?.cancel()
        val requestGeneration = ++detailGeneration
        detailRefreshJob = viewModelScope.launch {
            try {
                val result = repository.refreshGroup(groupId)
                if (requestGeneration != detailGeneration || selectedGroupId.value != groupId) {
                    return@launch
                }
                when (result) {
                    CollaborationMutationResult.Applied,
                    is CollaborationMutationResult.Created -> {
                        detailAccessRestricted.value = false
                        detailError.value = null
                        detailLoadState.value = GroupsDetailLoadState.CONTENT
                        isOffline.value = false
                        render()
                    }
                    CollaborationMutationResult.Queued ->
                        applyDetailError(CollaborationError.InvalidState("Queued membership response is unsupported"))
                    CollaborationMutationResult.NetworkRequired -> {
                        applyDetailError(CollaborationError.NetworkUnavailable())
                    }
                    is CollaborationMutationResult.Failure -> applyDetailError(result.error)
                    is CollaborationMutationResult.Conflict -> applyDetailError(result.error)
                    is CollaborationMutationResult.NotAuthorized -> applyDetailError(result.error)
                    is CollaborationMutationResult.InvalidState -> applyDetailError(result.error)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Exception) {
                if (requestGeneration == detailGeneration && selectedGroupId.value == groupId) {
                    setDetailError(mapThrowable(throwable))
                }
            } finally {
                if (requestGeneration == detailGeneration) detailRefreshJob = null
            }
        }
    }

    private fun openUpdateDialog() {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        if (actorPermissions()?.canEditGroup == true) {
            setDialog(GroupsDialog.UpdateGroup(groupId))
        } else {
            denyDialog()
        }
    }

    private fun openInviteDialog() {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        if (actorPermissions()?.canInviteMember == true) {
            setDialog(GroupsDialog.InviteMember(groupId))
        } else {
            denyDialog()
        }
    }

    private fun openChangeRoleDialog(memberId: UserId) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val target = findMember(memberId) ?: return setError(GroupsUiError.MemberNotFound)
        val actorRole = actorRole() ?: return denyDialog()
        val alternateRole = if (target.role == GroupRole.ADMIN) {
            GroupRole.MEMBER
        } else {
            GroupRole.ADMIN
        }
        if (GroupPermissionEvaluator.canChangeMemberRole(actorRole, target.role, alternateRole)) {
            setDialog(GroupsDialog.ChangeMemberRole(groupId, memberId))
        } else {
            denyDialog()
        }
    }

    private fun openRemoveMemberDialog(memberId: UserId) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val target = findMember(memberId) ?: return setError(GroupsUiError.MemberNotFound)
        val actorRole = actorRole() ?: return denyDialog()
        if (GroupPermissionEvaluator.canRemoveMember(actorRole, target.role)) {
            setDialog(GroupsDialog.RemoveMember(groupId, memberId))
        } else {
            denyDialog()
        }
    }

    private fun openTransferDialog(memberId: UserId) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val target = findMember(memberId) ?: return setError(GroupsUiError.MemberNotFound)
        val actorRole = actorRole() ?: return denyDialog()
        if (GroupPermissionEvaluator.canTransferOwnership(actorRole, target.role)) {
            setDialog(GroupsDialog.TransferOwnership(groupId, memberId))
        } else {
            denyDialog()
        }
    }

    private fun openLeaveDialog() {
        if (selectedGroupId.value == null) return setError(GroupsUiError.NotFound)
        val role = actorRole()
        if (role != null && GroupPermissionEvaluator.canLeaveGroup(role, members.value.size == 1)) {
            setDialog(GroupsDialog.LeaveGroup)
        } else {
            denyDialog()
        }
    }

    private fun openDeleteDialog() {
        if (selectedGroupId.value == null) return setError(GroupsUiError.NotFound)
        if (actorRole()?.let(GroupPermissionEvaluator::canDeleteGroup) == true) {
            setDialog(GroupsDialog.DeleteGroup)
        } else {
            denyDialog()
        }
    }

    private fun actorRole(): GroupRole? {
        if (!cachedPermissionsAreUsable()) return null
        return currentUserId.value?.let { id -> members.value.firstOrNull { it.userId == id }?.role }
    }

    private fun actorPermissions() = actorRole()?.let { role ->
        currentUserId.value?.let { GroupPermissionEvaluator.permissionsFor(it, role) }
    }

    /**
     * A cached detail remains useful after an offline failure, but an authorization or not-found
     * response makes cached actor permissions unsafe to use for mutation affordances.
     */
    private fun cachedPermissionsAreUsable(): Boolean =
        !detailAccessRestricted.value &&
            detailError.value !is GroupsUiError.NotAuthorized &&
            detailError.value !is GroupsUiError.NotFound

    private fun findMember(memberId: UserId): GroupMember? =
        members.value.firstOrNull { it.userId == memberId }

    private fun denyDialog() {
        dialog.value = null
        setError(GroupsUiError.NotAuthorized)
    }

    private fun createGroup(action: GroupsAction.CreateGroup) {
        if (dialog.value == null) setDialog(GroupsDialog.CreateGroup)
        val command = try {
            CreateGroupCommand(action.name, action.description)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(GroupsValidationKind.GROUP_NAME_REQUIRED))
            return
        }
        mutate(PendingGroupsMutation(GroupsMutation.CREATE_GROUP)) {
            repository.createGroup(command)
        }
    }

    private fun updateGroup(action: GroupsAction.UpdateGroup) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        if (actorPermissions()?.canEditGroup != true) return denyDialog()
        val command = try {
            UpdateGroupCommand(groupId, action.name, action.description)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(GroupsValidationKind.GROUP_NAME_REQUIRED))
            return
        }
        mutate(PendingGroupsMutation(GroupsMutation.UPDATE_GROUP, groupId)) {
            repository.updateGroup(command)
        }
    }

    private fun inviteMember(action: GroupsAction.InviteMember) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        if (actorPermissions()?.canInviteMember != true) return denyDialog()
        val command = try {
            InviteMemberCommand(groupId, action.email)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(GroupsValidationKind.EMAIL_INVALID))
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
        val target = findMember(action.memberId) ?: return setError(GroupsUiError.MemberNotFound)
        val actorRole = actorRole() ?: return denyDialog()
        if (!GroupPermissionEvaluator.canChangeMemberRole(actorRole, target.role, action.targetRole)) {
            return denyDialog()
        }
        val command = try {
            ChangeMemberRoleCommand(groupId, action.memberId, action.targetRole)
        } catch (validation: IllegalArgumentException) {
            setError(GroupsUiError.Validation(GroupsValidationKind.ROLE_INVALID))
            return
        }
        mutate(PendingGroupsMutation(GroupsMutation.CHANGE_MEMBER_ROLE, groupId, action.memberId)) {
            repository.changeMemberRole(command)
        }
    }

    private fun removeMember(memberId: UserId) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val target = findMember(memberId) ?: return setError(GroupsUiError.MemberNotFound)
        val actorRole = actorRole() ?: return denyDialog()
        if (!GroupPermissionEvaluator.canRemoveMember(actorRole, target.role)) return denyDialog()
        mutate(PendingGroupsMutation(GroupsMutation.REMOVE_MEMBER, groupId, memberId)) {
            repository.removeMember(RemoveMemberCommand(groupId, memberId))
        }
    }

    private fun transferOwnership(newOwnerId: UserId) {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val target = findMember(newOwnerId) ?: return setError(GroupsUiError.MemberNotFound)
        val actorRole = actorRole() ?: return denyDialog()
        if (!GroupPermissionEvaluator.canTransferOwnership(actorRole, target.role)) {
            return denyDialog()
        }
        mutate(PendingGroupsMutation(GroupsMutation.TRANSFER_OWNERSHIP, groupId, newOwnerId)) {
            repository.transferOwnership(TransferOwnershipCommand(groupId, newOwnerId))
        }
    }

    private fun leaveGroup() {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        val actorRole = actorRole() ?: return denyDialog()
        if (!GroupPermissionEvaluator.canLeaveGroup(actorRole, members.value.size == 1)) {
            return denyDialog()
        }
        mutate(PendingGroupsMutation(GroupsMutation.LEAVE_GROUP, groupId)) {
            repository.leaveGroup(LeaveGroupCommand(groupId))
        }
    }

    private fun deleteGroup() {
        val groupId = selectedGroupId.value ?: return setError(GroupsUiError.NotFound)
        if (actorRole()?.let(GroupPermissionEvaluator::canDeleteGroup) != true) {
            return denyDialog()
        }
        mutate(PendingGroupsMutation(GroupsMutation.DELETE_GROUP, groupId)) {
            repository.deleteGroup(DeleteGroupCommand(groupId))
        }
    }

    private fun mutate(
        mutation: PendingGroupsMutation,
        action: suspend () -> CollaborationMutationResult
    ) {
        if (pendingMutation.value != null || mutationJob?.isActive == true) return
        refreshJob?.cancel()
        detailRefreshJob?.cancel()
        refreshJob = null
        detailRefreshJob = null
        isRefreshing.value = false
        ++refreshGeneration
        ++detailGeneration
        val requestGeneration = ++mutationGeneration
        pendingMutation.value = mutation
        error.value = null
        render()
        mutationJob = viewModelScope.launch {
            try {
                val result = try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Exception) {
                    CollaborationMutationResult.Failure(mapThrowableToDomain(throwable))
                }
                if (requestGeneration != mutationGeneration || pendingMutation.value != mutation) {
                    return@launch
                }
                pendingMutation.value = null
                when (result) {
                    CollaborationMutationResult.Applied,
                    is CollaborationMutationResult.Created -> {
                        dialog.value = null
                        error.value = null
                        isOffline.value = false
                        effectsChannel.trySend(GroupsEffect.MutationCompleted(mutation.mutation))
                        if (result is CollaborationMutationResult.Created &&
                            mutation.mutation == GroupsMutation.CREATE_GROUP
                        ) {
                            pendingCreatedGroupId = result.groupId
                            selectGroup(result.groupId)
                            if (members.value.isEmpty()) {
                                startDetailRefresh(result.groupId)
                            } else {
                                detailLoadState.value = GroupsDetailLoadState.CONTENT
                                render()
                            }
                            reconcilePendingCreatedGroup()
                            effectsChannel.trySend(GroupsEffect.NavigateToDetail(result.groupId))
                        }
                        if (mutation.mutation == GroupsMutation.LEAVE_GROUP ||
                            mutation.mutation == GroupsMutation.DELETE_GROUP
                        ) {
                            clearSelection(emitEffect = false)
                            effectsChannel.trySend(GroupsEffect.NavigateToList)
                        }
                    }
                    CollaborationMutationResult.Queued ->
                        applyMutationError(
                            CollaborationError.InvalidState("Queued membership response is unsupported"),
                            mutation
                        )
                    CollaborationMutationResult.NetworkRequired -> {
                        applyError(CollaborationError.NetworkUnavailable())
                        isOffline.value = true
                    }
                    is CollaborationMutationResult.Failure -> applyMutationError(result.error, mutation)
                    is CollaborationMutationResult.Conflict -> applyMutationError(result.error, mutation)
                    is CollaborationMutationResult.NotAuthorized -> applyMutationError(result.error, mutation)
                    is CollaborationMutationResult.InvalidState -> applyMutationError(result.error, mutation)
                }
            } catch (cancelled: CancellationException) {
                if (requestGeneration == mutationGeneration && pendingMutation.value == mutation) {
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

    private fun selectGroup(groupId: CollaborationGroupId?) {
        val previousGroupId = selectedGroupId.value
        val preserveRestrictedDetail =
            groupId != null && previousGroupId == groupId && detailAccessRestricted.value
        if (groupId == null) {
            pendingCreatedGroupId = null
            ++detailGeneration
            detailRefreshJob?.cancel()
            detailRefreshJob = null
            detailAccessRestricted.value = false
        } else {
            if (previousGroupId != groupId) detailAccessRestricted.value = false
            if (groupId != pendingCreatedGroupId) pendingCreatedGroupId = null
        }
        selectedGroupId.value = groupId
        savedStateHandle[SELECTED_GROUP_ID_KEY] = groupId?.value
        if (groupId == null) {
            members.value = emptyList()
            detailLoadState.value = GroupsDetailLoadState.IDLE
            detailError.value = null
        } else {
            members.value = memberCacheByGroupId[groupId].orEmpty()
            if (!preserveRestrictedDetail) detailError.value = null
            detailLoadState.value = if (members.value.isEmpty()) {
                GroupsDetailLoadState.LOADING
            } else {
                GroupsDetailLoadState.OFFLINE_REFRESHING
            }
        }
        render()
    }

    private fun clearSelection(emitEffect: Boolean = true) {
        if (selectedGroupId.value == null) return
        selectGroup(null)
        dialog.value = null
        error.value = null
        if (emitEffect) effectsChannel.trySend(GroupsEffect.NavigateToList)
        render()
    }

    private fun setDialog(nextDialog: GroupsDialog?) {
        dialog.value = nextDialog
        if (nextDialog == null) error.value = null
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

    private fun applyDetailError(domainError: CollaborationError) {
        if (domainError is CollaborationError.NotAuthorized || domainError is CollaborationError.NotFound) {
            redactSelectedGroup(domainError)
            return
        }
        setDetailError(mapError(domainError))
        isOffline.value = domainError is CollaborationError.NetworkUnavailable
    }

    private fun applyMutationError(domainError: CollaborationError, mutation: PendingGroupsMutation) {
        if (domainError is CollaborationError.NotAuthorized || domainError is CollaborationError.NotFound) {
            if (mutation.groupId != null) {
                redactGroup(mutation.groupId, domainError)
            } else if (mutation.inviteId != null) {
                invites.value = invites.value.filterNot { it.id == mutation.inviteId }
                applyError(domainError)
            } else {
                applyError(domainError)
            }
            return
        }
        applyError(domainError)
    }

    private fun redactSelectedGroup(domainError: CollaborationError) {
        selectedGroupId.value?.let { redactGroup(it, domainError) } ?: applyError(domainError)
    }

    private fun redactGroup(groupId: CollaborationGroupId, domainError: CollaborationError) {
        refreshJob?.cancel()
        detailRefreshJob?.cancel()
        ++refreshGeneration
        ++detailGeneration
        groups.value = groups.value.filterNot { it.id == groupId }
        memberCacheByGroupId.remove(groupId)
        if (selectedGroupId.value == groupId) {
            selectedGroupId.value = null
            savedStateHandle[SELECTED_GROUP_ID_KEY] = null
            members.value = emptyList()
            detailLoadState.value = GroupsDetailLoadState.IDLE
            detailError.value = null
            detailAccessRestricted.value = false
            dialog.value = null
            pendingCreatedGroupId = null
            effectsChannel.trySend(GroupsEffect.NavigateToList)
        }
        error.value = mapError(domainError)
        isOffline.value = false
        render()
    }

    private fun setDetailError(nextError: GroupsUiError?) {
        if (nextError is GroupsUiError.NotAuthorized || nextError is GroupsUiError.NotFound) {
            detailAccessRestricted.value = true
        }
        detailError.value = nextError
        detailLoadState.value = if (members.value.isNotEmpty()) {
            GroupsDetailLoadState.CACHED_OFFLINE
        } else {
            GroupsDetailLoadState.ERROR
        }
        render()
    }

    private fun normalizeSelectionIfLoaded() {
        val selected = selectedGroupId.value ?: return
        if (!refreshCompleted.value) return
        reconcilePendingCreatedGroup()
        if (groups.value.none { it.id == selected }) {
            if (pendingCreatedGroupId != selected) selectGroup(null)
            return
        }
        if (
            memberCacheByGroupId[selected].isNullOrEmpty() &&
            detailRefreshJob?.isActive != true &&
            (detailLoadState.value == GroupsDetailLoadState.LOADING || detailError.value != null)
        ) {
            startDetailRefresh(selected)
        }
    }

    private fun reconcilePendingCreatedGroup() {
        val createdId = pendingCreatedGroupId ?: return
        if (groups.value.any { it.id == createdId }) {
            pendingCreatedGroupId = null
            if (error.value == GroupsUiError.NotFound) error.value = null
        }
    }

    private fun render() {
        val selectedId = selectedGroupId.value
        val selected = groups.value.firstOrNull { it.id == selectedId }
        val detail = selected?.let {
            val actorRole = actorRole()
            val actorPermissions = actorPermissions()
            GroupDetailUiModel(
                group = it,
                members = members.value,
                permissionsByMemberId = if (cachedPermissionsAreUsable()) {
                    members.value.associate { member ->
                        member.userId to GroupPermissionEvaluator.permissionsFor(member.userId, member.role)
                    }
                } else {
                    emptyMap()
                },
                currentUserId = currentUserId.value,
                currentUserRole = actorRole,
                actorPermissions = actorPermissions,
                memberActionsByMemberId = if (cachedPermissionsAreUsable()) {
                    members.value.associate { member ->
                        member.userId to GroupMemberUiPermissions(
                            canChangeRole = actorRole?.let { role ->
                                GroupPermissionEvaluator.canChangeMemberRole(
                                    actorRole = role,
                                    targetRole = member.role,
                                    newRole = if (member.role == GroupRole.ADMIN) {
                                        GroupRole.MEMBER
                                    } else {
                                        GroupRole.ADMIN
                                    }
                                )
                            } == true,
                            canRemove = actorRole?.let { role ->
                                GroupPermissionEvaluator.canRemoveMember(role, member.role)
                            } == true,
                            canTransferOwnership = actorRole?.let { role ->
                                GroupPermissionEvaluator.canTransferOwnership(role, member.role)
                            } == true
                        )
                    }
                } else {
                    emptyMap()
                },
                canLeaveGroup = actorRole?.let { role ->
                    GroupPermissionEvaluator.canLeaveGroup(role, members.value.size == 1)
                } == true
            )
        }
        val hasCachedData = groups.value.isNotEmpty()
        val displayedError = error.value ?: detailError.value ?: if (
            !isRefreshing.value &&
            pendingCreatedGroupId == selectedId &&
            selectedId != null &&
            selected == null
        ) {
            GroupsUiError.NotFound
        } else {
            null
        }
        val loadState = when {
            isRefreshing.value && hasCachedData -> GroupsLoadState.OFFLINE_REFRESHING
            isRefreshing.value -> GroupsLoadState.LOADING
            displayedError != null && !hasCachedData -> GroupsLoadState.ERROR
            isOffline.value && hasCachedData -> GroupsLoadState.CACHED_OFFLINE
            displayedError != null -> GroupsLoadState.ERROR
            groups.value.isEmpty() -> GroupsLoadState.EMPTY
            else -> GroupsLoadState.CONTENT
        }
        _uiState.value = GroupsUiState(
            loadState = loadState,
            detailLoadState = detailLoadState.value,
            screen = if (selectedId == null) GroupsScreen.LIST else GroupsScreen.DETAIL,
            groups = groups.value,
            pendingInvites = invites.value.filter {
                it.status == GroupInviteStatus.PENDING && it.inviteeUserId == currentUserId.value
            },
            selectedGroupId = selectedId,
            selectedGroup = detail,
            dialog = dialog.value,
            pendingMutation = pendingMutation.value,
            error = displayedError,
            detailError = detailError.value,
            isCached = hasCachedData,
            isOffline = isOffline.value,
            isRefreshing = isRefreshing.value,
            isDetailAccessRestricted = detailAccessRestricted.value
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
        is CollaborationError.Validation -> GroupsUiError.Validation(GroupsValidationKind.GENERAL)
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
