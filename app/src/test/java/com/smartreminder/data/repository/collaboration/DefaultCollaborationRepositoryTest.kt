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
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import com.smartreminder.domain.model.collaboration.GroupRole
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
    groups: List<CachedCollaborationGroupEntity> = emptyList()
) : CollaborationCacheDataSource {
    private val groupFlow = MutableStateFlow(groups)
    private val groupDetails = mutableMapOf<String, MutableStateFlow<CachedCollaborationGroupEntity?>>()
    var replaceGroupsCalls: Int = 0
        private set

    override fun observeGroups(): Flow<List<CachedCollaborationGroupEntity>> = groupFlow

    override fun observeGroup(groupId: String): Flow<CachedCollaborationGroupEntity?> =
        groupDetails.getOrPut(groupId) {
            MutableStateFlow(groupFlow.value.firstOrNull { it.id == groupId })
        }

    override fun observeMembers(groupId: String): Flow<List<CachedGroupMemberEntity>> =
        MutableStateFlow(emptyList())

    override fun observeTasks(groupId: String): Flow<List<CachedGroupTaskEntity>> =
        MutableStateFlow(emptyList())

    override fun observeInvites(): Flow<List<CachedGroupInviteEntity>> =
        MutableStateFlow(emptyList())

    override suspend fun replaceGroups(groups: List<CachedCollaborationGroupEntity>) {
        replaceGroupsCalls += 1
        groupFlow.value = groups
        groups.forEach { groupDetails.getOrPut(it.id) { MutableStateFlow(null) }.value = it }
    }

    override suspend fun replaceGroup(
        group: CachedCollaborationGroupEntity,
        members: List<CachedGroupMemberEntity>
    ) {
        val current = groupFlow.value.filterNot { it.id == group.id }
        groupFlow.value = current + group
        groupDetails.getOrPut(group.id) { MutableStateFlow(null) }.value = group
    }

    override suspend fun replaceInvites(invites: List<CachedGroupInviteEntity>) = Unit

    override suspend fun removeGroup(groupId: String) {
        groupFlow.value = groupFlow.value.filterNot { it.id == groupId }
        groupDetails[groupId]?.value = null
    }
}

private class FakeCollaborationRemoteDataSource : CollaborationRemoteDataSource {
    var groups: List<CollaborationGroupRemoteDto> = emptyList()
    var groupsFailure: Throwable? = null
    var createGroupCalls: Int = 0
    var fetchInvitesCalls: Int = 0
    val fetchGroupIds = mutableListOf<String>()

    override suspend fun fetchGroups(): List<CollaborationGroupRemoteDto> {
        groupsFailure?.let { throw it }
        return groups
    }

    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? {
        fetchGroupIds += groupId
        return groups.firstOrNull { it.id == groupId }
    }

    override suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto> = emptyList()

    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> {
        fetchInvitesCalls += 1
        return emptyList()
    }

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationEnvelopeRemoteDto {
        createGroupCalls += 1
        return applied()
    }

    override suspend fun updateGroup(command: UpdateGroupCommand) = applied()

    override suspend fun inviteMember(command: InviteMemberCommand) = applied()

    override suspend fun acceptInvite(command: AcceptInviteCommand) = applied()

    override suspend fun declineInvite(command: com.smartreminder.domain.repository.DeclineInviteCommand) = applied()

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = applied()

    override suspend fun removeMember(command: RemoveMemberCommand) = applied()

    override suspend fun transferOwnership(command: TransferOwnershipCommand) = applied()

    override suspend fun leaveGroup(command: LeaveGroupCommand) = applied()

    override suspend fun deleteGroup(command: DeleteGroupCommand) = applied()

    private fun applied() = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
}
