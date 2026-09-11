package com.smartreminder.data.repository.collaboration

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.utils.EmptyContent
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.Attributes
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.remote.MissingSupabaseConfigurationException
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.put
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCollaborationRepositoryTest {

    @Test
    fun `missing Supabase configuration is a typed feature error`() = runTest {
        val repository = DefaultCollaborationRepository(
            cache = FakeCollaborationCache(),
            remote = FakeCollaborationRemoteDataSource().apply {
                groupsFailure = MissingSupabaseConfigurationException("missing")
            },
            network = { true }
        )

        assertEquals(
            CollaborationMutationResult.Failure(CollaborationError.ConfigurationMissing),
            repository.refreshGroups()
        )
    }

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
    fun `session boundary clear removes all collaboration cache rows`() = runTest {
        val groupId = CollaborationGroupId("group-a")
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup(groupId.value, "A private")),
            members = listOf(
                CachedGroupMemberEntity(
                    groupId = groupId.value,
                    userId = "a",
                    role = "OWNER",
                    joinedAt = 1L
                )
            ),
            invites = listOf(cachedInvite("invite-a", groupId.value))
        )
        val repository = DefaultCollaborationRepository(
            cache = cache,
            remote = FakeCollaborationRemoteDataSource(),
            network = { false },
            getCurrentUserId = { UserId("account-a") }
        )

        repository.clearSessionCache()

        assertTrue(repository.observeGroups().first().isEmpty())
        assertTrue(repository.observeMembers(groupId).first().isEmpty())
        assertTrue(repository.observeInvites().first().isEmpty())
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
    fun `when a pre-mutation detail response completes last, it cannot overwrite post-mutation cache`() = runTest {
        val groupId = CollaborationGroupId("group-1")
        val remote = DeferredRaceRemoteDataSource()
        val cache = FakeCollaborationCache()
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val staleRefresh = async { repository.refreshGroup(groupId) }
        remote.staleGroupFetchStarted.await()

        val mutation = async {
            repository.changeMemberRole(
                ChangeMemberRoleCommand(groupId, UserId("member-1"), GroupRole.ADMIN)
            )
        }
        assertEquals(CollaborationMutationResult.Applied, mutation.await())

        remote.staleGroupFetchCompletion.complete(Unit)
        assertEquals(CollaborationMutationResult.Applied, staleRefresh.await())

        assertEquals(
            GroupRole.ADMIN,
            repository.observeMembers(groupId).first().single().role
        )
    }

    @Test
    fun `when detail refresh returns no group, then group and related invites are evicted`() = runTest {
        val groupId = CollaborationGroupId("group-1")
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup(groupId.value, "Private")),
            invites = listOf(cachedInvite("invite-1", groupId.value))
        )
        val repository = DefaultCollaborationRepository(
            cache = cache,
            remote = FakeCollaborationRemoteDataSource().apply { groups = emptyList() },
            network = { true }
        )

        assertEquals(
            CollaborationMutationResult.Failure(CollaborationError.NotFound),
            repository.refreshGroup(groupId)
        )
        assertTrue(repository.observeGroups().first().isEmpty())
        assertTrue(repository.observeInvites().first().isEmpty())
    }

    @Test
    fun `when leave applies but follow-up refresh fails, then known group is evicted immediately`() = runTest {
        val groupId = CollaborationGroupId("group-1")
        val cache = FakeCollaborationCache(groups = listOf(cachedGroup(groupId.value, "Private")))
        val remote = FakeCollaborationRemoteDataSource().apply {
            groupsFailure = IllegalStateException("offline after leave")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        assertEquals(CollaborationMutationResult.Applied, repository.leaveGroup(groupId))
        assertTrue(repository.observeGroups().first().isEmpty())
    }

    @Test
    fun `G2 membership rejects queued envelopes as a non-success result`() = runTest {
        val remote = FakeCollaborationRemoteDataSource().apply {
            createGroupEnvelope = CollaborationMutationEnvelopeRemoteDto(status = "QUEUED")
        }
        val repository = DefaultCollaborationRepository(FakeCollaborationCache(), remote) { true }

        val result = repository.createGroup(CreateGroupCommand("Queued"))

        assertTrue(result is CollaborationMutationResult.Failure)
        assertTrue((result as CollaborationMutationResult.Failure).error is CollaborationError.InvalidState)
    }

    @Test
    fun `transport authorization loss evicts the affected group and related invites`() = runTest {
        listOf(
            401 to CollaborationError.NotAuthorized,
            403 to CollaborationError.NotAuthorized,
            404 to CollaborationError.NotFound
        ).forEach { (status, expectedError) ->
            val groupId = CollaborationGroupId("group-$status")
            val cache = FakeCollaborationCache(
                groups = listOf(cachedGroup(groupId.value, "Private")),
                invites = listOf(cachedInvite("invite-$status", groupId.value))
            )
            val remote = FakeCollaborationRemoteDataSource().apply {
                updateGroupFailure = transportFailure(status)
            }
            val repository = DefaultCollaborationRepository(cache, remote) { true }

            val result = repository.updateGroup(UpdateGroupCommand(groupId, "Renamed"))

            assertEquals(CollaborationMutationResult.Failure(expectedError), result)
            assertTrue(repository.observeGroups().first().isEmpty())
            assertTrue(repository.observeInvites().first().isEmpty())
        }
    }

    @Test
    fun `transport authorization loss evicts the responded invite`() = runTest {
        listOf(
            401 to CollaborationError.NotAuthorized,
            403 to CollaborationError.NotAuthorized,
            404 to CollaborationError.NotFound
        ).forEach { (status, expectedError) ->
            val inviteId = GroupInviteId("invite-$status")
            val cache = FakeCollaborationCache(
                invites = listOf(cachedInvite(inviteId.value, "group-1"))
            )
            val remote = FakeCollaborationRemoteDataSource().apply {
                acceptInviteFailure = transportFailure(status)
            }
            val repository = DefaultCollaborationRepository(cache, remote) { true }

            val result = repository.acceptInvite(inviteId)

            assertEquals(CollaborationMutationResult.Failure(expectedError), result)
            assertTrue(repository.observeInvites().first().isEmpty())
        }
    }

    @Test
    fun `transport network failure retains cached group and invite`() = runTest {
        val groupId = CollaborationGroupId("group-1")
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup(groupId.value, "Private")),
            invites = listOf(cachedInvite("invite-1", groupId.value))
        )
        val remote = FakeCollaborationRemoteDataSource().apply {
            updateGroupFailure = transportFailure(500)
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.updateGroup(UpdateGroupCommand(groupId, "Renamed"))

        assertTrue(result is CollaborationMutationResult.Failure)
        assertTrue((result as CollaborationMutationResult.Failure).error is CollaborationError.NetworkUnavailable)
        assertEquals(listOf(groupId), repository.observeGroups().first().map { it.id })
        assertEquals(listOf(GroupInviteId("invite-1")), repository.observeInvites().first().map { it.id })
    }

    @Test
    fun `concurrent invite refresh completing after accept cannot reinsert responded invite`() = runTest {
        val inviteId = GroupInviteId("invite-1")
        val cache = FakeCollaborationCache(
            invites = listOf(cachedInvite(inviteId.value, "group-1"))
        )
        val remote = DeferredInviteRaceRemoteDataSource(inviteId)
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val accept = async { repository.acceptInvite(inviteId) }
        remote.acceptStarted.await()
        val staleRefresh = async { repository.refreshInvites() }
        remote.staleRefreshStarted.await()

        remote.acceptCompletion.complete(Unit)
        assertEquals(CollaborationMutationResult.Applied, accept.await())

        remote.staleRefreshCompletion.complete(Unit)
        assertEquals(CollaborationMutationResult.Applied, staleRefresh.await())
        assertTrue(repository.observeInvites().first().isEmpty())
    }

    @Test
    fun `concurrent invite refresh completing after decline cannot reinsert denied invite`() = runTest {
        val inviteId = GroupInviteId("invite-1")
        val cache = FakeCollaborationCache(
            invites = listOf(cachedInvite(inviteId.value, "group-1"))
        )
        val remote = DeferredInviteRaceRemoteDataSource(inviteId)
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val decline = async { repository.declineInvite(inviteId) }
        remote.declineStarted.await()
        val staleRefresh = async { repository.refreshInvites() }
        remote.staleRefreshStarted.await()

        remote.declineCompletion.complete(Unit)
        assertEquals(CollaborationMutationResult.Applied, decline.await())

        remote.staleRefreshCompletion.complete(Unit)
        assertEquals(CollaborationMutationResult.Applied, staleRefresh.await())
        assertTrue(repository.observeInvites().first().isEmpty())
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
    fun `create mutation propagates typed created group id from remote envelope`() = runTest {
        val remote = FakeCollaborationRemoteDataSource().apply {
            createGroupEnvelope = CollaborationMutationEnvelopeRemoteDto(
                status = "APPLIED",
                data = kotlinx.serialization.json.buildJsonObject {
                    put("group_id", "created-group")
                }
            )
        }
        val repository = DefaultCollaborationRepository(
            cache = FakeCollaborationCache(),
            remote = remote,
            network = { true }
        )

        assertEquals(
            CollaborationMutationResult.Created(CollaborationGroupId("created-group")),
            repository.createGroup(CreateGroupCommand("Created"))
        )
    }

    @Test
    fun `created mutation survives post-create refresh failure and retains the previous cache`() = runTest {
        val cache = FakeCollaborationCache(
            groups = listOf(cachedGroup("cached", "Keep cached group"))
        )
        val remote = FakeCollaborationRemoteDataSource().apply {
            createGroupEnvelope = CollaborationMutationEnvelopeRemoteDto(
                status = "APPLIED",
                data = kotlinx.serialization.json.buildJsonObject {
                    put("group_id", "created-group")
                }
            )
            groupsFailure = IllegalStateException("refresh unavailable")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        assertEquals(
            CollaborationMutationResult.Created(CollaborationGroupId("created-group")),
            repository.createGroup(CreateGroupCommand("Created"))
        )
        assertEquals(1, remote.fetchGroupsCalls)
        assertEquals(0, cache.replaceGroupsCalls)
        assertEquals(
            listOf("Keep cached group"),
            repository.observeGroups().first().map { it.name }
        )
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

    private fun cachedInvite(id: String, groupId: String) = CachedGroupInviteEntity(
        id = id,
        groupId = groupId,
        inviterId = "owner-1",
        inviteeUserId = "member-1",
        status = "PENDING",
        createdAt = 1L,
        respondedAt = null
    )

    @OptIn(InternalAPI::class)
    private fun transportFailure(status: Int): RestException {
        val client = HttpClient(OkHttp)
        val requestData = HttpRequestData(
            url = Url("https://example.test"),
            method = HttpMethod.Post,
            headers = Headers.Empty,
            body = EmptyContent,
            executionContext = SupervisorJob(),
            attributes = Attributes()
        )
        val responseData = HttpResponseData(
            statusCode = HttpStatusCode.fromValue(status),
            requestTime = GMTDate(),
            headers = Headers.Empty,
            version = HttpProtocolVersion.HTTP_1_1,
            body = ByteReadChannel.Empty,
            callContext = SupervisorJob()
        )
        val response = HttpClientCall(client, requestData, responseData).response
        return RestException("transport", null, response).also { client.close() }
    }
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

    override suspend fun removeInvitesForGroup(groupId: String) {
        inviteFlow.value = inviteFlow.value.filterNot { it.groupId == groupId }
    }

    override suspend fun removeInvite(inviteId: String) {
        inviteFlow.value = inviteFlow.value.filterNot { it.id == inviteId }
    }

    override suspend fun clearAll() {
        groupFlow.value = emptyList()
        groupDetails.values.forEach { it.value = null }
        memberFlows.values.forEach { it.value = emptyList() }
        inviteFlow.value = emptyList()
    }
}

private class DeferredRaceRemoteDataSource : CollaborationRemoteDataSource {
    val staleGroupFetchStarted = CompletableDeferred<Unit>()
    val staleGroupFetchCompletion = CompletableDeferred<Unit>()
    private var fetchGroupCalls = 0

    private val group = CollaborationGroupRemoteDto(
        id = "group-1",
        name = "Group",
        description = null,
        createdBy = "owner-1",
        createdAt = "2026-09-10T10:00:00Z",
        updatedAt = "2026-09-10T10:00:00Z"
    )

    override suspend fun fetchGroups() = listOf(group)

    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto {
        fetchGroupCalls += 1
        if (fetchGroupCalls == 1) {
            staleGroupFetchStarted.complete(Unit)
            staleGroupFetchCompletion.await()
        }
        return group
    }

    override suspend fun fetchMembers(groupId: String) = listOf(
        CollaborationMemberRemoteDto(
            groupId = groupId,
            userId = "member-1",
            role = if (fetchGroupCalls == 1) "MEMBER" else "ADMIN",
            joinedAt = "2026-09-10T10:00:00Z"
        )
    )

    override suspend fun fetchInvites() = emptyList<CollaborationInviteRemoteDto>()

    override suspend fun createGroup(command: CreateGroupCommand) = applied()
    override suspend fun updateGroup(command: UpdateGroupCommand) = applied()
    override suspend fun inviteMember(command: InviteMemberCommand) = applied()
    override suspend fun acceptInvite(command: AcceptInviteCommand) = applied()
    override suspend fun declineInvite(command: com.smartreminder.domain.repository.DeclineInviteCommand) = applied()
    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = applied()
    override suspend fun removeMember(command: RemoveMemberCommand) = applied()
    override suspend fun transferOwnership(command: com.smartreminder.domain.repository.TransferOwnershipCommand) = applied()
    override suspend fun leaveGroup(command: LeaveGroupCommand) = applied()
    override suspend fun deleteGroup(command: DeleteGroupCommand) = applied()

    private fun applied() = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
}

private class FakeCollaborationRemoteDataSource : CollaborationRemoteDataSource {
    var groups: List<CollaborationGroupRemoteDto> = emptyList()
    var members: List<CollaborationMemberRemoteDto> = emptyList()
    var groupsFailure: Throwable? = null
    var groupFailure: Throwable? = null
    var invitesFailure: Throwable? = null
    var createGroupFailure: Throwable? = null
    var updateGroupFailure: Throwable? = null
    var acceptInviteFailure: Throwable? = null
    var createGroupEnvelope: CollaborationMutationEnvelopeRemoteDto = applied()
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
        return createGroupEnvelope
    }

    override suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "updateGroup"
        updateGroupFailure?.let { throw it }
        return applied()
    }

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "inviteMember"
        return applied()
    }

    override suspend fun acceptInvite(command: AcceptInviteCommand): CollaborationMutationEnvelopeRemoteDto {
        mutationCalls += "acceptInvite"
        acceptInviteFailure?.let { throw it }
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

private class DeferredInviteRaceRemoteDataSource(
    private val inviteId: GroupInviteId
) : CollaborationRemoteDataSource {
    val acceptStarted = CompletableDeferred<Unit>()
    val acceptCompletion = CompletableDeferred<Unit>()
    val declineStarted = CompletableDeferred<Unit>()
    val declineCompletion = CompletableDeferred<Unit>()
    val staleRefreshStarted = CompletableDeferred<Unit>()
    val staleRefreshCompletion = CompletableDeferred<Unit>()
    private var fetchInvitesCalls = 0

    override suspend fun fetchGroups() = emptyList<CollaborationGroupRemoteDto>()

    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? = null

    override suspend fun fetchMembers(groupId: String) = emptyList<CollaborationMemberRemoteDto>()

    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> {
        fetchInvitesCalls += 1
        if (fetchInvitesCalls == 1) {
            staleRefreshStarted.complete(Unit)
            staleRefreshCompletion.await()
            return listOf(
                CollaborationInviteRemoteDto(
                    id = inviteId.value,
                    groupId = "group-1",
                    inviterId = "owner-1",
                    inviteeUserId = "member-1",
                    status = "PENDING",
                    createdAt = "2026-09-10T10:00:00Z",
                    respondedAt = null
                )
            )
        }
        return emptyList()
    }

    override suspend fun createGroup(command: CreateGroupCommand) = applied()
    override suspend fun updateGroup(command: UpdateGroupCommand) = applied()
    override suspend fun inviteMember(command: InviteMemberCommand) = applied()

    override suspend fun acceptInvite(command: AcceptInviteCommand): CollaborationMutationEnvelopeRemoteDto {
        acceptStarted.complete(Unit)
        acceptCompletion.await()
        return applied()
    }

    override suspend fun declineInvite(command: com.smartreminder.domain.repository.DeclineInviteCommand): CollaborationMutationEnvelopeRemoteDto {
        declineStarted.complete(Unit)
        declineCompletion.await()
        return applied()
    }
    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = applied()
    override suspend fun removeMember(command: RemoveMemberCommand) = applied()
    override suspend fun transferOwnership(command: TransferOwnershipCommand) = applied()
    override suspend fun leaveGroup(command: LeaveGroupCommand) = applied()
    override suspend fun deleteGroup(command: DeleteGroupCommand) = applied()

    private fun applied() = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
}
