package com.smartreminder.ui.groups

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import org.junit.Assert.assertFalse
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
}
