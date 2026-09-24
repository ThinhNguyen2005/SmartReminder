package com.smartreminder.ui.groups

import android.content.Context
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.smartreminder.R
import com.smartreminder.ui.groups.tasks.GroupTasksAction
import com.smartreminder.ui.groups.tasks.GroupTasksEffect
import com.smartreminder.ui.groups.tasks.GroupTasksScreen
import com.smartreminder.ui.groups.tasks.GroupTasksUiState
import com.smartreminder.ui.groups.tasks.GroupTasksViewModel
import com.smartreminder.ui.groups.tasks.showTaskMutationSnackbar
import com.smartreminder.ui.groups.tasks.shouldShowTaskMutationSnackbar
import com.smartreminder.ui.theme.CueSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * State/effect boundary for the Groups feature. The ViewModel is created by the composition root
 * and this route only collects it and forwards typed actions.
 */
@Composable
fun GroupsRoute(
    viewModel: GroupsViewModel,
    groupTasksViewModel: GroupTasksViewModel,
    modifier: Modifier = Modifier,
    onEffect: (GroupsEffect) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val taskUiState by groupTasksViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, groupTasksViewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var snackbarJob: Job? = null
            merge(
                viewModel.effects.map { GroupsRouteEffect.Group(it) },
                groupTasksViewModel.effects.map { GroupsRouteEffect.Task(it) }
            ).collect { event ->
                when (event) {
                    is GroupsRouteEffect.Group -> {
                        val effect = event.effect
                        if (effect is GroupsEffect.MutationCompleted ||
                            effect is GroupsEffect.MutationFailed
                        ) {
                            snackbarJob?.cancel()
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarJob = if (shouldShowMutationSnackbar(effect)) {
                                launch {
                                    showMutationSnackbar(
                                        effect = effect,
                                        context = context,
                                        snackbarHostState = snackbarHostState,
                                        onRetry = viewModel::onAction
                                    )
                                }
                            } else {
                                null
                            }
                        }
                        if (effect is GroupsEffect.NavigateToList) {
                            groupTasksViewModel.onAction(GroupTasksAction.BackToGroups)
                        }
                        onEffect(effect)
                    }
                    is GroupsRouteEffect.Task -> {
                        val effect = event.effect
                        if (effect is GroupTasksEffect.NavigateBack) {
                            viewModel.onAction(GroupsAction.Back)
                        }
                        if (shouldShowTaskMutationSnackbar(effect)) {
                            snackbarJob?.cancel()
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarJob = launch {
                                showTaskMutationSnackbar(
                                    effect = effect,
                                    context = context,
                                    snackbarHostState = snackbarHostState,
                                    onRetry = groupTasksViewModel::onAction
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel, groupTasksViewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(
                viewModel.uiState,
                groupTasksViewModel.uiState,
                ::groupTasksSynchronizationAction
            ).distinctUntilChanged().collect { action ->
                action?.let(groupTasksViewModel::onAction)
            }
        }
    }

    BackHandler(
        enabled = uiState.screen == GroupsScreen.DETAIL &&
            taskUiState.screen == GroupTasksScreen.LIST
    ) {
        viewModel.onAction(GroupsAction.Back)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState.screen) {
            GroupsScreen.LIST -> GroupsListScreen(
                uiState = uiState,
                onAction = viewModel::onAction,
                modifier = Modifier.fillMaxSize()
            )
            GroupsScreen.DETAIL -> GroupDetailScreen(
                uiState = uiState,
                onAction = viewModel::onAction,
                taskUiState = taskUiState,
                onTaskAction = groupTasksViewModel::onAction,
                modifier = Modifier.fillMaxSize()
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(CueSpacing.Lg)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                }
            )
        }

        GroupsDialogHost(
            dialog = uiState.dialog,
            uiState = uiState,
            onAction = viewModel::onAction
        )
    }
}

private sealed interface GroupsRouteEffect {
    data class Group(val effect: GroupsEffect) : GroupsRouteEffect
    data class Task(val effect: GroupTasksEffect) : GroupsRouteEffect
}

internal fun groupTasksSynchronizationAction(
    groupsState: GroupsUiState,
    tasksState: GroupTasksUiState
): GroupTasksAction? {
    if (tasksState.pendingMutation != null) return null
    val groupId = groupsState.selectedGroupId
    if (groupId == null) {
        return if (!groupsState.isRefreshing && tasksState.selectedGroupId != null) {
            GroupTasksAction.BackToGroups
        } else {
            null
        }
    }
    return GroupTasksAction.OpenGroup(groupId)
        .takeIf { tasksState.selectedGroupId != groupId }
}

internal fun shouldShowMutationSnackbar(effect: GroupsEffect): Boolean = when (effect) {
    is GroupsEffect.MutationCompleted -> true
    is GroupsEffect.MutationFailed -> effect.showSnackbar
    else -> false
}

@StringRes
private fun mutationSuccessStringRes(mutation: GroupsMutation): Int = when (mutation) {
    GroupsMutation.CREATE_GROUP -> R.string.groups_success_create
    GroupsMutation.UPDATE_GROUP -> R.string.groups_success_update
    GroupsMutation.INVITE_MEMBER -> R.string.groups_success_invite
    GroupsMutation.ACCEPT_INVITE -> R.string.groups_success_accept_invite
    GroupsMutation.DECLINE_INVITE -> R.string.groups_success_decline_invite
    GroupsMutation.CHANGE_MEMBER_ROLE -> R.string.groups_success_change_role
    GroupsMutation.REMOVE_MEMBER -> R.string.groups_success_remove_member
    GroupsMutation.TRANSFER_OWNERSHIP -> R.string.groups_success_transfer_ownership
    GroupsMutation.LEAVE_GROUP -> R.string.groups_success_leave
    GroupsMutation.DELETE_GROUP -> R.string.groups_success_delete
}

private suspend fun showMutationSnackbar(
    effect: GroupsEffect,
    context: Context,
    snackbarHostState: SnackbarHostState,
    onRetry: (GroupsAction) -> Unit
) {
    val result = when (effect) {
        is GroupsEffect.MutationCompleted -> snackbarHostState.showSnackbar(
            message = context.getString(mutationSuccessStringRes(effect.mutation)),
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        is GroupsEffect.MutationFailed -> snackbarHostState.showSnackbar(
            message = context.getString(
                R.string.groups_snackbar_failure,
                context.getString(groupsErrorStringRes(effect.error))
            ),
            actionLabel = effect.retryAction?.let {
                context.getString(R.string.groups_retry)
            },
            withDismissAction = true,
            duration = if (effect.retryAction == null) {
                SnackbarDuration.Long
            } else {
                SnackbarDuration.Indefinite
            }
        )
        else -> return
    }
    if (result == SnackbarResult.ActionPerformed) {
        (effect as? GroupsEffect.MutationFailed)?.retryAction?.let(onRetry)
    }
}
