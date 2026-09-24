package com.smartreminder.ui.groups.tasks

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import com.smartreminder.R

/** Handles Android back only while a task sub-flow is active. */
@Composable
internal fun GroupTasksBackHandler(
    uiState: GroupTasksUiState,
    onAction: (GroupTasksAction) -> Unit
) {
    BackHandler(enabled = uiState.screen != GroupTasksScreen.LIST) {
        when (uiState.screen) {
            GroupTasksScreen.DETAIL -> onAction(GroupTasksAction.BackFromTask)
            GroupTasksScreen.EDITOR -> onAction(GroupTasksAction.CancelEditor)
            GroupTasksScreen.LIST -> Unit
        }
    }
}

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

internal suspend fun showTaskMutationSnackbar(
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
