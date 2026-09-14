package com.smartreminder.ui.groups.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.smartreminder.R
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * State-only task UI. It deliberately does not create a repository, ViewModel, or Scaffold so the
 * Groups feature can decide where this panel lives and keep ownership of bottom navigation.
 * Set [embedded] when placing the panel inside the Group Detail LazyColumn.
 */
@Composable
fun GroupTasksContent(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    val screenDescription = stringResource(R.string.groups_tasks_title)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CueTheme.colors.background)
            .semantics(mergeDescendants = false) {
                contentDescription = screenDescription
            }
    ) {
        GroupTasksHeader(uiState = uiState, onAction = onAction)
        when (uiState.screen) {
            GroupTasksScreen.LIST -> GroupTasksList(
                uiState = uiState,
                onAction = onAction,
                embedded = embedded
            )
            GroupTasksScreen.DETAIL -> GroupTaskDetail(
                uiState = uiState,
                onAction = onAction
            )
            GroupTasksScreen.EDITOR -> GroupTaskEditor(
                uiState = uiState,
                onAction = onAction
            )
        }
        uiState.confirmation?.let { confirmation ->
            GroupTaskConfirmationDialog(
                confirmation = confirmation,
                pending = uiState.isMutationInProgress,
                onAction = onAction
            )
        }
    }
}

/**
 * ViewModel adapter for task screens that need effect-driven Snackbar feedback. The content
 * composable above remains reusable from Group Detail and is still the only UI state boundary.
 */
@Composable
fun GroupTasksRoute(
    viewModel: GroupTasksViewModel,
    modifier: Modifier = Modifier,
    onEffect: (GroupTasksEffect) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var snackbarJob: Job? = null
            viewModel.effects.collect { effect ->
                snackbarJob?.cancel()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarJob = if (shouldShowTaskMutationSnackbar(effect)) {
                    launch {
                        showTaskMutationSnackbar(
                            effect = effect,
                            context = context,
                            snackbarHostState = snackbarHostState,
                            onRetry = viewModel::onAction
                        )
                    }
                } else {
                    null
                }
                onEffect(effect)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GroupTasksContent(
            uiState = uiState,
            onAction = viewModel::onAction,
            modifier = Modifier.fillMaxSize()
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(CueSpacing.Lg)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun GroupTasksHeader(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit
) {
    val refreshDescription = stringResource(R.string.groups_task_refresh_description)
    val newTaskDescription = stringResource(R.string.groups_task_new)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Assignment,
            contentDescription = null,
            tint = CueTheme.colors.accent,
            modifier = Modifier.size(CueSpacing.Xl)
        )
        Text(
            text = stringResource(R.string.groups_tasks_title),
            style = MaterialTheme.typography.titleLarge,
            color = CueTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onAction(GroupTasksAction.Refresh) },
            enabled = uiState.selectedGroupId != null &&
                !uiState.isRefreshing &&
                !uiState.isMutationInProgress,
            modifier = Modifier
                .size(CueSpacing.Xxxl)
                .semantics {
                    contentDescription = refreshDescription
                }
        ) {
            if (uiState.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(CueSpacing.Lg),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
            }
        }
        IconButton(
            onClick = { onAction(GroupTasksAction.OpenCreateTask) },
            enabled = uiState.selectedGroupId != null &&
                uiState.members.isNotEmpty() &&
                !uiState.isMutationInProgress,
            modifier = Modifier
                .size(CueSpacing.Xxxl)
                .semantics {
                    contentDescription = newTaskDescription
                }
        ) {
            Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
        }
    }
}

@Composable
private fun GroupTasksList(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit,
    embedded: Boolean
) {
    val body: @Composable () -> Unit = {
        GroupTasksStatus(uiState = uiState, onAction = onAction)
        when {
            uiState.loadState == GroupTasksLoadState.LOADING && uiState.tasks.isEmpty() ->
                GroupTasksLoading()
            uiState.loadState == GroupTasksLoadState.ERROR && uiState.tasks.isEmpty() ->
                GroupTasksError(uiState = uiState, onAction = onAction)
            uiState.tasks.isEmpty() ->
                GroupTasksEmpty()
            else -> {
                uiState.tasks.forEach { item ->
                    GroupTaskRow(
                        item = item,
                        onOpen = { onAction(GroupTasksAction.OpenTask(item.id)) }
                    )
                    Spacer(modifier = Modifier.size(CueSpacing.Sm))
                }
            }
        }
        uiState.pendingMutation?.let {
            TaskSavingBanner()
        }
        TaskInlineError(
            error = uiState.error,
            excludeValidation = false,
            onRetry = { onAction(taskRetryAction(uiState.error)) }
        )
    }

    if (embedded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            body()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = CueSpacing.Xl,
                end = CueSpacing.Xl,
                bottom = CueSpacing.Xxl
            ),
            verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            item(key = "task_status") {
                GroupTasksStatus(uiState = uiState, onAction = onAction)
            }
            when {
                uiState.loadState == GroupTasksLoadState.LOADING && uiState.tasks.isEmpty() ->
                    item(key = "task_loading") { GroupTasksLoading() }
                uiState.loadState == GroupTasksLoadState.ERROR && uiState.tasks.isEmpty() ->
                    item(key = "task_error") { GroupTasksError(uiState = uiState, onAction = onAction) }
                uiState.tasks.isEmpty() ->
                    item(key = "task_empty") { GroupTasksEmpty() }
                else -> items(items = uiState.tasks, key = { "task_${it.id.value}" }) { item ->
                    GroupTaskRow(
                        item = item,
                        onOpen = { onAction(GroupTasksAction.OpenTask(item.id)) }
                    )
                }
            }
            uiState.pendingMutation?.let {
                item(key = "task_saving") { TaskSavingBanner() }
            }
            if (uiState.error != null) {
                item(key = "task_inline_error") {
                    TaskInlineError(
                        error = uiState.error,
                        excludeValidation = false,
                        onRetry = { onAction(taskRetryAction(uiState.error)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupTasksStatus(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit
) {
    when (uiState.loadState) {
        GroupTasksLoadState.CACHED_OFFLINE -> TaskStatusBanner(
            message = stringResource(R.string.groups_task_offline_cached),
            icon = Icons.Outlined.Warning,
            isError = false,
            onRetry = { onAction(GroupTasksAction.Retry) }
        )
        GroupTasksLoadState.OFFLINE_REFRESHING -> TaskStatusBanner(
            message = stringResource(R.string.groups_task_offline_refreshing),
            icon = Icons.Outlined.Refresh,
            isError = false,
            onRetry = { onAction(GroupTasksAction.Retry) }
        )
        else -> Unit
    }
}

@Composable
private fun TaskStatusBanner(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isError: Boolean,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (isError) error(message)
            },
        color = if (isError) CueTheme.colors.errorContainer else CueTheme.colors.surfaceSubtle,
        shape = RoundedCornerShape(CueSpacing.Lg),
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = CueTheme.colors.warning)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CueTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_task_retry))
            }
        }
    }
}

@Composable
private fun GroupTasksLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.groups_task_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTheme.colors.textSecondary
        )
    }
}

@Composable
private fun GroupTasksEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Assignment,
            contentDescription = null,
            tint = CueTheme.colors.accent,
            modifier = Modifier.size(CueSpacing.Xxl)
        )
        Text(
            text = stringResource(R.string.groups_tasks_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = CueTheme.colors.textPrimary
        )
        Text(
            text = stringResource(R.string.groups_tasks_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = CueTheme.colors.textSecondary
        )
    }
}

@Composable
private fun GroupTasksError(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit
) {
    val message = taskErrorMessage(uiState.error)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Xxl)
            .semantics {
                error(message)
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = CueTheme.colors.error,
            modifier = Modifier.size(CueSpacing.Xxl)
        )
        Text(
            text = stringResource(R.string.groups_task_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = CueTheme.colors.textPrimary
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = CueTheme.colors.textSecondary)
        Button(
            onClick = { onAction(taskRetryAction(uiState.error)) },
            modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
        ) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(CueSpacing.Sm))
            Text(text = stringResource(R.string.groups_task_retry))
        }
    }
}

@Composable
private fun GroupTaskRow(
    item: GroupTaskListItemUiModel,
    onOpen: () -> Unit
) {
    val task = item.task
    val assigneeName = item.assignee?.displayName?.takeIf(String::isNotBlank)
        ?: item.assignee?.userId?.value
        ?: stringResource(R.string.groups_task_unassigned)
    val due = formatTaskDue(LocalContext.current, task.dueAt)
    val status = taskStatusLabel(task.status)
    val accessibleDescription = stringResource(R.string.groups_task_open_description, task.title)
    val metadataDescription = buildString {
        append(stringResource(R.string.groups_task_assignee, assigneeName))
        append(", ")
        append(stringResource(R.string.groups_task_due, due))
        append(", ")
        append(status)
        if (item.isOverdue) {
            append(", ")
            append(stringResource(R.string.groups_task_overdue_description))
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibleDescription
                stateDescription = metadataDescription
            }
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.surface,
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CueSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
        ) {
            Box(
                modifier = Modifier
                    .size(CueSpacing.Xxl)
                    .background(CueTheme.colors.accentContainer, RoundedCornerShape(CueSpacing.Md)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Assignment,
                    contentDescription = null,
                    tint = CueTheme.colors.accent,
                    modifier = Modifier.size(CueSpacing.Xl)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CueSpacing.Xs)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.groups_task_assignee, assigneeName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.groups_task_due, due),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
                ) {
                    TaskStatusLabel(status = task.status)
                    if (item.isOverdue) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = stringResource(R.string.groups_task_overdue_description),
                            tint = CueTheme.colors.warning,
                            modifier = Modifier.size(CueSpacing.Lg)
                        )
                        Text(
                            text = stringResource(R.string.groups_task_overdue),
                            style = MaterialTheme.typography.labelLarge,
                            color = CueTheme.colors.warning,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = CueTheme.colors.textSecondary,
                modifier = Modifier.size(CueSpacing.Lg)
            )
        }
    }
}

@Composable
private fun TaskStatusLabel(status: GroupTaskStatus) {
    val label = taskStatusLabel(status)
    val icon = when (status) {
        GroupTaskStatus.COMPLETED -> Icons.Outlined.Check
        GroupTaskStatus.CANCELLED -> Icons.Outlined.Close
        GroupTaskStatus.TODO,
        GroupTaskStatus.IN_PROGRESS -> Icons.Outlined.Schedule
    }
    Row(
        modifier = Modifier.semantics { stateDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Xs)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(CueSpacing.Lg))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = CueTheme.colors.textPrimary)
    }
}

@Composable
private fun GroupTaskDetail(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit
) {
    val selectedTask = uiState.selectedTask
    if (selectedTask == null) {
        GroupTaskDetailError(uiState = uiState, onAction = onAction)
        return
    }
    val task = selectedTask.task
    val backDescription = stringResource(R.string.groups_task_back_description)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)
    ) {
        GroupTasksStatus(uiState = uiState, onAction = onAction)
        IconButton(
            onClick = { onAction(GroupTasksAction.BackFromTask) },
            modifier = Modifier
                .size(CueSpacing.Xxxl)
                .semantics {
                    contentDescription = backDescription
                }
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
        }
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            color = CueTheme.colors.textPrimary
        )
        if (!task.description.isNullOrBlank()) {
            Text(
                text = task.description.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = CueTheme.colors.textSecondary
            )
        }
        TaskDetailMetadata(selectedTask = selectedTask)
        uiState.detailError?.let { error ->
            val offlineBannerVisible = uiState.detailLoadState == GroupTaskDetailLoadState.CACHED_OFFLINE ||
                uiState.detailLoadState == GroupTaskDetailLoadState.OFFLINE_REFRESHING
            if (error != GroupTasksUiError.Offline || !offlineBannerVisible) {
                TaskInlineError(
                    error = error,
                    excludeValidation = true,
                    onRetry = { onAction(taskRetryAction(error)) }
                )
            }
        }
        if (uiState.detailLoadState == GroupTaskDetailLoadState.OFFLINE_REFRESHING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        GroupTaskDetailActions(
            permissions = selectedTask.permissions,
            members = uiState.members,
            pending = uiState.isMutationInProgress,
            onAction = onAction
        )
    }
}

@Composable
private fun GroupTaskDetailError(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit
) {
    val message = taskErrorMessage(uiState.detailError ?: uiState.error)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CueSpacing.Xl)
            .semantics {
                error(message)
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)
    ) {
        if (uiState.detailLoadState == GroupTaskDetailLoadState.LOADING) {
            CircularProgressIndicator()
            Text(text = stringResource(R.string.groups_task_loading))
        } else {
            Text(text = message, color = CueTheme.colors.textSecondary)
            Button(onClick = { onAction(taskRetryAction(uiState.detailError ?: uiState.error)) }) {
                Text(text = stringResource(R.string.groups_task_retry))
            }
        }
    }
}

@Composable
private fun TaskDetailMetadata(selectedTask: GroupTaskDetailUiModel) {
    val context = LocalContext.current
    val assigneeName = selectedTask.assignee?.displayName?.takeIf(String::isNotBlank)
        ?: selectedTask.assignee?.userId?.value
        ?: stringResource(R.string.groups_task_unassigned)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CueTheme.colors.surfaceSubtle, RoundedCornerShape(CueSpacing.Lg))
            .padding(CueSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        Text(
            text = stringResource(R.string.groups_task_assignee, assigneeName),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTheme.colors.textPrimary
        )
        Text(
            text = stringResource(R.string.groups_task_due, formatTaskDue(context, selectedTask.task.dueAt)),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTheme.colors.textPrimary
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)) {
            TaskStatusLabel(selectedTask.task.status)
            if (selectedTask.isOverdue) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = stringResource(R.string.groups_task_overdue_description),
                    tint = CueTheme.colors.warning,
                    modifier = Modifier.size(CueSpacing.Lg)
                )
                Text(
                    text = stringResource(R.string.groups_task_overdue),
                    style = MaterialTheme.typography.labelLarge,
                    color = CueTheme.colors.warning
                )
            }
        }
        if (selectedTask.details.reminders.isNotEmpty()) {
            Text(
                text = stringResource(R.string.groups_task_reminders_title),
                style = MaterialTheme.typography.labelLarge,
                color = CueTheme.colors.textSecondary
            )
            Text(
                text = selectedTask.details.reminders
                    .sortedBy { it.offsetSeconds }
                    .joinToString(separator = ", ") { formatReminderOffset(it.offsetSeconds, context) },
                style = MaterialTheme.typography.bodyMedium,
                color = CueTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun GroupTaskDetailActions(
    permissions: GroupTaskPermissions,
    members: List<GroupMember>,
    pending: Boolean,
    onAction: (GroupTasksAction) -> Unit
) {
    var reassignExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            if (permissions.canStart) {
                TaskActionButton(
                    label = stringResource(R.string.groups_task_start),
                    icon = Icons.Outlined.Schedule,
                    enabled = !pending,
                    onClick = { onAction(GroupTasksAction.StartTask) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (permissions.canComplete) {
                TaskActionButton(
                    label = stringResource(R.string.groups_task_complete),
                    icon = Icons.Outlined.Check,
                    enabled = !pending,
                    onClick = { onAction(GroupTasksAction.CompleteTask) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            if (permissions.canEdit) {
                TaskActionButton(
                    label = stringResource(R.string.groups_task_edit),
                    icon = Icons.Outlined.Edit,
                    enabled = !pending,
                    onClick = { onAction(GroupTasksAction.OpenEditTask) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (permissions.canReassign) {
                Box(modifier = Modifier.weight(1f)) {
                    TaskActionButton(
                        label = stringResource(R.string.groups_task_reassign),
                        icon = Icons.Outlined.MoreVert,
                        enabled = !pending && members.isNotEmpty(),
                        onClick = { reassignExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = reassignExpanded,
                        onDismissRequest = { reassignExpanded = false }
                    ) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(text = memberDisplayName(member)) },
                                onClick = {
                                    reassignExpanded = false
                                    onAction(GroupTasksAction.ReassignTask(member.userId))
                                },
                                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            if (permissions.canCancel) {
                TaskActionButton(
                    label = stringResource(R.string.groups_task_cancel),
                    icon = Icons.Outlined.Close,
                    enabled = !pending,
                    onClick = { onAction(GroupTasksAction.OpenCancelConfirmation) },
                    destructive = true,
                    modifier = Modifier.weight(1f)
                )
            }
            if (permissions.canReopen) {
                TaskActionButton(
                    label = stringResource(R.string.groups_task_reopen),
                    icon = Icons.Outlined.Refresh,
                    enabled = !pending,
                    onClick = { onAction(GroupTasksAction.OpenReopenConfirmation) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (pending) TaskSavingBanner()
    }
}

@Composable
private fun TaskActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = CueSpacing.Xxxl),
        border = if (destructive) BorderStroke(1.dp, CueTheme.colors.error) else null,
        colors = if (destructive) {
            ButtonDefaults.outlinedButtonColors(contentColor = CueTheme.colors.error)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        contentPadding = PaddingValues(horizontal = CueSpacing.Sm, vertical = CueSpacing.Sm)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(CueSpacing.Lg))
        Spacer(modifier = Modifier.width(CueSpacing.Xs))
        Text(text = label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupTaskEditor(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit
) {
    val editor = uiState.editor ?: return
    val context = LocalContext.current
    val titleError = editor.errors[GroupTaskField.TITLE]?.let { taskFieldErrorMessage(it) }
    val assigneeError = editor.errors[GroupTaskField.ASSIGNEE]?.let { taskFieldErrorMessage(it) }
    val deadlineError = editor.errors[GroupTaskField.DEADLINE]?.let { taskFieldErrorMessage(it) }
    val remindersError = editor.errors[GroupTaskField.REMINDER_OFFSETS]?.let { taskFieldErrorMessage(it) }
    val backDescription = stringResource(R.string.groups_task_back_description)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)
    ) {
        GroupTasksStatus(uiState = uiState, onAction = onAction)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onAction(GroupTasksAction.CancelEditor) },
                enabled = !uiState.isMutationInProgress,
                modifier = Modifier
                    .size(CueSpacing.Xxxl)
                    .semantics {
                        contentDescription = backDescription
                    }
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
            Text(
                text = stringResource(
                    if (editor.mode is GroupTaskEditorMode.Create) {
                        R.string.groups_task_new
                    } else {
                        R.string.groups_task_edit
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )
        }
        OutlinedTextField(
            value = editor.title,
            onValueChange = { onAction(GroupTasksAction.ChangeTitle(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .taskValidationSemantics(titleError),
            label = { Text(text = stringResource(R.string.groups_task_title_label)) },
            enabled = !uiState.isMutationInProgress,
            isError = titleError != null,
            supportingText = { TaskFieldErrorText(titleError) },
            maxLines = 3
        )
        OutlinedTextField(
            value = editor.description,
            onValueChange = { onAction(GroupTasksAction.ChangeDescription(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.groups_task_description)) },
            enabled = !uiState.isMutationInProgress,
            minLines = 2,
            maxLines = 5
        )
        TaskMemberPicker(
            members = uiState.members,
            selectedId = editor.assigneeId,
            enabled = !uiState.isMutationInProgress,
            error = assigneeError,
            onSelected = { onAction(GroupTasksAction.ChangeAssignee(it)) }
        )
        TaskDeadlinePicker(
            dueAt = editor.dueAt,
            enabled = !uiState.isMutationInProgress,
            error = deadlineError,
            context = context,
            onDeadlineChanged = { onAction(GroupTasksAction.ChangeDeadline(it)) }
        )
        TaskReminderPicker(
            offsets = editor.reminderOffsetsSeconds,
            enabled = !uiState.isMutationInProgress,
            error = remindersError,
            context = context,
            onChanged = { onAction(GroupTasksAction.ChangeReminderOffsets(it)) }
        )
        TaskInlineError(
            error = uiState.error,
            excludeValidation = true,
            onRetry = { onAction(taskRetryAction(uiState.error)) }
        )
        if (uiState.isMutationInProgress) TaskSavingBanner()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            TextButton(
                onClick = { onAction(GroupTasksAction.CancelEditor) },
                enabled = !uiState.isMutationInProgress,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_task_cancel_edit))
            }
            Button(
                onClick = { onAction(GroupTasksAction.SaveTask) },
                enabled = !uiState.isMutationInProgress,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = CueSpacing.Xxxl)
            ) {
                if (uiState.isMutationInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CueSpacing.Lg),
                        strokeWidth = 2.dp,
                        color = CueTheme.colors.onCta
                    )
                } else {
                    Text(text = stringResource(R.string.groups_task_save))
                }
            }
        }
    }
}

@Composable
private fun TaskMemberPicker(
    members: List<GroupMember>,
    selectedId: UserId?,
    enabled: Boolean,
    error: String?,
    onSelected: (UserId) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = members.firstOrNull { it.userId == selectedId }
        ?.let(::memberDisplayName)
        ?: stringResource(R.string.groups_task_choose_assignee)
    Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Xs)) {
        Text(
            text = stringResource(R.string.groups_task_assignee_label),
            style = MaterialTheme.typography.labelLarge,
            color = CueTheme.colors.textSecondary
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled && members.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CueSpacing.Xxxl)
                    .taskValidationSemantics(error),
                contentPadding = PaddingValues(horizontal = CueSpacing.Lg, vertical = CueSpacing.Sm)
            ) {
                Text(
                    text = selectedName,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                members.forEach { member ->
                    DropdownMenuItem(
                        text = { Text(text = memberDisplayName(member)) },
                        onClick = {
                            expanded = false
                            onSelected(member.userId)
                        },
                        modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
                    )
                }
            }
        }
        TaskFieldErrorText(error)
    }
}

@Composable
private fun TaskDeadlinePicker(
    dueAt: Instant?,
    enabled: Boolean,
    error: String?,
    context: Context,
    onDeadlineChanged: (Instant) -> Unit
) {
    var dateDialogVisible by remember { mutableStateOf(false) }
    var timeDialogVisible by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val dateTime = dueAt?.atZone(zone)
    Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Xs)) {
        Text(
            text = stringResource(R.string.groups_task_deadline_label),
            style = MaterialTheme.typography.labelLarge,
            color = CueTheme.colors.textSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            OutlinedButton(
                onClick = { dateDialogVisible = true },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = CueSpacing.Xxxl)
                    .taskValidationSemantics(error),
                contentPadding = PaddingValues(horizontal = CueSpacing.Sm, vertical = CueSpacing.Sm)
            ) {
                Icon(imageVector = Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(modifier = Modifier.width(CueSpacing.Xs))
                Text(
                    text = dueAt?.let { formatTaskDue(context, it) }
                        ?: stringResource(R.string.groups_task_change_deadline),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { timeDialogVisible = true },
                enabled = enabled && dueAt != null,
                modifier = Modifier
                    .heightIn(min = CueSpacing.Xxxl)
                    .taskValidationSemantics(error),
                contentPadding = PaddingValues(horizontal = CueSpacing.Sm, vertical = CueSpacing.Sm)
            ) {
                Icon(imageVector = Icons.Outlined.Schedule, contentDescription = null)
                Spacer(modifier = Modifier.width(CueSpacing.Xs))
                Text(text = stringResource(R.string.groups_task_change_time))
            }
        }
        TaskFieldErrorText(error)
    }
    if (dateDialogVisible) {
        val initial = dateTime ?: ZonedDateTime.now(zone).plusDays(1)
        DisposableEffect(dateDialogVisible, dueAt) {
            val dialog = DatePickerDialog(
                context,
                { _: DatePicker, year: Int, month: Int, day: Int ->
                    val selected = ZonedDateTime.of(
                        year,
                        month + 1,
                        day,
                        dateTime?.hour ?: 9,
                        dateTime?.minute ?: 0,
                        0,
                        0,
                        zone
                    )
                    onDeadlineChanged(selected.toInstant())
                    dateDialogVisible = false
                },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth
            )
            dialog.setOnDismissListener { dateDialogVisible = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
    if (timeDialogVisible && dueAt != null) {
        val initial = dateTime ?: dueAt.atZone(zone)
        DisposableEffect(timeDialogVisible, dueAt) {
            val dialog = TimePickerDialog(
                context,
                { _: TimePicker, hour: Int, minute: Int ->
                    val selected = initial.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    onDeadlineChanged(selected.toInstant())
                    timeDialogVisible = false
                },
                initial.hour,
                initial.minute,
                true
            )
            dialog.setOnDismissListener { timeDialogVisible = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}

@Composable
private fun TaskReminderPicker(
    offsets: List<Long>,
    enabled: Boolean,
    error: String?,
    context: Context,
    onChanged: (List<Long>) -> Unit
) {
    val presets = listOf(
        300L to R.string.groups_task_reminder_5_minutes,
        900L to R.string.groups_task_reminder_15_minutes,
        1800L to R.string.groups_task_reminder_30_minutes,
        3600L to R.string.groups_task_reminder_1_hour,
        86400L to R.string.groups_task_reminder_1_day
    )
    val customOffsets = offsets.filter { value -> presets.none { it.first == value } }
    Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)) {
        Text(
            text = stringResource(R.string.groups_task_reminders_title),
            style = MaterialTheme.typography.labelLarge,
            color = CueTheme.colors.textSecondary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            presets.forEach { (seconds, labelRes) ->
                FilterChip(
                    selected = seconds in offsets,
                    onClick = {
                        val next = if (seconds in offsets) {
                            offsets.filterNot { it == seconds }
                        } else {
                            (offsets + seconds).distinct().take(5)
                        }
                        onChanged(next)
                    },
                    enabled = enabled,
                    label = { Text(text = stringResource(labelRes)) },
                    leadingIcon = if (seconds in offsets) {
                        { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
                )
            }
            customOffsets.forEach { seconds ->
                FilterChip(
                    selected = true,
                    onClick = { onChanged(offsets.filterNot { it == seconds }) },
                    enabled = enabled,
                    label = { Text(text = formatReminderOffset(seconds, context)) },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) },
                    modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
                )
            }
        }
        TaskFieldErrorText(error)
    }
}

@Composable
private fun TaskFieldErrorText(message: String?) {
    message?.let {
        Text(
            text = it,
            color = CueTheme.colors.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }
        )
    }
}

private fun Modifier.taskValidationSemantics(message: String?): Modifier = if (message == null) {
    this
} else {
    semantics(mergeDescendants = true) {
        error(message)
        liveRegion = LiveRegionMode.Polite
    }
}

@Composable
private fun TaskSavingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CueTheme.colors.surfaceSubtle, RoundedCornerShape(CueSpacing.Lg))
            .padding(CueSpacing.Lg)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(CueSpacing.Lg), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.groups_task_saving),
            style = MaterialTheme.typography.bodyMedium,
            color = CueTheme.colors.textSecondary
        )
    }
}

@Composable
private fun TaskInlineError(
    error: GroupTasksUiError?,
    excludeValidation: Boolean,
    onRetry: () -> Unit
) {
    if (error == null || (excludeValidation && error is GroupTasksUiError.Validation)) return
    val message = taskErrorMessage(error)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                error(message)
                liveRegion = LiveRegionMode.Polite
            },
        color = CueTheme.colors.errorContainer,
        shape = RoundedCornerShape(CueSpacing.Lg),
        border = BorderStroke(1.dp, CueTheme.colors.error)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            Icon(imageVector = Icons.Outlined.Warning, contentDescription = null, tint = CueTheme.colors.error)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CueTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (error == GroupTasksUiError.Offline || error == GroupTasksUiError.Conflict || error == GroupTasksUiError.Unknown) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
                ) {
                    Text(text = stringResource(R.string.groups_task_retry))
                }
            }
        }
    }
}

@Composable
private fun GroupTaskConfirmationDialog(
    confirmation: GroupTaskConfirmation,
    pending: Boolean,
    onAction: (GroupTasksAction) -> Unit
) {
    val isCancel = confirmation is GroupTaskConfirmation.Cancel
    AlertDialog(
        onDismissRequest = { if (!pending) onAction(GroupTasksAction.DismissConfirmation) },
        title = {
            Text(
                text = stringResource(
                    if (isCancel) R.string.groups_task_cancel_confirm_title
                    else R.string.groups_task_reopen_confirm_title
                )
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (isCancel) R.string.groups_task_cancel_confirm_message
                    else R.string.groups_task_reopen_confirm_message
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onAction(
                        if (isCancel) GroupTasksAction.ConfirmCancel else GroupTasksAction.ConfirmReopen
                    )
                },
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl),
                colors = if (isCancel) {
                    ButtonDefaults.buttonColors(
                        containerColor = CueTheme.colors.error,
                        contentColor = CueTheme.colors.onCta
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(text = stringResource(R.string.groups_task_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(GroupTasksAction.DismissConfirmation) },
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_task_keep))
            }
        }
    )
}

private fun memberDisplayName(member: GroupMember): String =
    member.displayName?.takeIf(String::isNotBlank) ?: member.userId.value

@Composable
private fun taskStatusLabel(status: GroupTaskStatus): String = stringResource(
    when (status) {
        GroupTaskStatus.TODO -> R.string.groups_task_status_todo
        GroupTaskStatus.IN_PROGRESS -> R.string.groups_task_status_in_progress
        GroupTaskStatus.COMPLETED -> R.string.groups_task_status_completed
        GroupTaskStatus.CANCELLED -> R.string.groups_task_status_cancelled
    }
)

private fun formatTaskDue(context: Context, instant: Instant): String {
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun formatReminderOffset(seconds: Long, context: Context): String {
    val preset = when (seconds) {
        300L -> R.string.groups_task_reminder_5_minutes
        900L -> R.string.groups_task_reminder_15_minutes
        1800L -> R.string.groups_task_reminder_30_minutes
        3600L -> R.string.groups_task_reminder_1_hour
        86400L -> R.string.groups_task_reminder_1_day
        else -> null
    }
    return if (preset == null) {
        context.getString(R.string.groups_task_reminder_custom, seconds)
    } else {
        context.getString(preset)
    }
}

private fun taskRetryAction(error: GroupTasksUiError?): GroupTasksAction = when (error) {
    GroupTasksUiError.Conflict -> GroupTasksAction.Refresh
    else -> GroupTasksAction.Retry
}

@StringRes
internal fun taskErrorStringRes(error: GroupTasksUiError?): Int = when (error) {
    GroupTasksUiError.MissingConfiguration -> R.string.groups_task_error_configuration
    GroupTasksUiError.Offline -> R.string.groups_task_error_offline
    GroupTasksUiError.NotAuthorized -> R.string.groups_task_error_not_authorized
    GroupTasksUiError.NotFound -> R.string.groups_task_error_not_found
    GroupTasksUiError.InvalidState -> R.string.groups_task_error_invalid_state
    GroupTasksUiError.Conflict -> R.string.groups_task_error_conflict
    GroupTasksUiError.ValidationRejected -> R.string.groups_task_error_validation
    is GroupTasksUiError.Validation -> taskFieldErrorStringRes(error.reason)
    GroupTasksUiError.Unknown, null -> R.string.groups_task_error_unknown
}

@Composable
internal fun taskErrorMessage(error: GroupTasksUiError?): String =
    stringResource(taskErrorStringRes(error))

@StringRes
private fun taskFieldErrorStringRes(reason: GroupTaskFieldError): Int = when (reason) {
    GroupTaskFieldError.TITLE_REQUIRED -> R.string.groups_task_error_title_required
    GroupTaskFieldError.ASSIGNEE_REQUIRED -> R.string.groups_task_error_assignee_required
    GroupTaskFieldError.ASSIGNEE_NOT_MEMBER -> R.string.groups_task_error_assignee_not_member
    GroupTaskFieldError.DEADLINE_REQUIRED -> R.string.groups_task_error_deadline_required
    GroupTaskFieldError.REMINDER_OFFSETS_REQUIRED -> R.string.groups_task_error_reminders_required
    GroupTaskFieldError.REMINDER_OFFSETS_TOO_MANY -> R.string.groups_task_error_reminders_too_many
    GroupTaskFieldError.REMINDER_OFFSETS_NON_POSITIVE -> R.string.groups_task_error_reminders_non_positive
    GroupTaskFieldError.REMINDER_OFFSETS_DUPLICATE -> R.string.groups_task_error_reminders_duplicate
}

@Composable
private fun taskFieldErrorMessage(reason: GroupTaskFieldError): String =
    stringResource(taskFieldErrorStringRes(reason))

internal fun shouldShowTaskMutationSnackbar(effect: GroupTasksEffect): Boolean = when (effect) {
    is GroupTasksEffect.MutationCompleted -> true
    is GroupTasksEffect.MutationFailed -> effect.showSnackbar
    else -> false
}

@StringRes
private fun mutationSuccessStringRes(mutation: GroupTasksMutation): Int = when (mutation) {
    GroupTasksMutation.CREATE -> R.string.groups_task_success_create
    GroupTasksMutation.EDIT -> R.string.groups_task_success_edit
    GroupTasksMutation.REASSIGN -> R.string.groups_task_success_reassign
    GroupTasksMutation.START -> R.string.groups_task_success_start
    GroupTasksMutation.COMPLETE -> R.string.groups_task_success_complete
    GroupTasksMutation.CANCEL -> R.string.groups_task_success_cancel
    GroupTasksMutation.REOPEN -> R.string.groups_task_success_reopen
}

private suspend fun showTaskMutationSnackbar(
    effect: GroupTasksEffect,
    context: Context,
    snackbarHostState: SnackbarHostState,
    onRetry: (GroupTasksAction) -> Unit
) {
    val result = when (effect) {
        is GroupTasksEffect.MutationCompleted -> snackbarHostState.showSnackbar(
            message = context.getString(mutationSuccessStringRes(effect.mutation)),
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        is GroupTasksEffect.MutationFailed -> snackbarHostState.showSnackbar(
            message = context.getString(
                R.string.groups_task_snackbar_failure,
                context.getString(taskErrorStringRes(effect.error))
            ),
            actionLabel = effect.retryAction?.let { context.getString(R.string.groups_task_retry) },
            withDismissAction = true,
            duration = if (effect.retryAction == null) SnackbarDuration.Long else SnackbarDuration.Indefinite
        )
        else -> return
    }
    if (result == SnackbarResult.ActionPerformed) {
        (effect as? GroupTasksEffect.MutationFailed)?.retryAction?.let(onRetry)
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupTasksContentPreview() {
    val groupId = com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId("preview-group")
    val ownerId = UserId("preview-owner")
    val member = GroupMember(
        groupId = groupId,
        userId = ownerId,
        role = GroupRole.OWNER,
        joinedAt = Instant.EPOCH,
        displayName = "Preview owner"
    )
    val task = GroupTask(
        id = GroupTaskId("preview-task"),
        groupId = groupId,
        title = "Prepare the weekly plan",
        description = "Preview-only content",
        createdBy = ownerId,
        assigneeId = ownerId,
        dueAt = Instant.parse("2026-09-16T10:00:00Z"),
        status = GroupTaskStatus.IN_PROGRESS,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
    SmartReminderTheme {
        GroupTasksContent(
            uiState = GroupTasksUiState(
                loadState = GroupTasksLoadState.CONTENT,
                selectedGroupId = groupId,
                members = listOf(member),
                tasks = listOf(
                    GroupTaskListItemUiModel(
                        task = task,
                        assignee = member,
                        isOverdue = false,
                        permissions = GroupTaskPermissions(canStart = true)
                    )
                )
            ),
            onAction = {}
        )
    }
}
