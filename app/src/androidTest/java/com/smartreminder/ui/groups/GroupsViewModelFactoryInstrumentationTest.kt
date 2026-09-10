package com.smartreminder.ui.groups

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.EditOwnGroupTaskContentCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupsViewModelFactoryInstrumentationTest {

    @Test
    fun savedStateBundleRoundTripRestoresSelectedGroupIdThroughFactoryExtras() {
        var firstSelectedId: CollaborationGroupId? = null
        var restoredSelectedId: CollaborationGroupId? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val repository = InstrumentedGroupsRepository()
            val firstOwner = InstrumentedSavedStateOwner().also { it.attach(null) }
            val defaults = Bundle().apply {
                putString(GroupsViewModel.SELECTED_GROUP_ID_KEY, "group-1")
            }
            val firstViewModel = GroupsViewModelFactory(repository).create(
                GroupsViewModel::class.java,
                firstOwner.extras("groups", defaults)
            )
            firstSelectedId = firstViewModel.uiState.value.selectedGroupId

            val savedState = firstOwner.save()
            firstOwner.close()

            val recreatedOwner = InstrumentedSavedStateOwner().also { it.attach(savedState) }
            val recreatedViewModel = GroupsViewModelFactory(repository).create(
                GroupsViewModel::class.java,
                recreatedOwner.extras("groups")
            )
            restoredSelectedId = recreatedViewModel.uiState.value.selectedGroupId
            recreatedOwner.close()
        }

        assertEquals(CollaborationGroupId("group-1"), firstSelectedId)
        assertEquals(CollaborationGroupId("group-1"), restoredSelectedId)
    }
}

private class InstrumentedSavedStateOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    override val viewModelStore: ViewModelStore = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = controller.savedStateRegistry

    fun attach(restoredState: Bundle?) {
        controller.performAttach()
        controller.performRestore(restoredState)
        enableSavedStateHandles()
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun extras(key: String, defaultArgs: Bundle? = null): CreationExtras =
        MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@InstrumentedSavedStateOwner
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@InstrumentedSavedStateOwner
            this[ViewModelProvider.VIEW_MODEL_KEY] = key
            if (defaultArgs != null) this[DEFAULT_ARGS_KEY] = defaultArgs
        }

    fun save(): Bundle = Bundle().also(controller::performSave)

    fun close() {
        viewModelStore.clear()
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

private class InstrumentedGroupsRepository : CollaborationRepository {
    private val group = CollaborationGroup(
        id = CollaborationGroupId("group-1"),
        name = "Household",
        createdBy = UserId("owner-1"),
        createdAt = Instant.ofEpochMilli(1L),
        updatedAt = Instant.ofEpochMilli(1L)
    )

    override fun observeGroups(): Flow<List<CollaborationGroup>> = flowOf(listOf(group))

    override fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?> =
        flowOf(group.takeIf { it.id == groupId })

    override fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>> =
        flowOf(emptyList())

    override fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>> =
        flowOf(emptyList())

    override fun observeInvites(): Flow<List<GroupInvite>> = flowOf(emptyList())

    override suspend fun refreshGroups() = CollaborationMutationResult.Applied

    override suspend fun refreshGroup(groupId: CollaborationGroupId) = CollaborationMutationResult.Applied

    override suspend fun refreshInvites() = CollaborationMutationResult.Applied

    override suspend fun createGroup(command: CreateGroupCommand) = CollaborationMutationResult.Applied

    override suspend fun updateGroup(command: UpdateGroupCommand) = CollaborationMutationResult.Applied

    override suspend fun inviteMember(command: InviteMemberCommand) = CollaborationMutationResult.Applied

    override suspend fun acceptInvite(inviteId: GroupInviteId) = CollaborationMutationResult.Applied

    override suspend fun declineInvite(inviteId: GroupInviteId) = CollaborationMutationResult.Applied

    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = CollaborationMutationResult.Applied

    override suspend fun removeMember(command: RemoveMemberCommand) = CollaborationMutationResult.Applied

    override suspend fun transferOwnership(command: TransferOwnershipCommand) = CollaborationMutationResult.Applied

    override suspend fun leaveGroup(groupId: CollaborationGroupId) = CollaborationMutationResult.Applied

    override suspend fun deleteGroup(groupId: CollaborationGroupId) = CollaborationMutationResult.Applied

    override suspend fun createTask(command: CreateGroupTaskCommand) = CollaborationMutationResult.Applied

    override suspend fun startTask(taskId: GroupTaskId) = CollaborationMutationResult.Applied

    override suspend fun completeTask(taskId: GroupTaskId) = CollaborationMutationResult.Applied

    override suspend fun editOwnTaskContent(command: EditOwnGroupTaskContentCommand) =
        CollaborationMutationResult.Applied
}
