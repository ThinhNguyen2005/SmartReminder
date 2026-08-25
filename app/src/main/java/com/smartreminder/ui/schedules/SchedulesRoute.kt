package com.smartreminder.ui.schedules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartreminder.domain.model.schedule.ids.RoutineId

@Composable
fun SchedulesRoute(
    viewModel: SchedulesViewModel,
    onOpenRoutine: (RoutineId) -> Unit,
    onCreateRoutine: () -> Unit,
    onManageGroups: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SchedulesEffect.OpenRoutine -> onOpenRoutine(effect.routineId)
                SchedulesEffect.CreateRoutine -> onCreateRoutine()
                SchedulesEffect.ManageGroups -> onManageGroups()
            }
        }
    }

    SchedulesScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
