package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CollaborationMembershipContractTest {

    private val groupId = CollaborationGroupId("group_1")
    private val userId = UserId("user_1")

    @Test
    fun `group name value and commands trim surrounding whitespace`() {
        assertEquals("Project team", GroupName("  Project team  ").value)
        assertEquals("Project team", CreateGroupCommand("  Project team  ").name)
        assertEquals(
            "Project team",
            UpdateGroupCommand(groupId, "  Project team  ").name
        )
    }

    @Test
    fun `group name rejects blank input`() {
        listOf("", "   ", "\t\n").forEach { raw ->
            try {
                GroupName(raw)
                fail("Expected blank group name to be rejected: '$raw'")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun `invite email value and command trim and normalize lowercase`() {
        assertEquals("lan@example.com", InviteEmail("  LAN@Example.COM ").value)
        assertEquals(
            "lan@example.com",
            InviteMemberCommand(groupId, "  LAN@Example.COM ").email
        )
    }

    @Test
    fun `invite email rejects invalid input table`() {
        listOf("", " ", "plainaddress", "@example.com", "lan@example", "lan@.com").forEach { raw ->
            try {
                InviteEmail(raw)
                fail("Expected invalid invite email to be rejected: '$raw'")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun `invite email rejects malformed domain labels`() {
        listOf(
            "lan@example..com",
            "lan@-example.com",
            "lan@example-.com",
            "lan@sub..example.com"
        ).forEach { raw ->
            try {
                InviteEmail(raw)
                fail("Expected malformed domain to be rejected: '$raw'")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun `invite email accepts valid domain label table`() {
        listOf(
            "lan@example.com",
            "lan@sub.example.co.uk",
            "test+cue@domain.co.uk",
            "user-name@smart-reminder.vn"
        ).forEach { raw ->
            InviteEmail(raw)
        }
    }

    @Test
    fun `membership commands do not expose actor or caller selected create or invite role`() {
        val createFields = CreateGroupCommand::class.java.declaredFields.map { it.name }.toSet()
        val inviteFields = InviteMemberCommand::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("actorId" in createFields)
        assertFalse("actorId" in inviteFields)
        assertFalse("role" in createFields)
        assertFalse("role" in inviteFields)
    }

    @Test
    fun `change role command accepts only admin or member`() {
        assertEquals(
            GroupRole.ADMIN,
            ChangeMemberRoleCommand(groupId, userId, GroupRole.ADMIN).targetRole
        )
        assertEquals(
            GroupRole.MEMBER,
            ChangeMemberRoleCommand(groupId, userId, GroupRole.MEMBER).targetRole
        )

        try {
            ChangeMemberRoleCommand(groupId, userId, GroupRole.OWNER)
            fail("Ownership must only change through transferOwnership")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `repository exposes refresh and membership operations`() {
        val methodNames = CollaborationRepository::class.java.methods
            .map { it.name.substringBefore('-') }
            .toSet()
        listOf(
            "refreshGroups",
            "refreshGroup",
            "refreshInvites",
            "createGroup",
            "updateGroup",
            "inviteMember",
            "acceptInvite",
            "declineInvite",
            "changeMemberRole",
            "removeMember",
            "transferOwnership",
            "leaveGroup",
            "deleteGroup"
        ).forEach { methodName ->
            assertTrue("Missing repository operation: $methodName", methodName in methodNames)
        }

        assertEquals(GroupInviteId("invite_1"), AcceptInviteCommand(GroupInviteId("invite_1")).inviteId)
    }
}
