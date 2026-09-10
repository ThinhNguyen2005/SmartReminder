package com.smartreminder.domain.collaboration

import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.UserId

data class GroupPermissions(
    val canEditGroup: Boolean,
    val canInviteMember: Boolean,
    val canChangeRoles: Boolean,
    val canTransferOwnership: Boolean,
    val canDeleteGroup: Boolean
)

object GroupPermissionEvaluator {

    fun permissionsFor(currentUserId: UserId, role: GroupRole): GroupPermissions {
        return when (role) {
            GroupRole.OWNER -> GroupPermissions(
                canEditGroup = true,
                canInviteMember = true,
                canChangeRoles = true,
                canTransferOwnership = true,
                canDeleteGroup = true
            )
            GroupRole.ADMIN -> GroupPermissions(
                canEditGroup = true,
                canInviteMember = true,
                canChangeRoles = false,
                canTransferOwnership = false,
                canDeleteGroup = false
            )
            GroupRole.MEMBER -> GroupPermissions(
                canEditGroup = false,
                canInviteMember = false,
                canChangeRoles = false,
                canTransferOwnership = false,
                canDeleteGroup = false
            )
        }
    }

    fun canRemoveMember(actorRole: GroupRole, targetRole: GroupRole): Boolean {
        if (targetRole == GroupRole.OWNER) return false
        return when (actorRole) {
            GroupRole.OWNER -> targetRole == GroupRole.ADMIN || targetRole == GroupRole.MEMBER
            GroupRole.ADMIN -> targetRole == GroupRole.MEMBER
            GroupRole.MEMBER -> false
        }
    }

    /** Only the current owner may change a non-owner member to ADMIN or MEMBER. */
    fun canChangeMemberRole(
        actorRole: GroupRole,
        targetRole: GroupRole,
        newRole: GroupRole
    ): Boolean {
        return actorRole == GroupRole.OWNER &&
            targetRole != GroupRole.OWNER &&
            (newRole == GroupRole.ADMIN || newRole == GroupRole.MEMBER)
    }

    /** Ownership is transferred atomically to an already accepted non-owner member. */
    fun canTransferOwnership(actorRole: GroupRole, targetRole: GroupRole): Boolean {
        return actorRole == GroupRole.OWNER && targetRole != GroupRole.OWNER
    }

    /** An owner must transfer ownership first; a non-owner may leave. */
    fun canLeaveGroup(actorRole: GroupRole, isLastMember: Boolean): Boolean {
        return actorRole != GroupRole.OWNER
    }

    fun canDeleteGroup(actorRole: GroupRole): Boolean = actorRole == GroupRole.OWNER
}
