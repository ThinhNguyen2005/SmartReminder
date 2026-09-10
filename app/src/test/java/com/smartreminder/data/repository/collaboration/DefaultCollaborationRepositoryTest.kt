package com.smartreminder.data.repository.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.remote.collaboration.CollaborationErrorRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationGroupRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationInviteRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMemberRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMutationEnvelopeRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationRemoteDataSource
import com.smartreminder.data.remote.collaboration.UserProfileRemoteDto
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import com.smartreminder.domain.model.collaboration.GroupRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCollaborationRepositoryTest {

    @Test
    fun `observations are cache first`() = runTest {
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup("cached", "Cached group"))
        )
        val repository = DefaultCollaborationRepository(
            cache = cache,
            remote = FakeCollaborationRemoteDataSource(),
            network = { true }
        )

        assertEquals(
            listOf("Cached group"),
            repository.observeGroups().first().map { it.name }
        )
    }

    @Test
    fun `successful group refresh replaces cache atomically`() = runTest {
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup("old", "Old"))
        )
        val remote = FakeCollaborationRemoteDataSource().apply {
            groups = listOf(remoteGroup("new", "New"))
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        assertEquals(CollaborationMutationResult.Applied, repository.refreshGroups())
        assertEquals(listOf("New"), repository.observeGroups().first().map { it.name })
        assertEquals(1, cache.replaceGroupsCalls)
    }

    @Test
    fun `refresh failure retains valid cache`() = runTest {
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup("cached", "Keep me"))
        )
        val remote = FakeCollaborationRemoteDataSource().apply {
            groupsFailure = IllegalStateException("network down")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.refreshGroups()

        assertTrue(result is CollaborationMutationResult.Failure)
        assertEquals(
            listOf("Keep me"),
            repository.observeGroups().first().map { it.name }
        )
        assertEquals(0, cache.replaceGroupsCalls)
    }

    @Test
    fun `group detail refresh failure retains valid cache`() = runTest {
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup("group-1", "Keep me"))
        )
        val remote = FakeCollaborationRemoteDataSource().apply {
            groupFailure = IllegalStateException("detail unavailable")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.refreshGroup(CollaborationGroupId("group-1"))

        assertTrue(result is CollaborationMutationResult.Failure)
        assertEquals("Keep me", repository.observeGroup(CollaborationGroupId("group-1")).first()?.name)
        assertEquals(0, cache.replaceGroupCalls)
    }

    @Test
    fun `invite refresh failure retains valid cache`() = runTest {
        val cachedInvite = CachedGroupInviteEntity(
            id = "invite-1",
            groupId = "group-1",
            inviterId = "owner-1",
            inviteeUserId = "member-1",
            status = "PENDING",
            createdAt = 1L,
            respondedAt = null
        )
        val cache = FakeCollaborationCache(invites = listOf(cachedInvite))
        val remote = FakeCollaborationRemoteDataSource().apply {
            invitesFailure = IllegalStateException("invites unavailable")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.refreshInvites()

        assertTrue(result is CollaborationMutationResult.Failure)
        assertEquals(
            listOf("invite-1"),
            repository.observeInvites().first().map { it.id.value }
        )
        assertEquals(0, cache.replaceInvitesCalls)
    }

    @Test
    fun `offline mutation is network required and does not call remote`() = runTest {
        val remote = FakeCollaborationRemoteDataSource()
        val repository = DefaultCollaborationRepository(
            cache = FakeCollaborationCache(),
            remote = remote,
            network = { false }
        )

        val result = repository.createGroup(CreateGroupCommand("Offline group"))

        assertEquals(CollaborationMutationResult.NetworkRequired, result)
        assertEquals(0, remote.createGroupCalls)
    }

    @Test
    fun `applied invite refreshes invites and affected group`() = runTest {
        val remote = FakeCollaborationRemoteDataSource()
        val repository = DefaultCollaborationRepository(
            cache = FakeCollaborationCache(),
            remote = remote,
            network = { true }
        )

        val result = repository.inviteMember(
            InviteMemberCommand(CollaborationGroupId("group-1"), "invitee@example.com")
        )

        assertEquals(CollaborationMutationResult.Applied, result)
        assertEquals(1, remote.fetchInvitesCalls)
        assertEquals(listOf("group-1"), remote.fetchGroupIds)
    }

    @Test
    fun `transfer leave and delete refresh invites`() = runTest {
        val remote = FakeCollaborationRemoteDataSource().apply {
            groups = listOf(remoteGroup("group-1", "Group"))
        }
        val repository = DefaultCollaborationRepository(
            cache = FakeCollaborationCache(),
            remote = remote,
            network = { true }
        )

        assertEquals(
            CollaborationMutationResult.Applied,
            repository.transferOwnership(
                TransferOwnershipCommand(CollaborationGroupId("group-1"), UserId("member-1"))
            )
        )
        assertEquals(
            CollaborationMutationResult.Applied,
            repository.leaveGroup(CollaborationGroupId("group-1"))
        )
        assertEquals(
            CollaborationMutationResult.Applied,
            repository.deleteGroup(CollaborationGroupId("group-1"))
        )

        assertEquals(3, remote.fetchInvitesCalls)
    }

    @Test
    fun `all online membership mutations delegate and report applied`() = runTest {
        val groupId = CollaborationGroupId("group-1")
        val remote = FakeCollaborationRemoteDataSource().apply {
            groups = listOf(remoteGroup("group-1", "Group"))
        }
        val repository = DefaultCollaborationRepository(FakeCollaborationCache(), remote) { true }

        val results = listOf(
            repository.createGroup(CreateGroupCommand("New group")),
            repository.updateGroup(UpdateGroupCommand(groupId, "Renamed")),
            repository.inviteMember(InviteMemberCommand(groupId, "invitee@example.com")),
            repository.acceptInvite(GroupInviteId("invite-1")),
            repository.declineInvite(GroupInviteId("invite-2")),
            repository.changeMemberRole(
                ChangeMemberRoleCommand(groupId, UserId("member-1"), GroupRole.ADMIN)
            ),
            repository.removeMember(RemoveMemberCommand(groupId, UserId("member-1"))),
            repository.transferOwnership(
                TransferOwnershipCommand(groupId, UserId("member-1"))
            ),
            repository.leaveGroup(groupId),
            repository.deleteGroup(groupId)
        )

        assertTrue(results.all { it == CollaborationMutationResult.Applied })
        assertEquals(
            listOf(
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
            ),
            remote.mutationCalls
        )
        assertEquals(10, remote.fetchGroupsCalls)
        assertEquals(6, remote.fetchInvitesCalls)
        assertEquals(
            listOf("group-1", "group-1", "group-1", "group-1", "group-1"),
            remote.fetchGroupIds
        )
    }

    @Test
    fun `all membership mutations are network required offline`() = runTest {
        val groupId = CollaborationGroupId("group-1")
        val remote = FakeCollaborationRemoteDataSource()
        val repository = DefaultCollaborationRepository(FakeCollaborationCache(), remote) { false }

        val results = listOf(
            repository.createGroup(CreateGroupCommand("New group")),
            repository.updateGroup(UpdateGroupCommand(groupId, "Renamed")),
            repository.inviteMember(InviteMemberCommand(groupId, "invitee@example.com")),
            repository.acceptInvite(GroupInviteId("invite-1")),
            repository.declineInvite(GroupInviteId("invite-2")),
            repository.changeMemberRole(
                ChangeMemberRoleCommand(groupId, UserId("member-1"), GroupRole.ADMIN)
            ),
            repository.removeMember(RemoveMemberCommand(groupId, UserId("member-1"))),
            repository.transferOwnership(
                TransferOwnershipCommand(groupId, UserId("member-1"))
            ),
            repository.leaveGroup(groupId),
            repository.deleteGroup(groupId)
        )

        assertTrue(results.all { it == CollaborationMutationResult.NetworkRequired })
        assertEquals(emptyList<String>(), remote.mutationCalls)
        assertEquals(0, remote.fetchGroupsCalls)
        assertEquals(0, remote.fetchInvitesCalls)
    }

    @Test
    fun `cancellation from a remote mutation is never converted to a result`() = runTest {
        val remote = FakeCollaborationRemoteDataSource().apply {
            createGroupFailure = CancellationException("cancelled")
        }
        val repository = DefaultCollaborationRepository(FakeCollaborationCache(), remote) { true }

        var cancelled = false
        try {
            repository.createGroup(CreateGroupCommand("Cancelled"))
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    @Test
    fun `fatal errors from a remote mutation are not swallowed`() = runTest {
        val remote = FakeCollaborationRemoteDataSource().apply {
            createGroupFailure = AssertionError("fatal")
        }
        val repository = DefaultCollaborationRepository(FakeCollaborationCache(), remote) { true }

        var thrown = false
        try {
            repository.createGroup(CreateGroupCommand("Fatal"))
        } catch (_: AssertionError) {
            thrown = true
        }

        assertTrue(thrown)
    }

    @Test
    fun `mapping failure retains cached members`() = runTest {
        val cachedMember = CachedGroupMemberEntity(
            groupId = "group-1",
            userId = "member-1",
            role = "MEMBER",
            joinedAt = 1L,
            displayName = "Cached member",
            avatarUrl = "https://example.test/cached.png"
        )
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup("group-1", "Cached group")),
            members = listOf(cachedMember)
        )
        val remote = FakeCollaborationRemoteDataSource().apply {
            groups = listOf(remoteGroup("group-1", "Remote group"))
            members = listOf(
                CollaborationMemberRemoteDto(
                    groupId = "group-1",
                    userId = "member-1",
                    role = "UNKNOWN_ROLE",
                    joinedAt = "2026-09-10T10:00:00Z"
                )
            )
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.refreshGroup(CollaborationGroupId("group-1"))

        assertTrue(result is CollaborationMutationResult.Failure)
        val error = (result as CollaborationMutationResult.Failure).error
        assertTrue(error is CollaborationError.MappingFailure)
        assertEquals(
            "Unknown collaboration member role: UNKNOWN_ROLE",
            (error as CollaborationError.MappingFailure).message
        )
        assertEquals(
            listOf("Cached member"),
            repository.observeMembers(CollaborationGroupId("group-1")).first()
                .map { it.displayName }
        )
    }

    private fun cachedGroup(id: String, name: String) = CachedCollaborationGroupEntity(
        id = id,
        name = name,
        description = null,
        createdBy = "owner-1",
        createdAt = 1L,
        updatedAt = 2L
    )

    private fun remoteGroup(id: String, name: String) = CollaborationGroupRemoteDto(
        id = id,
        name = name,
        description = null,
        createdBy = "owner-1",
        createdAt = "2026-09-10T10:00:00Z",
        updatedAt = "2026-09-10T10:00:00Z"
    )
}

private class FakeCollaborationCache(
    groups: List<CachedCollaborationGroupEntity> = emptyList(),
    members: List<CachedGroupMemberEntity> = emptyList(),
    invites: List<CachedGroupInviteEntity> = emptyList()
) : CollaborationCacheDataSource {
    private val groupFlow = MutableStateFlow(groups)
    private val groupDetails = mutableMapOf<String, MutableStateFlow<CachedCollaborationGroupEntity?>>()
    private val memberFlows = mutableMapOf<String, MutableStateFlow<List<CachedGroupMemberEntity>>>()
    private val inviteFlow = MutableStateFlow(invites)
    var replaceGroupsCalls: Int = 0
        private set
    var replaceGroupCalls: Int = 0
        private set
    var replaceInvitesCalls: Int = 0
        private set

    init {
        members.groupBy(CachedGroupMemberEntity::groupId).forEach { (groupId, groupMembers) ->
            memberFlows[groupId] = MutableStateFlow(groupMembers)
        }
    }

    override fun observeGroups(): Flow<List<CachedCollaborationGroupEntity>> = groupFlow

    override fun observeGroup(groupId: String): Flow<CachedCollaborationGroupEntity?> =
        groupDetails.getOrPut(groupId) {
            MutableStateFlow(groupFlow.value.firstOrNull { it.id == groupId })
        }

    override fun observeMembers(groupId: String): Flow<List<CachedGroupMemberEntity>> =
        memberFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }

    override fun observeTasks(groupId: String): Flow<List<CachedGroupTaskEntity>> =
        MutableStateFlow(emptyList())

    override fun observeInvites(): Flow<List<CachedGroupInviteEntity>> =
        inviteFlow

    override suspend fun replaceGroups(groups: List<CachedCollaborationGroupEntity>) {
        replaceGroupsCalls += 1
        groupFlow.value = groups
        groups.forEach { groupDetails.getOrPut(it.id) { MutableStateFlow(null) }.value = it }
    }

    override suspend fun replaceGroup(
        group: CachedCollaborationGroupEntity,
        members: List<CachedGroupMemberEntity>
    ) {
        replaceGroupCalls += 1
        val current = groupFlow.value.filterNot { it.id == group.id }
        groupFlow.value = current + group
        groupDetails.getOrPut(group.id) { MutableStateFlow(null) }.value = group
        memberFlows.getOrPut(group.id) { MutableStateFlow(emptyList()) }.value = members
    }

    override suspend fun replaceInvites(invites: List<CachedGroupInviteEntity>) {
        replaceInvitesCalls += 1
        inviteFlow.value = invites
    }

    override suspend fun removeGroup(groupId: String) {
        groupFlow.value = groupFlow.value.filterNot { it.id == groupId }
        groupDetails[groupId]?.value = null
    }
}

private class FakeCollaborationRemoteDataSource : CollaborationRemoteDataSource {
    var groups: List<CollaborationGroupRemoteDto> = emptyList()
    var members: List<CollaborationMemberRemoteDto> = emptyList()
    var groupsFailure: Throwable? = null
    var groupFailure: Throwable? = null
    var invitesFailure: Throwable? = null
    var createGroupFailure: Throwable? = null
    var createGroupCalls: Int = 0
    var fetchGroupsCalls: Int = 0
    var fetchInvitesCalls: Int = 0
    val fetchGroupIds = mutableListOf<String>()
    val mutationCalls = mutableListOf<String>()

    override suspend fun fetchGroups(): List<CollaborationGroupRemoteDto> {
        fetchGroupsCalls += 1
        groupsFailure?.let { throw it }
        return groups
    }

    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? {
        fetchGroupIds += groupId
        groupFailure?.let { throw it }
        return groups.firstOrNull { it.id == groupId }
    }

    override suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto> = members

    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> {
        fetchInvitesCalls += 1
        invitesFailure?.let { throw it }
        return emptyList()
    }

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationEnvelopeRemoteDto {
        createGroupCalls += 1
        mutationCalls += "createGroup"
        createGroupFailure?.let { throw it }
        return applied()
    }

    override suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "updateGroup"
        return applied()
    }

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "inviteMember"
        return applied()
    }

    override suspend fun acceptInvite(command: AcceptInviteCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "acceptInvite"
        return applied()
    }

    override suspend fun declineInvite(command: com.smartreminder.domain.repository.DeclineInviteCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "declineInvite"
        return applied()
    }

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "changeMemberRole"
        return applied()
    }

    override suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "removeMember"
        return applied()
    }

    override suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "transferOwnership"
        return applied()
    }

    override suspend fun leaveGroup(command: LeaveGroupCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "leaveGroup"
        return applied()
    }

    override suspend fun deleteGroup(command: DeleteGroupCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "deleteGroup"
        return applied()
    }

    private fun applied() = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
}
