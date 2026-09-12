package com.smartreminder.ui.groups

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId

sealed interface GroupsEffect {
    data object NavigateToList : GroupsEffect
    data class NavigateToDetail(val groupId: CollaborationGroupId) : GroupsEffect
    data class MutationCompleted(val mutation: GroupsMutation) : GroupsEffect
    data class MutationFailed(
        val mutation: GroupsMutation,
        val error: GroupsUiError,
        val retryAction: GroupsAction? = null,
        val showSnackbar: Boolean = true
    ) : GroupsEffect
}
