package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.util.Locale
import java.util.regex.Pattern

/** A group name normalized at the domain boundary. */
class GroupName(raw: String) {
    val value: String = raw.trim()

    init {
        require(value.isNotEmpty()) { "group name must not be blank" }
    }

    override fun equals(other: Any?): Boolean = other is GroupName && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

/** An existing-account invite address normalized at the domain boundary. */
class InviteEmail(raw: String) {
    val value: String = raw.trim().lowercase(Locale.ROOT)

    init {
        require(EMAIL_PATTERN.matcher(value).matches()) { "invite email is invalid" }
    }

    override fun equals(other: Any?): Boolean = other is InviteEmail && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    private companion object {
        val EMAIL_PATTERN: Pattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$"
        )
    }
}

class CreateGroupCommand(
    name: String,
    val description: String? = null
) {
    val name: String = GroupName(name).value
}

class UpdateGroupCommand(
    val groupId: CollaborationGroupId,
    name: String,
    val description: String? = null
) {
    val name: String = GroupName(name).value
}

class InviteMemberCommand(
    val groupId: CollaborationGroupId,
    email: String
) {
    val email: String = InviteEmail(email).value
}

class AcceptInviteCommand(val inviteId: GroupInviteId)

class DeclineInviteCommand(val inviteId: GroupInviteId)

class ChangeMemberRoleCommand(
    val groupId: CollaborationGroupId,
    val memberId: UserId,
    val targetRole: GroupRole
) {
    init {
        require(targetRole == GroupRole.ADMIN || targetRole == GroupRole.MEMBER) {
            "target role must be ADMIN or MEMBER; ownership changes only through transferOwnership"
        }
    }

    val newRole: GroupRole
        get() = targetRole
}

class RemoveMemberCommand(
    val groupId: CollaborationGroupId,
    val memberId: UserId
) {
    val targetUserId: UserId
        get() = memberId
}

class TransferOwnershipCommand(
    val groupId: CollaborationGroupId,
    val newOwnerId: UserId
) {
    val targetUserId: UserId
        get() = newOwnerId
}

class LeaveGroupCommand(val groupId: CollaborationGroupId)

class DeleteGroupCommand(val groupId: CollaborationGroupId)
