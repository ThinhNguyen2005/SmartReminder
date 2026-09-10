package com.smartreminder.data.remote.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
