package com.smartreminder.ui.groups

import com.smartreminder.domain.collaboration.GroupPermissions
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId

enum class GroupsScreen {
    LIST,
    DETAIL
}

enum class GroupsLoadState {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
    CACHED_OFFLINE,
    OFFLINE_REFRESHING
}

enum class GroupsDetailLoadState {
    IDLE,
    LOADING,
    CONTENT,
    ERROR,
    CACHED_OFFLINE,
    OFFLINE_REFRESHING
}

sealed interface GroupsUiError {
    data object MissingConfiguration : GroupsUiError
    data object Offline : GroupsUiError
    data object NotAuthorized : GroupsUiError
    data object NotFound : GroupsUiError
    data object MemberNotFound : GroupsUiError
    data object AlreadyMember : GroupsUiError
    data object InviteAlreadyPending : GroupsUiError
    data class Validation(val message: String) : GroupsUiError
    data class Conflict(val message: String?) : GroupsUiError
    data class InvalidState(val message: String?) : GroupsUiError
    data class MappingFailure(val message: String) : GroupsUiError
    data class Unknown(val cause: Throwable? = null) : GroupsUiError
}

sealed interface GroupsDialog {
    data object CreateGroup : GroupsDialog
    data class UpdateGroup(val groupId: CollaborationGroupId) : GroupsDialog
    data class InviteMember(val groupId: CollaborationGroupId) : GroupsDialog
    data class ChangeMemberRole(
        val groupId: CollaborationGroupId,
        val memberId: UserId
    ) : GroupsDialog
    data class RemoveMember(
        val groupId: CollaborationGroupId,
        val memberId: UserId
    ) : GroupsDialog
    data class TransferOwnership(
        val groupId: CollaborationGroupId,
        val memberId: UserId
    ) : GroupsDialog
    data object LeaveGroup : GroupsDialog
    data object DeleteGroup : GroupsDialog
    data class RespondToInvite(val inviteId: GroupInviteId) : GroupsDialog
}

enum class GroupsMutation {
    CREATE_GROUP,
    UPDATE_GROUP,
    INVITE_MEMBER,
    ACCEPT_INVITE,
    DECLINE_INVITE,
    CHANGE_MEMBER_ROLE,
    REMOVE_MEMBER,
    TRANSFER_OWNERSHIP,
    LEAVE_GROUP,
    DELETE_GROUP
}

data class PendingGroupsMutation(
    val mutation: GroupsMutation,
    val groupId: CollaborationGroupId? = null,
    val memberId: UserId? = null,
    val inviteId: GroupInviteId? = null
)

/** Actor-relative actions for one target member in the selected group. */
data class GroupMemberUiPermissions(
    val canChangeRole: Boolean,
    val canRemove: Boolean,
    val canTransferOwnership: Boolean
)

data class GroupDetailUiModel(
    val group: CollaborationGroup,
    val members: List<GroupMember>,
    val permissionsByMemberId: Map<UserId, GroupPermissions>,
    val currentUserId: UserId? = null,
    val currentUserRole: GroupRole? = null,
    val actorPermissions: GroupPermissions? = null,
    val memberActionsByMemberId: Map<UserId, GroupMemberUiPermissions> = emptyMap(),
    val canLeaveGroup: Boolean = false
) {
    val id: CollaborationGroupId
        get() = group.id
}

data class GroupsUiState(
    val loadState: GroupsLoadState = GroupsLoadState.LOADING,
    val screen: GroupsScreen = GroupsScreen.LIST,
    val groups: List<CollaborationGroup> = emptyList(),
    val pendingInvites: List<GroupInvite> = emptyList(),
    val selectedGroupId: CollaborationGroupId? = null,
    val selectedGroup: GroupDetailUiModel? = null,
    val dialog: GroupsDialog? = null,
    val pendingMutation: PendingGroupsMutation? = null,
    val error: GroupsUiError? = null,
    val isCached: Boolean = false,
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val detailLoadState: GroupsDetailLoadState = GroupsDetailLoadState.IDLE,
    val detailError: GroupsUiError? = null
) {
    val isLoading: Boolean
        get() = loadState == GroupsLoadState.LOADING

    val isMutationInProgress: Boolean
        get() = pendingMutation != null
}
