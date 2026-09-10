package com.smartreminder.data.remote.collaboration

import com.smartreminder.data.remote.MissingSupabaseConfigurationException
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SupabaseCollaborationRemoteDataSourceTest {

    @Test
    fun `provider-backed remote defers missing configuration until feature access`() {
        var providerCalls = 0
        val remote = SupabaseCollaborationRemoteDataSource {
            providerCalls += 1
            throw MissingSupabaseConfigurationException("missing")
        }

        assertEquals(0, providerCalls)
        assertThrows(MissingSupabaseConfigurationException::class.java) {
            runTest { remote.fetchGroups() }
        }
        assertEquals(1, providerCalls)
    }

    @Test
    fun `all membership mutations use exact RPC names and parameters`() = runTest {
        val invoker = RecordingRpcInvoker()
        val remote = SupabaseCollaborationRemoteDataSource(invoker)
        val groupId = CollaborationGroupId("group-1")
        val inviteId = GroupInviteId("invite-1")
        val memberId = UserId("member-1")

        remote.createGroup(CreateGroupCommand("New group", "Description"))
        remote.updateGroup(UpdateGroupCommand(groupId, "Renamed", "Updated"))
        remote.inviteMember(InviteMemberCommand(groupId, "invitee@example.com"))
        remote.acceptInvite(AcceptInviteCommand(inviteId))
        remote.declineInvite(DeclineInviteCommand(inviteId))
        remote.changeMemberRole(ChangeMemberRoleCommand(groupId, memberId, GroupRole.ADMIN))
        remote.removeMember(RemoveMemberCommand(groupId, memberId))
        remote.transferOwnership(TransferOwnershipCommand(groupId, memberId))
        remote.leaveGroup(LeaveGroupCommand(groupId))
        remote.deleteGroup(DeleteGroupCommand(groupId))

        assertEquals(
            listOf(
                "create_collaboration_group",
                "update_collaboration_group",
                "invite_group_member",
                "respond_group_invite",
                "respond_group_invite",
                "change_group_member_role",
                "remove_group_member",
                "transfer_group_ownership",
                "leave_collaboration_group",
                "delete_collaboration_group"
            ),
            invoker.calls.map(RecordingRpcInvoker.Call::function)
        )

        assertEquals("New group", invoker.calls[0].parameters["p_name"]!!.jsonPrimitive.content)
        assertEquals("Description", invoker.calls[0].parameters["p_description"]!!.jsonPrimitive.content)
        assertEquals("group-1", invoker.calls[1].parameters["p_group_id"]!!.jsonPrimitive.content)
        assertEquals("Renamed", invoker.calls[1].parameters["p_name"]!!.jsonPrimitive.content)
        assertEquals("Updated", invoker.calls[1].parameters["p_description"]!!.jsonPrimitive.content)
        assertEquals("group-1", invoker.calls[2].parameters["p_group_id"]!!.jsonPrimitive.content)
        assertEquals("invitee@example.com", invoker.calls[2].parameters["p_invitee_email"]!!.jsonPrimitive.content)
        assertEquals("invite-1", invoker.calls[3].parameters["p_invite_id"]!!.jsonPrimitive.content)
        assertTrue(invoker.calls[3].parameters["p_accept"]!!.jsonPrimitive.boolean)
        assertEquals("invite-1", invoker.calls[4].parameters["p_invite_id"]!!.jsonPrimitive.content)
        assertFalse(invoker.calls[4].parameters["p_accept"]!!.jsonPrimitive.boolean)
        assertEquals("group-1", invoker.calls[5].parameters["p_group_id"]!!.jsonPrimitive.content)
        assertEquals("member-1", invoker.calls[5].parameters["p_member_id"]!!.jsonPrimitive.content)
        assertEquals("ADMIN", invoker.calls[5].parameters["p_role"]!!.jsonPrimitive.content)
        assertEquals("group-1", invoker.calls[6].parameters["p_group_id"]!!.jsonPrimitive.content)
        assertEquals("member-1", invoker.calls[6].parameters["p_member_id"]!!.jsonPrimitive.content)
        assertEquals("group-1", invoker.calls[7].parameters["p_group_id"]!!.jsonPrimitive.content)
        assertEquals("member-1", invoker.calls[7].parameters["p_new_owner_id"]!!.jsonPrimitive.content)
        assertEquals("group-1", invoker.calls[8].parameters["p_group_id"]!!.jsonPrimitive.content)
        assertEquals("group-1", invoker.calls[9].parameters["p_group_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `nullable descriptions are omitted from RPC payloads`() = runTest {
        val invoker = RecordingRpcInvoker()
        val remote = SupabaseCollaborationRemoteDataSource(invoker)

        remote.createGroup(CreateGroupCommand("New group"))
        remote.updateGroup(UpdateGroupCommand(CollaborationGroupId("group-1"), "Renamed"))

        assertFalse(invoker.calls[0].parameters.containsKey("p_description"))
        assertFalse(invoker.calls[1].parameters.containsKey("p_description"))
    }

    @Test
    fun `RPC envelope is returned without losing status error or data`() = runTest {
        val expected = CollaborationMutationEnvelopeRemoteDto(
            status = "CONFLICT",
            error = CollaborationErrorRemoteDto(
                code = "CONFLICT",
                detail = "group changed"
            ),
            data = buildJsonObject { put("group_id", "group-1") }
        )
        val invoker = RecordingRpcInvoker().apply { response = expected }
        val remote = SupabaseCollaborationRemoteDataSource(invoker)

        val actual = remote.updateGroup(
            UpdateGroupCommand(CollaborationGroupId("group-1"), "Renamed")
        )

        assertEquals(expected, actual)
    }
}

private class RecordingRpcInvoker : CollaborationRpcInvoker {
    data class Call(
        val function: String,
        val parameters: kotlinx.serialization.json.JsonObject
    )

    val calls = mutableListOf<Call>()
    var response = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")

    override suspend fun invoke(
        function: String,
        parameters: kotlinx.serialization.json.JsonObject
    ): CollaborationMutationEnvelopeRemoteDto {
        calls += Call(function, parameters)
        return response
    }
}
