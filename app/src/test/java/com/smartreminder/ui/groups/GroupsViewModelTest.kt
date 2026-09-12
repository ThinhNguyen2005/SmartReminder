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
import kotlinx.coroutines.test.runCurrent
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
    fun `when authenticated identity changes, then old offline cache and actor actions are reset`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()
        assertEquals(UserId("owner-1"), viewModel.uiState.value.selectedGroup?.currentUserId)

        repository.currentUserId = UserId("account-b")
        advanceUntilIdle()

        val signedInAsB = viewModel.uiState.value
        assertEquals(GroupsScreen.LIST, signedInAsB.screen)
        assertTrue(signedInAsB.groups.isEmpty())
        assertTrue(signedInAsB.pendingInvites.isEmpty())
        assertNull(signedInAsB.selectedGroup)
        assertNull(signedInAsB.selectedGroupId)
        assertTrue(repository.clearSessionCacheCalls > 0)

        repository.groups = listOf(group("group-b", "B private"))
        repository.members = listOf(member("group-b", "account-b", GroupRole.MEMBER))
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-b")))
        advanceUntilIdle()
        val bDetail = viewModel.uiState.value.selectedGroup
        assertEquals(UserId("account-b"), bDetail?.currentUserId)
        assertEquals(GroupRole.MEMBER, bDetail?.currentUserRole)
        assertFalse(bDetail?.actorPermissions?.canInviteMember == true)
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
    fun `given restored selected group without cached members, then detail refresh starts and resolves`() = runTest {
        repository.members = emptyList()
        val refreshCompletion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = refreshCompletion
        val viewModel = GroupsViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(GroupsViewModel.SELECTED_GROUP_ID_KEY to "group-1")
            )
        )

        advanceUntilIdle()

        assertEquals(1, repository.refreshGroupCalls)
        assertEquals(GroupsDetailLoadState.LOADING, viewModel.uiState.value.detailLoadState)

        repository.members = listOf(member("group-1", "owner-1", GroupRole.OWNER))
        refreshCompletion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()

        assertEquals(GroupsDetailLoadState.CONTENT, viewModel.uiState.value.detailLoadState)
        assertEquals(UserId("owner-1"), viewModel.uiState.value.selectedGroup?.currentUserId)
        assertTrue(viewModel.uiState.value.selectedGroup?.actorPermissions != null)
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
    fun `when create succeeds with cold detail cache, then new owner detail is refreshed`() = runTest {
        val createdId = CollaborationGroupId("created-group")
        repository.groups = listOf(group("group-1", "Household"), group(createdId.value, "Created"))
        repository.members = emptyList()
        repository.createGroupResult = CollaborationMutationResult.Created(createdId)
        val detailRefresh = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[createdId] = detailRefresh
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(GroupsAction.CreateGroup("Created"))
        advanceUntilIdle()
        assertEquals(1, repository.refreshGroupCallsFor(createdId))
        assertEquals(GroupsDetailLoadState.LOADING, viewModel.uiState.value.detailLoadState)

        repository.members = listOf(member(createdId.value, "owner-1", GroupRole.OWNER))
        detailRefresh.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()

        assertEquals(createdId, viewModel.uiState.value.selectedGroupId)
        assertEquals(UserId("owner-1"), viewModel.uiState.value.selectedGroup?.currentUserId)
        assertTrue(viewModel.uiState.value.selectedGroup?.actorPermissions?.canInviteMember == true)
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
    fun `when manager has outgoing pending invite, then it is not rendered as a response card`() = runTest {
        repository.currentUserId = UserId("owner-1")
        repository.invites = listOf(
            invite("incoming", "group-1", inviteeUserId = "owner-1"),
            invite("outgoing", "group-1", inviteeUserId = "member-1")
        )
        val viewModel = GroupsViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf(GroupInviteId("incoming")), viewModel.uiState.value.pendingInvites.map { it.id })
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
    fun `when group-bound mutation fails, then failure does not expose a drifting retry action`() = runTest {
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()

        val actions = listOf(
            GroupsAction.UpdateGroup("Renamed"),
            GroupsAction.InviteMember("new@example.com"),
            GroupsAction.ChangeMemberRole(UserId("member-1"), GroupRole.ADMIN),
            GroupsAction.RemoveMember(UserId("member-1")),
            GroupsAction.TransferOwnership(UserId("member-1"))
        )
        actions.forEach { action ->
            val failureDeferred = async {
                viewModel.effects
                    .filter { it is GroupsEffect.MutationFailed }
                    .first() as GroupsEffect.MutationFailed
            }

            viewModel.onAction(action)
            advanceUntilIdle()

            assertNull(
                "${action::class.simpleName} must not retry against the current selected group",
                failureDeferred.await().retryAction
            )
        }
    }

    @Test
    fun `when leave or delete fails, then failure has no retry action that could bypass confirmation`() = runTest {
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenDeleteGroupDialog)
        assertEquals(GroupsDialog.DeleteGroup, viewModel.uiState.value.dialog)

        val failureDeferred = async {
            viewModel.effects
                .filter { it is GroupsEffect.MutationFailed }
                .first() as GroupsEffect.MutationFailed
        }
        viewModel.onAction(GroupsAction.DeleteGroup)
        advanceUntilIdle()
        val deleteFailure = failureDeferred.await()
        assertNull(deleteFailure.retryAction)
        assertFalse(deleteFailure.showSnackbar)
        assertEquals(GroupsDialog.DeleteGroup, viewModel.uiState.value.dialog)

        val leaveRepository = FakeCollaborationRepository(
            groups = listOf(group("group-1", "Household")),
            members = listOf(
                member("group-1", "owner-1", GroupRole.OWNER),
                member("group-1", "member-1", GroupRole.MEMBER)
            ),
            currentUserId = UserId("member-1")
        ).apply {
            mutationResult = CollaborationMutationResult.NetworkRequired
        }
        val leaveViewModel = GroupsViewModel(leaveRepository)
        advanceUntilIdle()
        leaveViewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()
        leaveViewModel.onAction(GroupsAction.OpenLeaveGroupDialog)
        assertEquals(GroupsDialog.LeaveGroup, leaveViewModel.uiState.value.dialog)
        val leaveFailureDeferred = async {
            leaveViewModel.effects
                .filter { it is GroupsEffect.MutationFailed }
                .first() as GroupsEffect.MutationFailed
        }
        leaveViewModel.onAction(GroupsAction.LeaveGroup)
        advanceUntilIdle()
        val leaveFailure = leaveFailureDeferred.await()
        assertNull(leaveFailure.retryAction)
        assertFalse(leaveFailure.showSnackbar)
        assertEquals(GroupsDialog.LeaveGroup, leaveViewModel.uiState.value.dialog)
    }

    @Test
    fun `when invite response fails, then retry action remains bound to invite id`() = runTest {
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()

        val acceptFailureDeferred = async {
            viewModel.effects
                .filter { it is GroupsEffect.MutationFailed }
                .first() as GroupsEffect.MutationFailed
        }
        viewModel.onAction(GroupsAction.AcceptInvite(GroupInviteId("invite-1")))
        advanceUntilIdle()
        assertEquals(
            GroupsAction.AcceptInvite(GroupInviteId("invite-1")),
            acceptFailureDeferred.await().retryAction
        )

        val declineFailureDeferred = async {
            viewModel.effects
                .filter { it is GroupsEffect.MutationFailed }
                .first() as GroupsEffect.MutationFailed
        }
        viewModel.onAction(GroupsAction.DeclineInvite(GroupInviteId("invite-1")))
        advanceUntilIdle()
        assertEquals(
            GroupsAction.DeclineInvite(GroupInviteId("invite-1")),
            declineFailureDeferred.await().retryAction
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
        val failure = effects.single() as GroupsEffect.MutationFailed
        assertEquals(GroupsMutation.CREATE_GROUP, failure.mutation)
        assertNull(failure.retryAction)
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
        val failure = effects.single() as GroupsEffect.MutationFailed
        assertEquals(GroupsUiError.Unknown(cause), failure.error)
        assertEquals(GroupsAction.CreateGroup("Failed"), failure.retryAction)
    }

    @Test
    fun `when mutation fails offline, then retryable mutation feedback effect is emitted`() = runTest {
        repository.createGroupResult = CollaborationMutationResult.NetworkRequired
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        val effects = mutableListOf<GroupsEffect>()
        val effectsJob = launch {
            viewModel.effects.collect { effects += it }
        }

        viewModel.onAction(GroupsAction.CreateGroup("Offline"))
        advanceUntilIdle()

        val failure = effects.single() as GroupsEffect.MutationFailed
        assertEquals(GroupsMutation.CREATE_GROUP, failure.mutation)
        assertEquals(GroupsUiError.Offline, failure.error)
        assertEquals(GroupsAction.CreateGroup("Offline"), failure.retryAction)
        effectsJob.cancel()
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
    fun `when detail refresh is delayed without cached members, then loading state hides actions until members arrive`() = runTest {
        repository.members = emptyList()
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))

        assertEquals(GroupsDetailLoadState.LOADING, viewModel.uiState.value.detailLoadState)
        assertNull(viewModel.uiState.value.selectedGroup?.actorPermissions)

        completion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()

        assertEquals(GroupsDetailLoadState.CONTENT, viewModel.uiState.value.detailLoadState)
    }

    @Test
    fun `when cached detail refresh goes offline, then cached members and retryable error remain`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        runCurrent()
        assertEquals(GroupsDetailLoadState.OFFLINE_REFRESHING, viewModel.uiState.value.detailLoadState)
        assertEquals(2, viewModel.uiState.value.selectedGroup?.members?.size)

        completion.complete(CollaborationMutationResult.NetworkRequired)
        advanceUntilIdle()

        assertEquals(GroupsDetailLoadState.CACHED_OFFLINE, viewModel.uiState.value.detailLoadState)
        assertEquals(GroupsUiError.Offline, viewModel.uiState.value.detailError)
        assertEquals(2, viewModel.uiState.value.selectedGroup?.members?.size)
    }

    @Test
    fun `when cached detail refresh fails, then typed detail error remains retryable`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        runCurrent()

        completion.complete(CollaborationMutationResult.Failure(CollaborationError.Conflict("stale")))
        advanceUntilIdle()

        assertEquals(GroupsDetailLoadState.CACHED_OFFLINE, viewModel.uiState.value.detailLoadState)
        assertEquals(GroupsUiError.Conflict("stale"), viewModel.uiState.value.detailError)
        assertEquals(2, viewModel.uiState.value.selectedGroup?.members?.size)
    }

    @Test
    fun `when cached detail refresh is unauthorized, then private detail is redacted`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        runCurrent()

        completion.complete(
            CollaborationMutationResult.NotAuthorized(CollaborationError.NotAuthorized)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GroupsScreen.LIST, state.screen)
        assertNull(state.selectedGroup)
        assertNull(state.selectedGroupId)
        assertFalse(state.groups.any { it.id == CollaborationGroupId("group-1") })
        assertEquals(GroupsUiError.NotAuthorized, state.error)
    }

    @Test
    fun `when cached detail is not found, then private detail is redacted`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        runCurrent()

        completion.complete(
            CollaborationMutationResult.Failure(CollaborationError.NotFound)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(GroupsScreen.LIST, state.screen)
        assertNull(state.selectedGroup)
        assertNull(state.selectedGroupId)
        assertFalse(state.groups.any { it.id == CollaborationGroupId("group-1") })
        assertEquals(GroupsUiError.NotFound, state.error)
    }

    @Test
    fun `when cached detail refresh is offline, then cached permissions remain available`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        runCurrent()

        completion.complete(CollaborationMutationResult.NetworkRequired)
        advanceUntilIdle()

        val detail = viewModel.uiState.value.selectedGroup
        assertEquals(GroupsUiError.Offline, viewModel.uiState.value.detailError)
        assertNotNull(detail?.currentUserRole)
        assertNotNull(detail?.actorPermissions)
        assertTrue(detail?.permissionsByMemberId?.isNotEmpty() == true)
    }

    @Test
    fun `when detail access is revoked, then cache is removed and no retry can expose it`() = runTest {
        val groupId = CollaborationGroupId("group-1")
        val initialRefresh = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[groupId] = initialRefresh

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(groupId))
        runCurrent()

        initialRefresh.complete(
            CollaborationMutationResult.NotAuthorized(CollaborationError.NotAuthorized)
        )
        advanceUntilIdle()
        val deniedState = viewModel.uiState.value
        assertEquals(GroupsScreen.LIST, deniedState.screen)
        assertNull(deniedState.selectedGroup)
        assertFalse(deniedState.groups.any { it.id == groupId })
        assertEquals(GroupsUiError.NotAuthorized, deniedState.error)
    }

    @Test
    fun `when create validation fails, then dialog keeps localized error until input or dismissal clears it`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenCreateGroupDialog)

        viewModel.onAction(GroupsAction.CreateGroup("", null))

        assertEquals(
            GroupsValidationKind.GROUP_NAME_REQUIRED,
            (viewModel.uiState.value.error as GroupsUiError.Validation).kind
        )
        assertEquals(GroupsDialog.CreateGroup, viewModel.uiState.value.dialog)

        viewModel.onAction(GroupsAction.DismissError)
        assertNull(viewModel.uiState.value.error)
        viewModel.onAction(GroupsAction.DismissDialog)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `when invite email validation fails, then active invite dialog exposes a typed error`() = runTest {
        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenInviteMemberDialog)

        viewModel.onAction(GroupsAction.InviteMember("not-an-email"))

        assertEquals(
            GroupsValidationKind.EMAIL_INVALID,
            (viewModel.uiState.value.error as GroupsUiError.Validation).kind
        )
        assertEquals(GroupsDialog.InviteMember(CollaborationGroupId("group-1")), viewModel.uiState.value.dialog)
    }

    @Test
    fun `when repository returns validation detail, then UI error is typed without raw server text`() = runTest {
        val completion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshGroupCompletions[CollaborationGroupId("group-1")] = completion

        val viewModel = GroupsViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(GroupsAction.OpenGroup(CollaborationGroupId("group-1")))
        runCurrent()

        completion.complete(
            CollaborationMutationResult.Failure(
                CollaborationError.Validation("server says invite email is invalid")
            )
        )
        advanceUntilIdle()

        val validation = viewModel.uiState.value.detailError as GroupsUiError.Validation
        assertEquals(GroupsValidationKind.GENERAL, validation.kind)
        assertNull(validation.diagnostic)
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

    private val identityFlow = MutableStateFlow(currentUserId)
    var currentUserId: UserId? = currentUserId
        set(value) {
            field = value
            identityFlow.value = value
        }
    var clearSessionCacheCalls: Int = 0

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

    var invites: List<GroupInvite>
        get() = invitesFlow.value
        set(value) {
            invitesFlow.value = value
        }

    var refreshGroupsResult: CollaborationMutationResult = CollaborationMutationResult.Applied
    var refreshGroupsCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var refreshGroupsFailure: Throwable? = null
    var refreshGroupsCancelled: Boolean = false
    var refreshGroupCalls: Int = 0
        private set
    private val refreshGroupCallCounts = mutableMapOf<CollaborationGroupId, Int>()
    var createGroupCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var createGroupResult: CollaborationMutationResult = CollaborationMutationResult.Applied
    var mutationResult: CollaborationMutationResult = CollaborationMutationResult.Applied
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

    override fun observeCurrentUserId(): Flow<UserId?> = identityFlow.asStateFlow()

    override suspend fun clearSessionCache() {
        clearSessionCacheCalls += 1
        groupsFlow.value = emptyList()
        invitesFlow.value = emptyList()
        membersFlow.value = emptyList()
    }

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
        refreshGroupCalls += 1
        refreshGroupCallCounts[groupId] = refreshGroupCallCounts.getOrDefault(groupId, 0) + 1
        refreshGroupCompletions[groupId]?.await() ?: CollaborationMutationResult.Applied
    } catch (cancelled: CancellationException) {
        refreshGroupCancelled += groupId
        throw cancelled
    }

    fun refreshGroupCallsFor(groupId: CollaborationGroupId): Int = refreshGroupCallCounts.getOrDefault(groupId, 0)

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
        return mutationResult
    }

    override suspend fun inviteMember(command: InviteMemberCommand): CollaborationMutationResult {
        mutationCalls += "invite:${command.email}"
        return mutationResult
    }

    override suspend fun acceptInvite(inviteId: GroupInviteId): CollaborationMutationResult {
        mutationCalls += "accept:${inviteId.value}"
        return mutationResult
    }

    override suspend fun declineInvite(inviteId: GroupInviteId): CollaborationMutationResult {
        mutationCalls += "decline:${inviteId.value}"
        return mutationResult
    }

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand): CollaborationMutationResult {
        mutationCalls += "role:${command.memberId.value}:${command.newRole.name}"
        return mutationResult
    }

    override suspend fun removeMember(command: RemoveMemberCommand): CollaborationMutationResult {
        mutationCalls += "remove:${command.memberId.value}"
        return mutationResult
    }

    override suspend fun transferOwnership(command: TransferOwnershipCommand): CollaborationMutationResult {
        mutationCalls += "transfer:${command.newOwnerId.value}"
        return mutationResult
    }

    override suspend fun leaveGroup(groupId: CollaborationGroupId): CollaborationMutationResult {
        mutationCalls += "leave:${groupId.value}"
        return mutationResult
    }

    override suspend fun deleteGroup(groupId: CollaborationGroupId): CollaborationMutationResult {
        mutationCalls += "delete:${groupId.value}"
        return mutationResult
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

private fun invite(id: String, groupId: String, inviteeUserId: String = "owner-1") = GroupInvite(
    id = GroupInviteId(id),
    groupId = CollaborationGroupId(groupId),
    inviterId = UserId("owner-1"),
    inviteeUserId = UserId(inviteeUserId),
    status = GroupInviteStatus.PENDING,
    createdAt = Instant.parse("2026-09-10T00:00:00Z")
)
