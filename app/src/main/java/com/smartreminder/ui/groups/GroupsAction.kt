package com.smartreminder.ui.groups

import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId

sealed interface GroupsAction {
    data class OpenGroup(val groupId: CollaborationGroupId) : GroupsAction
    data object Back : GroupsAction
    data object Refresh : GroupsAction

    data object OpenCreateGroupDialog : GroupsAction
    data object OpenUpdateGroupDialog : GroupsAction
    data object OpenInviteMemberDialog : GroupsAction
    data class OpenChangeMemberRoleDialog(val memberId: UserId) : GroupsAction
    data class OpenRemoveMemberDialog(val memberId: UserId) : GroupsAction
    data class OpenTransferOwnershipDialog(val memberId: UserId) : GroupsAction
    data object OpenLeaveGroupDialog : GroupsAction
    data object OpenDeleteGroupDialog : GroupsAction
    data class OpenInviteResponseDialog(val inviteId: GroupInviteId) : GroupsAction
    data object DismissDialog : GroupsAction

    data class CreateGroup(val name: String, val description: String? = null) : GroupsAction
    data class UpdateGroup(val name: String, val description: String? = null) : GroupsAction
    data class InviteMember(val email: String) : GroupsAction
    data class AcceptInvite(val inviteId: GroupInviteId) : GroupsAction
    data class DeclineInvite(val inviteId: GroupInviteId) : GroupsAction
    data class ChangeMemberRole(val memberId: UserId, val targetRole: GroupRole) : GroupsAction
    data class RemoveMember(val memberId: UserId) : GroupsAction
    data class TransferOwnership(val newOwnerId: UserId) : GroupsAction
    data object LeaveGroup : GroupsAction
    data object DeleteGroup : GroupsAction

    data object DismissError : GroupsAction
}
