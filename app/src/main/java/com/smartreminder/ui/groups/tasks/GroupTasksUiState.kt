package com.smartreminder.ui.groups.tasks

import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId

enum class GroupTasksScreen {
    LIST,
    DETAIL,
    EDITOR
}

enum class GroupTasksLoadState {
    IDLE,
    LOADING,
    CONTENT,
    EMPTY,
    ERROR,
    CACHED_OFFLINE,
    OFFLINE_REFRESHING
}

enum class GroupTaskDetailLoadState {
    IDLE,
    LOADING,
    CONTENT,
    ERROR,
    CACHED_OFFLINE,
    OFFLINE_REFRESHING
}

sealed interface GroupTaskEditorMode {
    data object Create : GroupTaskEditorMode
    data class Edit(val taskId: GroupTaskId) : GroupTaskEditorMode
}

enum class GroupTaskField {
    TITLE,
    ASSIGNEE,
    DEADLINE,
    REMINDER_OFFSETS
}

enum class GroupTaskFieldError {
    TITLE_REQUIRED,
    ASSIGNEE_REQUIRED,
    ASSIGNEE_NOT_MEMBER,
    DEADLINE_REQUIRED,
    REMINDER_OFFSETS_REQUIRED,
    REMINDER_OFFSETS_TOO_MANY,
    REMINDER_OFFSETS_NON_POSITIVE,
    REMINDER_OFFSETS_DUPLICATE
}

sealed interface GroupTasksUiError {
    data object MissingConfiguration : GroupTasksUiError
    data object Offline : GroupTasksUiError
    data object NotAuthorized : GroupTasksUiError
    data object NotFound : GroupTasksUiError
    data object InvalidState : GroupTasksUiError
    data object Conflict : GroupTasksUiError
    data object ValidationRejected : GroupTasksUiError
    data class Validation(
        val field: GroupTaskField,
        val reason: GroupTaskFieldError
    ) : GroupTasksUiError
    data object Unknown : GroupTasksUiError
}

data class GroupTaskPermissions(
    val canEdit: Boolean = false,
    val canReassign: Boolean = false,
    val canStart: Boolean = false,
    val canComplete: Boolean = false,
    val canCancel: Boolean = false,
    val canReopen: Boolean = false
)

data class GroupTaskListItemUiModel(
    val task: GroupTask,
    val assignee: GroupMember?,
    val isOverdue: Boolean,
    val permissions: GroupTaskPermissions
) {
    val id: GroupTaskId
        get() = task.id
}

data class GroupTaskDetailUiModel(
    val details: GroupTaskDetails,
    val assignee: GroupMember?,
    val isOverdue: Boolean,
    val permissions: GroupTaskPermissions
) {
    val task: GroupTask
        get() = details.task

    val id: GroupTaskId
        get() = task.id
}

data class GroupTaskEditorUiState(
    val mode: GroupTaskEditorMode,
    val taskId: GroupTaskId? = null,
    val title: String = "",
    val description: String = "",
    val assigneeId: UserId? = null,
    val dueAt: java.time.Instant? = null,
    val reminderOffsetsSeconds: List<Long> = emptyList(),
    val expectedVersion: Long? = null,
    val errors: Map<GroupTaskField, GroupTaskFieldError> = emptyMap()
)

sealed interface GroupTaskConfirmation {
    data class Cancel(val taskId: GroupTaskId) : GroupTaskConfirmation
    data class Reopen(val taskId: GroupTaskId) : GroupTaskConfirmation
}

enum class GroupTasksMutation {
    CREATE,
    EDIT,
    REASSIGN,
    START,
    COMPLETE,
    CANCEL,
    REOPEN
}

data class PendingGroupTaskMutation(
    val mutation: GroupTasksMutation,
    val groupId: CollaborationGroupId,
    val taskId: GroupTaskId
)

data class GroupTasksUiState(
    val loadState: GroupTasksLoadState = GroupTasksLoadState.IDLE,
    val detailLoadState: GroupTaskDetailLoadState = GroupTaskDetailLoadState.IDLE,
    val screen: GroupTasksScreen = GroupTasksScreen.LIST,
    val selectedGroupId: CollaborationGroupId? = null,
    val selectedTaskId: GroupTaskId? = null,
    val tasks: List<GroupTaskListItemUiModel> = emptyList(),
    val members: List<GroupMember> = emptyList(),
    val selectedTask: GroupTaskDetailUiModel? = null,
    val editor: GroupTaskEditorUiState? = null,
    val confirmation: GroupTaskConfirmation? = null,
    val pendingMutation: PendingGroupTaskMutation? = null,
    val error: GroupTasksUiError? = null,
    val detailError: GroupTasksUiError? = null,
    val isCached: Boolean = false,
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDetailAccessRestricted: Boolean = false
) {
    val isLoading: Boolean
        get() = loadState == GroupTasksLoadState.LOADING

    val isMutationInProgress: Boolean
        get() = pendingMutation != null
}
