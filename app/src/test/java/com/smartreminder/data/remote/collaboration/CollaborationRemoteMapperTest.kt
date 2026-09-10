package com.smartreminder.data.remote.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class CollaborationRemoteMapperTest {

    @Test
    fun `remote member profile maps to typed domain and cache rows`() {
        val dto = CollaborationMemberRemoteDto(
            groupId = "group-1",
            userId = "user-1",
            role = "OWNER",
            joinedAt = "2026-09-10T10:00:00Z",
            profile = UserProfileRemoteDto(
                userId = "user-1",
                displayName = "Lan",
                avatarUrl = "https://example.test/lan.png"
            )
        )

        val member = CollaborationRemoteMapper.toDomain(dto)
        val cache = CollaborationRemoteMapper.toCache(dto)

        assertEquals(CollaborationGroupId("group-1"), member.groupId)
        assertEquals(UserId("user-1"), member.userId)
        assertEquals(GroupRole.OWNER, member.role)
        assertEquals(Instant.parse("2026-09-10T10:00:00Z"), member.joinedAt)
        assertEquals("Lan", member.displayName)
        assertEquals("https://example.test/lan.png", member.avatarUrl)
        assertEquals(
            CachedGroupMemberEntity(
                groupId = "group-1",
                userId = "user-1",
                role = "OWNER",
                joinedAt = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli(),
                displayName = "Lan",
                avatarUrl = "https://example.test/lan.png"
            ),
            cache
        )
    }

    @Test
    fun `cached member profile maps to typed domain`() {
        val member = CollaborationRemoteMapper.fromCache(
            CachedGroupMemberEntity(
                groupId = "group-1",
                userId = "user-1",
                role = "MEMBER",
                joinedAt = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli(),
                displayName = "Lan",
                avatarUrl = "https://example.test/lan.png"
            )
        )

        assertEquals("Lan", member.displayName)
        assertEquals("https://example.test/lan.png", member.avatarUrl)
    }

    @Test
    fun `remote invite maps status without leaking raw storage string`() {
        val dto = CollaborationInviteRemoteDto(
            id = "invite-1",
            groupId = "group-1",
            inviterId = "owner-1",
            inviteeUserId = "user-2",
            status = "accepted",
            createdAt = "2026-09-10T10:00:00Z",
            respondedAt = "2026-09-10T11:00:00Z"
        )

        val invite = CollaborationRemoteMapper.toDomain(dto)

        assertEquals(GroupInviteId("invite-1"), invite.id)
        assertEquals(GroupInviteStatus.ACCEPTED, invite.status)
        assertEquals(Instant.parse("2026-09-10T11:00:00Z"), invite.respondedAt)
    }

    @Test
    fun `typed remote error envelope maps to domain result`() {
        val result = CollaborationRemoteMapper.toMutationResult(
            CollaborationMutationEnvelopeRemoteDto(
                status = "invite_already_pending",
                error = CollaborationErrorRemoteDto(
                    code = "INVITE_ALREADY_PENDING",
                    detail = "a pending invite exists"
                )
            )
        )

        assertTrue(result is CollaborationMutationResult.Failure)
        assertEquals(
            CollaborationError.InviteAlreadyPending,
            (result as CollaborationMutationResult.Failure).error
        )
    }

    @Test
    fun `create envelope maps typed created group id from task 2 data`() {
        val result = CollaborationRemoteMapper.toCreateGroupMutationResult(
            CollaborationMutationEnvelopeRemoteDto(
                status = "APPLIED",
                data = buildJsonObject { put("group_id", "created-group") }
            )
        )

        assertEquals(
            CollaborationMutationResult.Created(CollaborationGroupId("created-group")),
            result
        )
    }

    @Test
    fun `unknown member role is a mapping failure`() {
        assertThrows(CollaborationMappingException::class.java) {
            CollaborationRemoteMapper.toDomain(
                CollaborationMemberRemoteDto(
                    groupId = "group-1",
                    userId = "user-1",
                    role = "OWNER_OF_EVERYTHING",
                    joinedAt = "2026-09-10T10:00:00Z"
                )
            )
        }
    }

    @Test
    fun `unknown invite status is a mapping failure`() {
        assertThrows(CollaborationMappingException::class.java) {
            CollaborationRemoteMapper.toDomain(
                CollaborationInviteRemoteDto(
                    id = "invite-1",
                    groupId = "group-1",
                    inviterId = "owner-1",
                    inviteeUserId = "user-2",
                    status = "REVOKED_BY_MAGIC",
                    createdAt = "2026-09-10T10:00:00Z"
                )
            )
        }
    }

    @Test
    fun `invalid timestamp is a mapping failure`() {
        assertThrows(CollaborationMappingException::class.java) {
            CollaborationRemoteMapper.toDomain(
                CollaborationGroupRemoteDto(
                    id = "group-1",
                    name = "Group",
                    description = null,
                    createdBy = "owner-1",
                    createdAt = "not-a-timestamp",
                    updatedAt = "2026-09-10T10:00:00Z"
                )
            )
        }
    }

    @Test
    fun `unknown mutation status is a mapping failure`() {
        assertThrows(CollaborationMappingException::class.java) {
            CollaborationRemoteMapper.toMutationResult(
                CollaborationMutationEnvelopeRemoteDto(status = "MAYBE_APPLIED")
            )
        }
    }
}
