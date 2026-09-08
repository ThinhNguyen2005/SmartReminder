package com.smartreminder.domain.collaboration

import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPermissionEvaluatorTest {

    private val userId = UserId("user_1")

    @Test
    fun `owner permissions include all administrative capabilities`() {
        val permissions = GroupPermissionEvaluator.permissionsFor(userId, GroupRole.OWNER)
        assertTrue(permissions.canEditGroup)
        assertTrue(permissions.canInviteMember)
        assertTrue(permissions.canChangeRoles)
        assertTrue(permissions.canTransferOwnership)
        assertTrue(permissions.canDeleteGroup)
    }

    @Test
    fun `admin permissions include editing and inviting but not ownership or roles`() {
        val permissions = GroupPermissionEvaluator.permissionsFor(userId, GroupRole.ADMIN)
        assertTrue(permissions.canEditGroup)
        assertTrue(permissions.canInviteMember)
        assertFalse(permissions.canChangeRoles)
        assertFalse(permissions.canTransferOwnership)
        assertFalse(permissions.canDeleteGroup)
    }

    @Test
    fun `member permissions deny all group administration capabilities`() {
        val permissions = GroupPermissionEvaluator.permissionsFor(userId, GroupRole.MEMBER)
        assertFalse(permissions.canEditGroup)
        assertFalse(permissions.canInviteMember)
        assertFalse(permissions.canChangeRoles)
        assertFalse(permissions.canTransferOwnership)
        assertFalse(permissions.canDeleteGroup)
    }

    @Test
    fun `canRemoveMember rules table`() {
        // Owner can remove Admin or Member, but not another Owner (group must have exactly 1 owner)
        assertFalse(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.OWNER, targetRole = GroupRole.OWNER))
        assertTrue(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.OWNER, targetRole = GroupRole.ADMIN))
        assertTrue(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.OWNER, targetRole = GroupRole.MEMBER))

        // Admin can remove Member only
        assertFalse(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.ADMIN, targetRole = GroupRole.OWNER))
        assertFalse(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.ADMIN, targetRole = GroupRole.ADMIN))
        assertTrue(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.ADMIN, targetRole = GroupRole.MEMBER))

        // Member cannot remove anyone
        assertFalse(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.MEMBER, targetRole = GroupRole.OWNER))
        assertFalse(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.MEMBER, targetRole = GroupRole.ADMIN))
        assertFalse(GroupPermissionEvaluator.canRemoveMember(actorRole = GroupRole.MEMBER, targetRole = GroupRole.MEMBER))
    }
}
