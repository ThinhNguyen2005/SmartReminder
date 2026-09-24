package com.smartreminder.data.repository.collaboration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.data.local.room.CueDatabase
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.repository.RoomCollaborationCacheDataSource
import com.smartreminder.data.remote.collaboration.CollaborationGroupRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationInviteRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMemberRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMutationEnvelopeRemoteDto
import com.smartreminder.data.remote.collaboration.UserProfileRemoteDto
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCollaborationRepositoryRefreshTest {

    private lateinit var database: CueDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun mutationRefreshKeepsMemberProfileAfterGroupsReplacement() = runTest {
        val cache = RoomCollaborationCacheDataSource(database)
        cache.replaceGroup(
            group = cachedGroup("Cached name"),
            members = listOf(
                CachedGroupMemberEntity(
                    groupId = "group-1",
                    userId = "member-1",
                    role = "MEMBER",
                    joinedAt = 1L,
                    displayName = "Cached member",
                    avatarUrl = "https://example.test/cached.png"
                )
            )
        )
        val remote = RefreshRemoteDataSource()
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.updateGroup(
            UpdateGroupCommand(CollaborationGroupId("group-1"), "Updated name")
        )

        assertEquals(CollaborationMutationResult.Applied, result)
        assertEquals(listOf("fetchGroups", "fetchGroup", "fetchMembers"), remote.readCalls)

        val cachedMember = database.collaborationCacheDao()
            .observeMembers("group-1")
            .first()
            .singleOrNull()
        assertNotNull(cachedMember)
        assertEquals("Remote member", cachedMember?.displayName)
        assertEquals("https://example.test/remote.png", cachedMember?.avatarUrl)

        val domainMember = repository.observeMembers(CollaborationGroupId("group-1"))
            .first()
            .single()
        assertEquals("Remote member", domainMember.displayName)
        assertEquals("https://example.test/remote.png", domainMember.avatarUrl)
    }

    @Test
    fun groupsRefreshSuccessThenDetailFailureKeepsExistingMemberProfile() = runTest {
        val cache = RoomCollaborationCacheDataSource(database)
        cache.replaceGroup(
            group = cachedGroup("Cached name"),
            members = listOf(cachedMember())
        )
        val remote = RefreshRemoteDataSource().apply {
            detailFailure = IllegalStateException("detail unavailable")
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.updateGroup(
            UpdateGroupCommand(CollaborationGroupId("group-1"), "Updated name")
        )

        assertEquals(CollaborationMutationResult.Applied, result)
        assertEquals(listOf("fetchGroups", "fetchGroup"), remote.readCalls)
        val retainedMember = database.collaborationCacheDao()
            .observeMembers("group-1")
            .first()
            .singleOrNull()
        assertNotNull(retainedMember)
        assertEquals("Cached member", retainedMember?.displayName)
        assertEquals("https://example.test/cached.png", retainedMember?.avatarUrl)
    }

    @Test
    fun groupsReconciliationRemovesStaleGroupsAndEmptyPayloadClearsAll() = runTest {
        val cache = RoomCollaborationCacheDataSource(database)
        cache.replaceGroup(
            group = cachedGroup("Group one", id = "group-1"),
            members = listOf(cachedMember(groupId = "group-1", userId = "member-1"))
        )
        cache.replaceGroup(
            group = cachedGroup("Group two", id = "group-2"),
            members = listOf(cachedMember(groupId = "group-2", userId = "member-2"))
        )

        cache.replaceGroups(listOf(cachedGroup("Updated group one", id = "group-1")))

        assertEquals(
            listOf("group-1"),
            database.collaborationCacheDao().observeGroups().first().map { it.id }
        )
        assertEquals(
            listOf("member-1"),
            database.collaborationCacheDao().observeMembers("group-1").first().map { it.userId }
        )
        assertTrue(database.collaborationCacheDao().observeMembers("group-2").first().isEmpty())

        cache.replaceGroups(emptyList())

        assertTrue(database.collaborationCacheDao().observeGroups().first().isEmpty())
        assertTrue(database.collaborationCacheDao().observeMembers("group-1").first().isEmpty())
    }

    private fun cachedGroup(name: String, id: String = "group-1") = CachedCollaborationGroupEntity(
        id = id,
        name = name,
        description = null,
        createdBy = "owner-1",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun cachedMember(
        groupId: String = "group-1",
        userId: String = "member-1"
    ) = CachedGroupMemberEntity(
        groupId = groupId,
        userId = userId,
        role = "MEMBER",
        joinedAt = 1L,
        displayName = "Cached member",
        avatarUrl = "https://example.test/cached.png"
    )
}

private class RefreshRemoteDataSource : GroupOnlyRemoteDataSource() {
    val readCalls = mutableListOf<String>()
    var detailFailure: Throwable? = null

    private val group = CollaborationGroupRemoteDto(
        id = "group-1",
        name = "Updated name",
        description = null,
        createdBy = "owner-1",
        createdAt = "2026-09-10T10:00:00Z",
        updatedAt = "2026-09-10T11:00:00Z"
    )

    override suspend fun fetchGroups(): List<CollaborationGroupRemoteDto> {
        readCalls += "fetchGroups"
        return listOf(group)
    }

    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? {
        readCalls += "fetchGroup"
        detailFailure?.let { throw it }
        return group
    }

    override suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto> {
        readCalls += "fetchMembers"
        return listOf(
            CollaborationMemberRemoteDto(
                groupId = groupId,
                userId = "member-1",
                role = "MEMBER",
                joinedAt = "2026-09-10T10:00:00Z",
                profile = UserProfileRemoteDto(
                    userId = "member-1",
                    displayName = "Remote member",
                    avatarUrl = "https://example.test/remote.png"
                )
            )
        )
    }

    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> = emptyList()

    override suspend fun createGroup(command: CreateGroupCommand) = applied()

    override suspend fun updateGroup(command: UpdateGroupCommand) = applied()

    override suspend fun inviteMember(command: InviteMemberCommand) = applied()

    override suspend fun acceptInvite(command: AcceptInviteCommand) = applied()

    override suspend fun declineInvite(command: DeclineInviteCommand) = applied()

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = applied()

    override suspend fun removeMember(command: RemoveMemberCommand) = applied()

    override suspend fun transferOwnership(command: TransferOwnershipCommand) = applied()

    override suspend fun leaveGroup(command: LeaveGroupCommand) = applied()

    override suspend fun deleteGroup(command: DeleteGroupCommand) = applied()

    private fun applied() = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
}
