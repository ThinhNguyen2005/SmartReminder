package com.smartreminder.ui.groups.tasks

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

sealed interface GroupTasksAction {
    data class OpenGroup(val groupId: CollaborationGroupId) : GroupTasksAction
    data object BackToGroups : GroupTasksAction
    data object Refresh : GroupTasksAction

    data class OpenTask(val taskId: GroupTaskId) : GroupTasksAction
    data object BackFromTask : GroupTasksAction
    data object OpenCreateTask : GroupTasksAction
    data object OpenEditTask : GroupTasksAction
    data object CancelEditor : GroupTasksAction
    data object SaveTask : GroupTasksAction

    data class ChangeTitle(val title: String) : GroupTasksAction
    data class ChangeDescription(val description: String) : GroupTasksAction
    data class ChangeAssignee(val assigneeId: UserId?) : GroupTasksAction
    data class ChangeDeadline(val dueAt: Instant?) : GroupTasksAction
    data class ChangeReminderOffsets(val offsetsSeconds: List<Long>) : GroupTasksAction

    data class ReassignTask(val assigneeId: UserId) : GroupTasksAction
    data object StartTask : GroupTasksAction
    data object CompleteTask : GroupTasksAction

    data object OpenCancelConfirmation : GroupTasksAction
    data object OpenReopenConfirmation : GroupTasksAction
    data object ConfirmCancel : GroupTasksAction
    data object ConfirmReopen : GroupTasksAction
    data object CancelTask : GroupTasksAction
    data object ReopenTask : GroupTasksAction
    data object DismissConfirmation : GroupTasksAction

    data object Retry : GroupTasksAction
    data object RetryLastMutation : GroupTasksAction
    data object DismissError : GroupTasksAction
}
