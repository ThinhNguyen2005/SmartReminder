package com.smartreminder.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.EditOwnGroupTaskContentCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeCollaborationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCollaborationRepository(
            groups = listOf(group("group-1", "Household")),
            invites = listOf(invite("invite-1", "group-1")),
            members = listOf(member("group-1", "owner-1", GroupRole.OWNER))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given cached groups, when viewModel starts, then exposes content and refreshes`() = runTest {
        val viewModel = GroupsViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GroupsLoadState.CONTENT, state.loadState)
        assertEquals(listOf("Household"), state.groups.map { it.name })
        assertEquals(listOf("invite-1"), state.pendingInvites.map { it.id.value })
        assertEquals(1, repository.refreshGroupsCalls)
    }

    @Test
    fun `given restored selected group, when it still exists, then detail is restored`() = runTest {
        val viewModel = GroupsViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(GroupsViewModel.SELECTED_GROUP_ID_KEY to "group-1")
            )
        )

        advanceUntilIdle()

        assertEquals(GroupsScreen.DETAIL, viewModel.uiState.value.screen)
        assertEquals(CollaborationGroupId("group-1"), viewModel.uiState.value.selectedGroupId)
        assertEquals("owner-1", viewModel.uiState.value.selectedGroup?.members?.single()?.userId?.value)
    }

    @Test
    fun `given restored selected group no longer exists, then view returns to list`() = runTest {
        val viewModel = GroupsViewModel(
            repository = FakeCollaborationRepository(),
            savedStateHandle = SavedStateHandle(
                mapOf(GroupsViewModel.SELECTED_GROUP_ID_KEY to "deleted-group")
            )
        )

        advanceUntilIdle()

        assertEquals(GroupsScreen.LIST, viewModel.uiState.value.screen)
        assertNull(viewModel.uiState.value.selectedGroupId)
        assertNull(viewModel.uiState.value.selectedGroup)
    }

    @Test
    fun `when back is pressed from detail, then selection is cleared and list is shown`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        assertEquals(GroupsScreen.DETAIL, viewModel.uiState.value.screen)

        viewModel.onAction(GroupsAction.Back)

        assertEquals(GroupsScreen.LIST, viewModel.uiState.value.screen)
        assertNull(viewModel.uiState.value.selectedGroupId)
    }

    @Test
    fun `when create dialog opens, then typed dialog state is exposed`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.OpenCreateGroupDialog)

        assertEquals(GroupsDialog.CreateGroup, viewModel.uiState.value.dialog)
    }

    @Test
    fun `when create mutation is pending, then mutation progress is exposed and cleared on completion`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.createGroupCompletion = completion
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.CreateGroup("New group", "Description"))
        advanceUntilIdle()

        assertEquals(
            GroupsMutation.CREATE_GROUP,
            viewModel.uiState.value.pendingMutation?.mutation
        )
        assertEquals(GroupsDialog.CreateGroup, viewModel.uiState.value.dialog)
        assertEquals("New group", repository.createGroupCommand?.name)

        completion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingMutation)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `when mutation is cancelled, then pending state is cleared without a user error`() = runTest {
        repository.cancelCreateGroup = true
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.CreateGroup("Cancelled"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingMutation)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `when invite accept and decline actions are sent, then repository receives both commands`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.AcceptInvite(GroupInviteId("invite-1")))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.DeclineInvite(GroupInviteId("invite-2")))
        advanceUntilIdle()

        assertEquals(
            listOf("accept:invite-1", "decline:invite-2"),
            repository.mutationCalls
        )
    }

    @Test
    fun `when membership actions are sent for selected group, then all typed commands delegate`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))

        viewModel.onAction(GroupsAction.UpdateGroup("Renamed", "Updated"))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.InviteMember("new@example.com"))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.ChangeMemberRole(UserId("member-1"), GroupRole.ADMIN))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.RemoveMember(UserId("member-1")))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.TransferOwnership(UserId("member-1")))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.LeaveGroup)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        viewModel.onAction(GroupsAction.DeleteGroup)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "update:Renamed",
                "invite:new@example.com",
                "role:member-1:ADMIN",
                "remove:member-1",
                "transfer:member-1",
                "leave:group-1",
                "delete:group-1"
            ),
            repository.mutationCalls
        )
    }

    @Test
    fun `when detail is loaded, then permissions are exposed for each member`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()

        val detail = viewModel.uiState.value.selectedGroup
        assertNotNull(detail)
        assertTrue(detail!!.permissionsByMemberId[UserId("owner-1")]!!.canDeleteGroup)
    }

    @Test
    fun `given offline refresh with cache, then cached offline state is exposed`() = runTest {
        repository.refreshGroupsResult = CollaborationMutationResult.NetworkRequired

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        assertEquals(GroupsLoadState.CACHED_OFFLINE, viewModel.uiState.value.loadState)
        assertTrue(viewModel.uiState.value.isCached)
        assertTrue(viewModel.uiState.value.isOffline)
    }

    @Test
    fun `given refresh failure without cache, then typed error state is exposed`() = runTest {
        repository.groups = emptyList()
        repository.refreshGroupsResult = CollaborationMutationResult.Failure(
            CollaborationError.NetworkUnavailable()
        )

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        assertEquals(GroupsLoadState.ERROR, viewModel.uiState.value.loadState)
        assertEquals(GroupsUiError.Offline, viewModel.uiState.value.error)
    }

    @Test
    fun `given missing collaboration configuration, then feature error is exposed`() = runTest {
        repository.groups = emptyList()
        repository.refreshGroupsResult = CollaborationMutationResult.Failure(
            CollaborationError.ConfigurationMissing
        )

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        assertEquals(GroupsLoadState.ERROR, viewModel.uiState.value.loadState)
        assertEquals(GroupsUiError.MissingConfiguration, viewModel.uiState.value.error)
    }

    @Test
    fun `when offline refresh finishes while cache exists, then offline refreshing state is observable`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupsCompletion = completion
        val viewModel = GroupsViewModel(repository)

        advanceUntilIdle()
        assertEquals(GroupsLoadState.OFFLINE_REFRESHING, viewModel.uiState.value.loadState)

        completion.complete(CollaborationMutationResult.NetworkRequired)
        advanceUntilIdle()

        assertEquals(GroupsLoadState.CACHED_OFFLINE, viewModel.uiState.value.loadState)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelFactoryTest {

    @Test
    fun `given collaboration repository, when factory creates supported model, then returns GroupsViewModel`() {
        val factory = GroupsViewModelFactory(FakeCollaborationRepository())

        assertEquals(GroupsViewModel::class.java, factory.create(GroupsViewModel::class.java)::class.java)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given unsupported model, when factory creates it, then throws`() {
        GroupsViewModelFactory(FakeCollaborationRepository()).create(UnsupportedViewModel::class.java)
    }
}

private class UnsupportedViewModel : androidx.lifecycle.ViewModel()

private class FakeCollaborationRepository(
    groups: List<CollaborationGroup> = emptyList(),
    invites: List<GroupInvite> = emptyList(),
    members: List<GroupMember> = emptyList()
) : CollaborationRepository {

    private val groupsFlow = MutableStateFlow(groups)
    private val invitesFlow = MutableStateFlow(invites)
    private val membersFlow = MutableStateFlow(members)

    var groups: List<CollaborationGroup>
        get() = groupsFlow.value
        set(value) {
            groupsFlow.value = value
        }

    var refreshGroupsResult: CollaborationMutationResult = CollaborationMutationResult.Applied
    var refreshGroupsCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var createGroupCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var cancelCreateGroup: Boolean = false
    var refreshGroupsCalls: Int = 0
        private set
    var createGroupCommand: CreateGroupCommand? = null
        private set
    val mutationCalls = mutableListOf<String>()

    override fun observeGroups(): Flow<List<CollaborationGroup>> = groupsFlow.asStateFlow()

    override fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?> =
        groupsFlow.map { groups -> groups.firstOrNull { it.id == groupId } }

    override fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>> =
        membersFlow.map { members -> members.filter { it.groupId == groupId } }

    override fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeInvites(): Flow<List<GroupInvite>> = invitesFlow.asStateFlow()

    override suspend fun refreshGroups(): CollaborationMutationResult {
        refreshGroupsCalls += 1
        return refreshGroupsCompletion?.await() ?: refreshGroupsResult
    }

    override suspend fun refreshGroup(groupId: CollaborationGroupId) =
        CollaborationMutationResult.Applied

    override suspend fun refreshInvites() = CollaborationMutationResult.Applied

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationResult {
        createGroupCommand = command
        mutationCalls += "create:${command.name}"
        if (cancelCreateGroup) throw CancellationException("cancelled")
        return createGroupCompletion?.await() ?: CollaborationMutationResult.Applied
    }

    override suspend fun updateGroup(command: UpdateGroupCommand): CollaborationMutationResult {
        mutationCalls += "update:${command.name}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationResult {
        mutationCalls += "invite:${command.email}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun acceptInvite(inviteId: GroupInviteId): CollaborationMutationResult {
        mutationCalls += "accept:${inviteId.value}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun declineInvite(inviteId: GroupInviteId): CollaborationMutationResult {
        mutationCalls += "decline:${inviteId.value}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationResult {
        mutationCalls += "role:${command.memberId.value}:${command.newRole.name}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationResult {
        mutationCalls += "remove:${command.memberId.value}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationResult {
        mutationCalls += "transfer:${command.newOwnerId.value}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun leaveGroup(groupId: CollaborationGroupId): CollaborationMutationResult {
        mutationCalls += "leave:${groupId.value}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun deleteGroup(groupId: CollaborationGroupId): CollaborationMutationResult {
        mutationCalls += "delete:${groupId.value}"
        return CollaborationMutationResult.Applied
    }

    override suspend fun createTask(command: CreateGroupTaskCommand) =
        CollaborationMutationResult.Applied

    override suspend fun startTask(taskId: GroupTaskId) = CollaborationMutationResult.Applied

    override suspend fun completeTask(taskId: GroupTaskId) = CollaborationMutationResult.Applied

    override suspend fun editOwnTaskContent(command: EditOwnGroupTaskContentCommand) =
        CollaborationMutationResult.Applied
}

private fun group(id: String, name: String) = CollaborationGroup(
    id = CollaborationGroupId(id),
    name = name,
    description = null,
    createdBy = UserId("owner-1"),
    createdAt = Instant.parse("2026-09-10T00:00:00Z"),
    updatedAt = Instant.parse("2026-09-10T00:00:00Z")
)

private fun member(groupId: String, userId: String, role: GroupRole) = GroupMember(
    groupId = CollaborationGroupId(groupId),
    userId = UserId(userId),
    role = role,
    joinedAt = Instant.parse("2026-09-10T00:00:00Z"),
    displayName = userId
)

private fun invite(id: String, groupId: String) = GroupInvite(
    id = GroupInviteId(id),
    groupId = CollaborationGroupId(groupId),
    inviterId = UserId("owner-1"),
    inviteeUserId = UserId("member-1"),
    status = GroupInviteStatus.PENDING,
    createdAt = Instant.parse("2026-09-10T00:00:00Z")
)
