package com.smartreminder.ui.groups

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
            members = listOf(
                member("group-1", "owner-1", GroupRole.OWNER),
                member("group-1", "member-1", GroupRole.MEMBER)
            )
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
        assertTrue(
            viewModel.uiState.value.selectedGroup?.members
                ?.any { it.userId == UserId("owner-1") } == true
        )
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
    fun `when create returns typed group id, then created group is selected and detail navigation is emitted`() = runTest {
        val createdId = CollaborationGroupId("created-group")
        repository.groups = listOf(group("group-1", "Household"), group("created-group", "Created"))
        repository.createGroupResult = CollaborationMutationResult.Created(createdId)
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.CreateGroup("Created"))
        advanceUntilIdle()

        assertEquals(createdId, viewModel.uiState.value.selectedGroupId)
        assertEquals(GroupsScreen.DETAIL, viewModel.uiState.value.screen)
        assertEquals(
            GroupsEffect.NavigateToDetail(createdId),
            viewModel.effects.filter { it is GroupsEffect.NavigateToDetail }.first()
        )
    }

    @Test
    fun `when create succeeds but cache refresh misses the new group, then intent stays in detail error until retry`() = runTest {
        val createdId = CollaborationGroupId("created-after-refresh-failure")
        repository.groups = listOf(group("group-1", "Household"))
        repository.createGroupResult = CollaborationMutationResult.Created(createdId)
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.CreateGroup("Created"))
        advanceUntilIdle()

        assertEquals(createdId, viewModel.uiState.value.selectedGroupId)
        assertEquals(GroupsScreen.DETAIL, viewModel.uiState.value.screen)
        assertEquals(GroupsLoadState.ERROR, viewModel.uiState.value.loadState)
        assertNull(viewModel.uiState.value.selectedGroup)
        assertEquals(GroupsUiError.NotFound, viewModel.uiState.value.error)
        assertEquals(
            GroupsEffect.NavigateToDetail(createdId),
            viewModel.effects.filter { it == GroupsEffect.NavigateToDetail(createdId) }.first()
        )

        repository.groups = listOf(group("group-1", "Household"), group(createdId.value, "Created"))
        repository.refreshGroupsResult = CollaborationMutationResult.Applied
        viewModel.onAction(GroupsAction.Refresh)
        advanceUntilIdle()

        assertEquals(createdId, viewModel.uiState.value.selectedGroupId)
        assertEquals("Created", viewModel.uiState.value.selectedGroup?.group?.name)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `when detail refresh for group A finishes after group B opens, then stale failure is ignored`() = runTest {
        val groupA = CollaborationGroupId("group-a")
        val groupB = CollaborationGroupId("group-b")
        repository.groups = listOf(group("group-a", "A"), group("group-b", "B"))
        val completionA = CompletableDeferred<CollaborationMutationResult>()
        val completionB = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[groupA] = completionA
        repository.refreshGroupCompletions[groupB] = completionB
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        val effects = mutableListOf<GroupsEffect>()
        val effectsJob = launch {
            viewModel.effects.collect { effects += it }
        }

        viewModel.onAction(GroupsAction.OpenGroup(groupA))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(groupB))
        advanceUntilIdle()

        completionA.complete(
            CollaborationMutationResult.Conflict(CollaborationError.Conflict("stale A"))
        )
        advanceUntilIdle()

        assertEquals(groupB, viewModel.uiState.value.selectedGroupId)
        assertNull(viewModel.uiState.value.error)

        completionB.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()
        assertEquals(groupB, viewModel.uiState.value.selectedGroupId)
        effectsJob.cancel()
        assertNoMutationOutcomeEffects(effects)
    }

    @Test
    fun `when refresh is already running, then repeated refresh does not issue a duplicate`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupsCompletion = completion
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.Refresh)
        advanceUntilIdle()

        assertEquals(1, repository.refreshGroupsCalls)
        completion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()
    }

    @Test
    fun `when mutation is pending, then Back and OpenGroup are ignored`() = runTest {
        val otherGroup = group("group-2", "Other")
        repository.groups = listOf(group("group-1", "Household"), otherGroup)
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.createGroupCompletion = completion
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.CreateGroup("Pending"))
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.Back)
        viewModel.onAction(GroupsAction.OpenGroup(otherGroup.id))

        assertEquals(CollaborationGroupId("group-1"), viewModel.uiState.value.selectedGroupId)
        assertNotNull(viewModel.uiState.value.pendingMutation)

        completion.complete(CollaborationMutationResult.Conflict(CollaborationError.Conflict("conflict")))
        advanceUntilIdle()
        assertEquals(CollaborationGroupId("group-1"), viewModel.uiState.value.selectedGroupId)
        assertEquals(GroupsUiError.Conflict("conflict"), viewModel.uiState.value.error)
    }

    @Test
    fun `when mutation is already running, then duplicate mutation action is ignored`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.createGroupCompletion = completion
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.CreateGroup("First"))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.CreateGroup("Second"))

        assertEquals(listOf("create:First"), repository.mutationCalls)
        completion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()
    }

    @Test
    fun `when mutation returns typed conflict or not authorized, then matching ui errors are exposed`() = runTest {
        repository.createGroupResult = CollaborationMutationResult.NotAuthorized(
            CollaborationError.NotAuthorized
        )
        val notAuthorizedViewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        notAuthorizedViewModel.onAction(GroupsAction.CreateGroup("Denied"))
        advanceUntilIdle()
        assertEquals(GroupsUiError.NotAuthorized, notAuthorizedViewModel.uiState.value.error)

        val conflictRepository = FakeCollaborationRepository(
            groups = listOf(group("group-1", "Household")),
            members = listOf(member("group-1", "owner-1", GroupRole.OWNER))
        ).apply {
            createGroupResult = CollaborationMutationResult.Conflict(
                CollaborationError.Conflict("changed remotely")
            )
        }
        val conflictViewModel = GroupsViewModel(conflictRepository)
        advanceUntilIdle()
        conflictViewModel.onAction(GroupsAction.CreateGroup("Conflict"))
        advanceUntilIdle()
        assertEquals(GroupsUiError.Conflict("changed remotely"), conflictViewModel.uiState.value.error)
    }

    @Test
    fun `when actor is admin, then target permissions and role gated dialogs reflect admin rules`() = runTest {
        repository.currentUserId = UserId("admin-1")
        repository.members = listOf(
            member("group-1", "owner-1", GroupRole.OWNER),
            member("group-1", "admin-1", GroupRole.ADMIN),
            member("group-1", "member-1", GroupRole.MEMBER)
        )
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()

        val detail = viewModel.uiState.value.selectedGroup!!
        assertEquals(UserId("admin-1"), detail.currentUserId)
        assertEquals(GroupRole.ADMIN, detail.currentUserRole)
        assertTrue(detail.actorPermissions!!.canEditGroup)
        assertFalse(detail.actorPermissions.canChangeRoles)
        assertTrue(
            "permissionsByMemberId remains target-role based",
            detail.permissionsByMemberId[UserId("owner-1")]!!.canDeleteGroup
        )
        assertFalse(detail.memberActionsByMemberId[UserId("owner-1")]!!.canRemove)
        assertTrue(detail.memberActionsByMemberId[UserId("member-1")]!!.canRemove)

        viewModel.onAction(GroupsAction.OpenUpdateGroupDialog)
        assertEquals(GroupsDialog.UpdateGroup(CollaborationGroupId("group-1")), viewModel.uiState.value.dialog)
        viewModel.onAction(GroupsAction.DismissDialog)
        viewModel.onAction(GroupsAction.OpenChangeMemberRoleDialog(UserId("member-1")))
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(GroupsUiError.NotAuthorized, viewModel.uiState.value.error)
    }

    @Test
    fun `when actor is member, then every membership dialog is denied`() = runTest {
        repository.currentUserId = UserId("member-1")
        repository.members = listOf(
            member("group-1", "owner-1", GroupRole.OWNER),
            member("group-1", "member-1", GroupRole.MEMBER)
        )
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()

        listOf<GroupsAction>(
            GroupsAction.OpenUpdateGroupDialog,
            GroupsAction.OpenInviteMemberDialog,
            GroupsAction.OpenChangeMemberRoleDialog(UserId("owner-1")),
            GroupsAction.OpenRemoveMemberDialog(UserId("owner-1")),
            GroupsAction.OpenTransferOwnershipDialog(UserId("owner-1")),
            GroupsAction.OpenDeleteGroupDialog
        ).forEach { action ->
            viewModel.onAction(action)
            assertNull(viewModel.uiState.value.dialog)
            assertEquals(GroupsUiError.NotAuthorized, viewModel.uiState.value.error)
            viewModel.onAction(GroupsAction.DismissError)
        }

        viewModel.onAction(GroupsAction.OpenLeaveGroupDialog)
        assertEquals(GroupsDialog.LeaveGroup, viewModel.uiState.value.dialog)
    }

    @Test
    fun `when refresh throws, then feature exposes typed unknown error instead of crashing`() = runTest {
        repository.groups = emptyList()
        repository.refreshGroupsFailure = IllegalStateException("refresh failed")
        val viewModel = GroupsViewModel(repository)

        advanceUntilIdle()

        assertEquals(GroupsLoadState.ERROR, viewModel.uiState.value.loadState)
        assertTrue(viewModel.uiState.value.error is GroupsUiError.Unknown)
    }

    @Test
    fun `when mutation returns network required, then offline error is exposed`() = runTest {
        repository.createGroupResult = CollaborationMutationResult.NetworkRequired
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.CreateGroup("Offline"))
        advanceUntilIdle()

        assertEquals(GroupsUiError.Offline, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isOffline)
    }

    @Test
    fun `when mutation is cancelled, then pending state is cleared without a user error`() = runTest {
        repository.cancelCreateGroup = true
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        val effects = mutableListOf<GroupsEffect>()
        val effectsJob = launch {
            viewModel.effects.collect { effects += it }
        }

        viewModel.onAction(GroupsAction.CreateGroup("Cancelled"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingMutation)
        assertNull(viewModel.uiState.value.error)
        effectsJob.cancel()
        assertNoMutationOutcomeEffects(effects)
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
        advanceUntilIdle()

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

        assertEquals(
            listOf(
                "update:Renamed",
                "invite:new@example.com",
                "role:member-1:ADMIN",
                "remove:member-1",
                "transfer:member-1"
            ),
            repository.mutationCalls
        )
    }

    @Test
    fun `when a non-owner leaves selected group, then leave mutation delegates`() = runTest {
        repository.currentUserId = UserId("member-1")
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.LeaveGroup)
        advanceUntilIdle()

        assertEquals(listOf("leave:group-1"), repository.mutationCalls)
    }

    @Test
    fun `when owner deletes selected group, then delete mutation delegates`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.DeleteGroup)
        advanceUntilIdle()

        assertEquals(listOf("delete:group-1"), repository.mutationCalls)
    }

    @Test
    fun `when leave succeeds, then mutation completion and list navigation effects are emitted`() = runTest {
        repository.currentUserId = UserId("member-1")
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()
        val effects = mutableListOf<GroupsEffect>()
        val effectsJob = async {
            viewModel.effects.take(2).toList(effects)
        }

        viewModel.onAction(GroupsAction.LeaveGroup)
        advanceUntilIdle()
        effectsJob.await()

        assertEquals(
            listOf(
                GroupsEffect.MutationCompleted(GroupsMutation.LEAVE_GROUP),
                GroupsEffect.NavigateToList
            ),
            effects
        )
        assertEquals(GroupsScreen.LIST, viewModel.uiState.value.screen)
    }

    @Test
    fun `when mutation returns invalid state, then typed ui error is exposed without completion effect`() = runTest {
        repository.createGroupResult = CollaborationMutationResult.InvalidState(
            CollaborationError.InvalidState("stale version")
        )
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        val effects = mutableListOf<GroupsEffect>()
        val effectsJob = launch {
            viewModel.effects.collect { effects += it }
        }

        viewModel.onAction(GroupsAction.CreateGroup("Invalid"))
        advanceUntilIdle()

        assertEquals(GroupsUiError.InvalidState("stale version"), viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.pendingMutation)
        effectsJob.cancel()
        assertNoMutationOutcomeEffects(effects)
    }

    @Test
    fun `when mutation returns generic failure, then typed unknown state has no completion or navigation effect`() = runTest {
        val cause = IllegalStateException("server failed")
        repository.createGroupResult = CollaborationMutationResult.Failure(
            CollaborationError.Unknown(cause)
        )
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        val effects = mutableListOf<GroupsEffect>()
        val effectsJob = launch {
            viewModel.effects.collect { effects += it }
        }

        viewModel.onAction(GroupsAction.CreateGroup("Failed"))
        advanceUntilIdle()

        assertEquals(GroupsUiError.Unknown(cause), viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.pendingMutation)
        effectsJob.cancel()
        assertNoMutationOutcomeEffects(effects)
    }

    @Test
    fun `when ViewModelStore clears during refresh, then suspended refresh is cancelled and late result is ignored`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupsCompletion = completion
        val owner = TestViewModelStoreOwner()
        val viewModel = ViewModelProvider(
            owner,
            GroupsViewModelFactory(repository, SavedStateHandle())
        )[GroupsViewModel::class.java]
        advanceUntilIdle()

        owner.viewModelStore.clear()
        advanceUntilIdle()
        completion.complete(CollaborationMutationResult.Failure(CollaborationError.Conflict("late")))
        advanceUntilIdle()

        assertTrue(repository.refreshGroupsCancelled)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `when ViewModelStore clears during detail refresh, then suspended detail is cancelled`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion
        val owner = TestViewModelStoreOwner()
        val viewModel = ViewModelProvider(
            owner,
            GroupsViewModelFactory(repository, SavedStateHandle())
        )[GroupsViewModel::class.java]
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()

        owner.viewModelStore.clear()
        advanceUntilIdle()
        completion.complete(CollaborationMutationResult.Conflict(CollaborationError.Conflict("late")))
        advanceUntilIdle()

        assertTrue(repository.refreshGroupCancelled.contains(CollaborationGroupId("group-1")))
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `when ViewModelStore clears during mutation, then suspended mutation is cancelled without a late effect`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.createGroupCompletion = completion
        val owner = TestViewModelStoreOwner()
        val viewModel = ViewModelProvider(
            owner,
            GroupsViewModelFactory(repository, SavedStateHandle())
        )[GroupsViewModel::class.java]
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.CreateGroup("Pending"))
        advanceUntilIdle()
        val effects = mutableListOf<GroupsEffect>()
        val effectsJob = launch {
            viewModel.effects.collect { effects += it }
        }

        owner.viewModelStore.clear()
        advanceUntilIdle()
        completion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()

        assertTrue(repository.createGroupCancelled)
        assertNull(viewModel.uiState.value.pendingMutation)
        assertNull(viewModel.uiState.value.error)
        effectsJob.cancel()
        assertNoMutationOutcomeEffects(effects)
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

    @Test
    fun `when factory receives CreationExtras, then injected SavedStateHandle selection is restored`() {
        val savedStateHandle = SavedStateHandle(
            mapOf(GroupsViewModel.SELECTED_GROUP_ID_KEY to "group-1")
        )
        val viewModel = GroupsViewModelFactory(
            FakeCollaborationRepository(groups = listOf(group("group-1", "Household"))),
            savedStateHandle
        ).create(GroupsViewModel::class.java, CreationExtras.Empty)

        assertEquals(CollaborationGroupId("group-1"), viewModel.uiState.value.selectedGroupId)
    }

    @Test
    fun `when factory receives real saved-state extras, then createSavedStateHandle branch restores selection`() {
        val owner = TestSavedStateOwner()
        val extras = MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = owner
            this[VIEW_MODEL_STORE_OWNER_KEY] = owner
            this[ViewModelProvider.VIEW_MODEL_KEY] = "groups"
        }
        owner.seedSavedStateHandle(
            key = "groups",
            handle = SavedStateHandle(
                mapOf(GroupsViewModel.SELECTED_GROUP_ID_KEY to "group-1")
            )
        )

        val viewModel = GroupsViewModelFactory(
            FakeCollaborationRepository(groups = listOf(group("group-1", "Household")))
        ).create(GroupsViewModel::class.java, extras)

        assertEquals(CollaborationGroupId("group-1"), viewModel.uiState.value.selectedGroupId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given unsupported model, when factory creates it, then throws`() {
        GroupsViewModelFactory(FakeCollaborationRepository()).create(UnsupportedViewModel::class.java)
    }
}

private class UnsupportedViewModel : androidx.lifecycle.ViewModel()

private fun assertNoMutationOutcomeEffects(effects: List<GroupsEffect>) {
    assertTrue(effects.none { effect ->
        effect is GroupsEffect.MutationCompleted ||
            effect == GroupsEffect.NavigateToList ||
            effect is GroupsEffect.NavigateToDetail
    })
}

private class TestViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

private class TestSavedStateOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    override val lifecycle: Lifecycle = NoOpLifecycle()
    override val viewModelStore: ViewModelStore = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performAttach()
        controller.performRestore(null)
        enableSavedStateHandles()
    }

    fun seedSavedStateHandle(key: String, handle: SavedStateHandle) {
        // The JVM Android stubs do not implement Bundle.containsKey. Seed the
        // AndroidX SavedStateHandlesVM so the factory still exercises its real
        // CreationExtras/createSavedStateHandle branch without a fake factory.
        val handlesVmClass = Class.forName("androidx.lifecycle.SavedStateHandlesVM")
        val handlesVm = handlesVmClass.getDeclaredConstructor().apply {
            isAccessible = true
        }.newInstance() as androidx.lifecycle.ViewModel
        viewModelStore.put("androidx.lifecycle.internal.SavedStateHandlesVM", handlesVm)
        val handlesField = handlesVmClass.getDeclaredField("handles").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        (handlesField.get(handlesVm) as MutableMap<String, SavedStateHandle>)[key] = handle
    }
}

private class NoOpLifecycle : Lifecycle() {
    override fun addObserver(observer: LifecycleObserver) = Unit

    override fun removeObserver(observer: LifecycleObserver) = Unit

    override val currentState: State = State.INITIALIZED
}

private class FakeCollaborationRepository(
    groups: List<CollaborationGroup> = emptyList(),
    invites: List<GroupInvite> = emptyList(),
    members: List<GroupMember> = emptyList(),
    currentUserId: UserId? = UserId("owner-1")
) : CollaborationRepository {

    private val groupsFlow = MutableStateFlow(groups)
    private val invitesFlow = MutableStateFlow(invites)
    private val membersFlow = MutableStateFlow(members)

    var currentUserId: UserId? = currentUserId

    var members: List<GroupMember>
        get() = membersFlow.value
        set(value) {
            membersFlow.value = value
        }

    var groups: List<CollaborationGroup>
        get() = groupsFlow.value
        set(value) {
            groupsFlow.value = value
        }

    var refreshGroupsResult: CollaborationMutationResult = CollaborationMutationResult.Applied
    var refreshGroupsCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var refreshGroupsFailure: Throwable? = null
    var refreshGroupsCancelled: Boolean = false
    var createGroupCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var createGroupResult: CollaborationMutationResult = CollaborationMutationResult.Applied
    var cancelCreateGroup: Boolean = false
    var createGroupCancelled: Boolean = false
    val refreshGroupCompletions = mutableMapOf<CollaborationGroupId, CompletableDeferred<CollaborationMutationResult>>()
    val refreshGroupCancelled = mutableSetOf<CollaborationGroupId>()
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

    override fun currentUserId(): UserId? = currentUserId

    override suspend fun refreshGroups(): CollaborationMutationResult {
        refreshGroupsCalls += 1
        refreshGroupsFailure?.let { throw it }
        return try {
            refreshGroupsCompletion?.await() ?: refreshGroupsResult
        } catch (cancelled: CancellationException) {
            refreshGroupsCancelled = true
            throw cancelled
        }
    }

    override suspend fun refreshGroup(groupId: CollaborationGroupId): CollaborationMutationResult = try {
        refreshGroupCompletions[groupId]?.await() ?: CollaborationMutationResult.Applied
    } catch (cancelled: CancellationException) {
        refreshGroupCancelled += groupId
        throw cancelled
    }

    override suspend fun refreshInvites() = CollaborationMutationResult.Applied

    override suspend fun createGroup(command: CreateGroupCommand): CollaborationMutationResult {
        createGroupCommand = command
        mutationCalls += "create:${command.name}"
        if (cancelCreateGroup) throw CancellationException("cancelled")
        return try {
            createGroupCompletion?.await() ?: createGroupResult
        } catch (cancelled: CancellationException) {
            createGroupCancelled = true
            throw cancelled
        }
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
