package com.smartreminder.ui.schedules.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

@Composable
fun RoutineEditorRoute(
    viewModel: RoutineEditorViewModel,
    mode: RoutineEditorMode,
    entryToken: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(entryToken) {
        viewModel.onAction(RoutineEditorAction.Initialize(mode, entryToken))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            onNavigateBack()
        }
    }

    RoutineEditorScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
