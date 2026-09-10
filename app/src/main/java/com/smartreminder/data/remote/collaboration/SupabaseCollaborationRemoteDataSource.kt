package com.smartreminder.data.remote.collaboration

import com.smartreminder.data.remote.SupabaseManager
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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Narrow RPC transport seam for exact request tests without a live Supabase client. */
fun interface CollaborationRpcInvoker {
    suspend fun invoke(
        function: String,
        parameters: JsonObject
    ): CollaborationMutationEnvelopeRemoteDto
}

/** PostgREST/RPC implementation; no Supabase type escapes this data boundary. */
class SupabaseCollaborationRemoteDataSource private constructor(
    private val supabaseProvider: (() -> SupabaseClient)?,
    private val rpcInvoker: CollaborationRpcInvoker
) : CollaborationRemoteDataSource {

    constructor(supabase: SupabaseClient) : this(
        supabaseProvider = { supabase },
        rpcInvoker = CollaborationRpcInvoker { function, parameters ->
            supabase.postgrest.rpc(function, parameters).decodeAs()
        }
    )

    constructor(rpcInvoker: CollaborationRpcInvoker) : this(
        supabaseProvider = null,
        rpcInvoker = rpcInvoker
    )

    private val supabase: SupabaseClient
        get() = supabaseProvider?.invoke()
            ?: error("A Supabase client is required for PostgREST reads")

    override suspend fun fetchGroups(): List<CollaborationGroupRemoteDto> =
        supabase.from(GROUPS_TABLE).select().decodeList()

    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? =
        supabase.from(GROUPS_TABLE).select {
            filter { eq("id", groupId) }
        }.decodeSingleOrNull()

    override suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto> {
        val members = supabase.from(MEMBERS_TABLE).select {
            filter { eq("group_id", groupId) }
        }.decodeList<CollaborationMemberRemoteDto>()
        if (members.isEmpty()) return emptyList()

        // The profile query is RLS-scoped to the same active groups as the member query.
        val profiles = supabase.from(PROFILES_TABLE).select().decodeList<UserProfileRemoteDto>()
        val profilesById = profiles.associateBy(UserProfileRemoteDto::userId)
        return members.map { it.copy(profile = profilesById[it.userId]) }
    }

    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> =
        supabase.from(INVITES_TABLE).select().decodeList()

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(CREATE_GROUP_RPC, buildJsonObject {
            put("p_name", command.name)
            command.description?.let { put("p_description", it) }
        })

    override suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(UPDATE_GROUP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_name", command.name)
            command.description?.let { put("p_description", it) }
        })

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(INVITE_MEMBER_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_invitee_email", command.email)
        })

    override suspend fun acceptInvite(command: AcceptInviteCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(RESPOND_INVITE_RPC, buildJsonObject {
            put("p_invite_id", command.inviteId.value)
            put("p_accept", true)
        })

    override suspend fun declineInvite(command: DeclineInviteCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(RESPOND_INVITE_RPC, buildJsonObject {
            put("p_invite_id", command.inviteId.value)
            put("p_accept", false)
        })

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(CHANGE_MEMBER_ROLE_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_member_id", command.memberId.value)
            put("p_role", command.newRole.name)
        })

    override suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(REMOVE_MEMBER_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_member_id", command.memberId.value)
        })

    override suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(TRANSFER_OWNERSHIP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
            put("p_new_owner_id", command.newOwnerId.value)
        })

    override suspend fun leaveGroup(command: LeaveGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(LEAVE_GROUP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
        })

    override suspend fun deleteGroup(command: DeleteGroupCommand): CollaborationMutationEnvelopeRemoteDto =
        rpc(DELETE_GROUP_RPC, buildJsonObject {
            put("p_group_id", command.groupId.value)
        })

    private suspend fun rpc(
        function: String,
        parameters: JsonObject
    ): CollaborationMutationEnvelopeRemoteDto =
        rpcInvoker.invoke(function, parameters)

    companion object {
        const val GROUPS_TABLE = "collaboration_groups"
        const val MEMBERS_TABLE = "group_members"
        const val PROFILES_TABLE = "user_profiles"
        const val INVITES_TABLE = "group_invites"

        const val CREATE_GROUP_RPC = "create_collaboration_group"
        const val UPDATE_GROUP_RPC = "update_collaboration_group"
        const val INVITE_MEMBER_RPC = "invite_group_member"
        const val RESPOND_INVITE_RPC = "respond_group_invite"
        const val CHANGE_MEMBER_ROLE_RPC = "change_group_member_role"
        const val REMOVE_MEMBER_RPC = "remove_group_member"
        const val TRANSFER_OWNERSHIP_RPC = "transfer_group_ownership"
        const val LEAVE_GROUP_RPC = "leave_collaboration_group"
        const val DELETE_GROUP_RPC = "delete_collaboration_group"

        fun configured(): SupabaseCollaborationRemoteDataSource =
            SupabaseCollaborationRemoteDataSource(SupabaseManager.client)
    }
}
