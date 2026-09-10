package com.smartreminder.ui.groups

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect

/**
 * State/effect boundary for the Groups feature. The ViewModel is created by the composition root
 * and this route only collects it and forwards typed actions.
 */
@Composable
fun GroupsRoute(
    viewModel: GroupsViewModel,
    modifier: Modifier = Modifier,
    onEffect: (GroupsEffect) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect(onEffect)
        }
    }

    BackHandler(enabled = uiState.screen == GroupsScreen.DETAIL) {
        viewModel.onAction(GroupsAction.Back)
    }

    when (uiState.screen) {
        GroupsScreen.LIST -> GroupsListScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            modifier = modifier
        )
        GroupsScreen.DETAIL -> GroupDetailScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            modifier = modifier
        )
    }

    GroupsDialogHost(
        dialog = uiState.dialog,
        uiState = uiState,
        onAction = viewModel::onAction
    )
}
