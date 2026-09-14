package com.smartreminder.ui.groups

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.ui.groups.tasks.GroupTasksAction
import com.smartreminder.ui.groups.tasks.GroupTasksLoadState
import com.smartreminder.ui.groups.tasks.GroupTasksScreen
import com.smartreminder.ui.groups.tasks.GroupTasksUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsRouteTest {

    @Test
    fun `inline mutation failure does not schedule a hidden snackbar`() {
        val effect = GroupsEffect.MutationFailed(
            mutation = GroupsMutation.DELETE_GROUP,
            error = GroupsUiError.Offline,
            showSnackbar = false
        )

        assertFalse(shouldShowMutationSnackbar(effect))
    }

    @Test
    fun `standalone mutation failure and success schedule the latest snackbar`() {
        val failure = GroupsEffect.MutationFailed(
            mutation = GroupsMutation.CREATE_GROUP,
            error = GroupsUiError.Offline,
            showSnackbar = true
        )
        val success = GroupsEffect.MutationCompleted(GroupsMutation.CREATE_GROUP)

        assertTrue(shouldShowMutationSnackbar(failure))
        assertTrue(shouldShowMutationSnackbar(success))
        assertFalse(
            shouldShowMutationSnackbar(
                GroupsEffect.NavigateToDetail(CollaborationGroupId("group-1"))
            )
        )
    }

    @Test
    fun `group selection opens task stream only when task vm is on another group`() {
        val groupId = CollaborationGroupId("group-1")

        assertEquals(
            GroupTasksAction.OpenGroup(groupId),
            groupTasksSynchronizationAction(
                groupsState = GroupsUiState(
                    loadState = GroupsLoadState.CONTENT,
                    selectedGroupId = groupId
                ),
                tasksState = GroupTasksUiState(
                    selectedGroupId = CollaborationGroupId("group-2")
                )
            )
        )
        assertNull(
            groupTasksSynchronizationAction(
                groupsState = GroupsUiState(
                    loadState = GroupsLoadState.CONTENT,
                    selectedGroupId = groupId
                ),
                tasksState = GroupTasksUiState(selectedGroupId = groupId)
            )
        )
    }

    @Test
    fun `restored task selection is held while groups are still restoring`() {
        assertNull(
            groupTasksSynchronizationAction(
                groupsState = GroupsUiState(isRefreshing = true),
                tasksState = GroupTasksUiState(
                    loadState = GroupTasksLoadState.CONTENT,
                    screen = GroupTasksScreen.DETAIL,
                    selectedGroupId = CollaborationGroupId("restored-group")
                )
            )
        )
    }

    @Test
    fun `task selection is cleared after groups settle on list`() {
        assertEquals(
            GroupTasksAction.BackToGroups,
            groupTasksSynchronizationAction(
                groupsState = GroupsUiState(
                    loadState = GroupsLoadState.EMPTY,
                    isRefreshing = false
                ),
                tasksState = GroupTasksUiState(
                    selectedGroupId = CollaborationGroupId("stale-group")
                )
            )
        )
    }
}
