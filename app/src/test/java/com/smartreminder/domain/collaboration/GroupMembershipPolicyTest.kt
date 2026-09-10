package com.smartreminder.domain.collaboration

import com.smartreminder.domain.model.collaboration.GroupRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMembershipPolicyTest {

    @Test
    fun `administrative permission matrix follows group role rules`() {
        val expected = listOf(
            GroupRole.OWNER to GroupPermissions(
                canEditGroup = true,
                canInviteMember = true,
                canChangeRoles = true,
                canTransferOwnership = true,
                canDeleteGroup = true
            ),
            GroupRole.ADMIN to GroupPermissions(
                canEditGroup = true,
                canInviteMember = true,
                canChangeRoles = false,
                canTransferOwnership = false,
                canDeleteGroup = false
            ),
            GroupRole.MEMBER to GroupPermissions(
                canEditGroup = false,
                canInviteMember = false,
                canChangeRoles = false,
                canTransferOwnership = false,
                canDeleteGroup = false
            )
        )

        expected.forEach { (role, permissions) ->
            assertEquals(permissions, GroupPermissionEvaluator.permissionsFor(TEST_USER, role))
        }
    }

    @Test
    fun `ownership and leave delete rules are explicit`() {
        val roleCases = listOf(
            Triple(GroupRole.OWNER, GroupRole.MEMBER, true),
            Triple(GroupRole.OWNER, GroupRole.ADMIN, true),
            Triple(GroupRole.OWNER, GroupRole.OWNER, false),
            Triple(GroupRole.ADMIN, GroupRole.MEMBER, false),
            Triple(GroupRole.MEMBER, GroupRole.MEMBER, false)
        )
        roleCases.forEach { (actorRole, targetRole, expected) ->
            assertEquals(
                expected,
                GroupPermissionEvaluator.canTransferOwnership(actorRole, targetRole)
            )
        }

        assertTrue(GroupPermissionEvaluator.canLeaveGroup(GroupRole.ADMIN, isLastMember = false))
        assertTrue(GroupPermissionEvaluator.canLeaveGroup(GroupRole.MEMBER, isLastMember = true))
        assertFalse(GroupPermissionEvaluator.canLeaveGroup(GroupRole.OWNER, isLastMember = false))
        assertFalse(GroupPermissionEvaluator.canLeaveGroup(GroupRole.OWNER, isLastMember = true))

        assertTrue(GroupPermissionEvaluator.canDeleteGroup(GroupRole.OWNER))
        assertFalse(GroupPermissionEvaluator.canDeleteGroup(GroupRole.ADMIN))
        assertFalse(GroupPermissionEvaluator.canDeleteGroup(GroupRole.MEMBER))
    }

    @Test
    fun `role change and member removal preserve ownership`() {
        assertTrue(
            GroupPermissionEvaluator.canChangeMemberRole(
                actorRole = GroupRole.OWNER,
                targetRole = GroupRole.ADMIN,
                newRole = GroupRole.MEMBER
            )
        )
        assertFalse(
            GroupPermissionEvaluator.canChangeMemberRole(
                actorRole = GroupRole.OWNER,
                targetRole = GroupRole.OWNER,
                newRole = GroupRole.MEMBER
            )
        )
        assertFalse(
            GroupPermissionEvaluator.canChangeMemberRole(
                actorRole = GroupRole.OWNER,
                targetRole = GroupRole.MEMBER,
                newRole = GroupRole.OWNER
            )
        )
        assertTrue(GroupPermissionEvaluator.canRemoveMember(GroupRole.OWNER, GroupRole.ADMIN))
        assertFalse(GroupPermissionEvaluator.canRemoveMember(GroupRole.OWNER, GroupRole.OWNER))
    }

    private companion object {
        val TEST_USER = com.smartreminder.domain.model.collaboration.ids.UserId("user_1")
    }
}
