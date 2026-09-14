package com.smartreminder.ui.groups.tasks

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId

sealed interface GroupTasksEffect {
    data class NavigateToGroupTasks(val groupId: CollaborationGroupId) : GroupTasksEffect
    data class NavigateToTask(
        val groupId: CollaborationGroupId,
        val taskId: GroupTaskId
    ) : GroupTasksEffect
    data object NavigateBack : GroupTasksEffect
    data class MutationCompleted(val mutation: GroupTasksMutation) : GroupTasksEffect
    data class MutationFailed(
        val mutation: GroupTasksMutation,
        val error: GroupTasksUiError,
        val retryAction: GroupTasksAction? = null,
        val showSnackbar: Boolean = true
    ) : GroupTasksEffect
}
