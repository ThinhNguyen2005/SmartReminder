package com.smartreminder.ui.groups.tasks

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.smartreminder.R
import com.smartreminder.ui.theme.CueSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Stateful boundary for task UI. ViewModel collection, lifecycle effect handling, and Snackbar
 * delivery stay here; [GroupTasksContent] remains reusable from Group Detail and only renders the
 * supplied state while emitting typed actions.
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
