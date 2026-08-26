package com.smartreminder.ui.schedules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import com.smartreminder.ui.schedules.editor.RoutineEditorIdGenerator
import com.smartreminder.ui.schedules.editor.RoutineEditorMode
import com.smartreminder.ui.schedules.editor.RoutineEditorRoute
import com.smartreminder.ui.schedules.editor.RoutineEditorViewModel
import com.smartreminder.ui.schedules.editor.RoutineEditorViewModelFactory
import java.time.Clock

@Composable
fun SchedulesHost(
    schedulesViewModel: SchedulesViewModel,
    scheduleGroupRepository: ScheduleGroupRepository,
    routineRepository: RoutineRepository,
    idGenerator: RoutineEditorIdGenerator,
    clock: Clock,
    onManageGroups: () -> Unit,
    modifier: Modifier = Modifier
) {
    var destination by rememberSaveable { mutableStateOf(SchedulesHostDestination.LIST.name) }
    var selectedRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    var entryToken by rememberSaveable { mutableStateOf(0L) }
    val editorViewModel: RoutineEditorViewModel = viewModel(
        factory = RoutineEditorViewModelFactory(
            scheduleGroupRepository = scheduleGroupRepository,
            routineRepository = routineRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    )

    fun openCreate() {
        selectedRoutineId = null
        destination = SchedulesHostDestination.CREATE.name
        entryToken += 1L
    }

    fun openEdit(routineId: RoutineId) {
        selectedRoutineId = routineId.value
        destination = SchedulesHostDestination.EDIT.name
        entryToken += 1L
    }

    when (destination) {
        SchedulesHostDestination.LIST.name -> SchedulesRoute(
            viewModel = schedulesViewModel,
            onOpenRoutine = ::openEdit,
            onCreateRoutine = ::openCreate,
            onManageGroups = onManageGroups,
            modifier = modifier
        )
        SchedulesHostDestination.CREATE.name -> RoutineEditorRoute(
            viewModel = editorViewModel,
            mode = RoutineEditorMode.Create,
            entryToken = entryToken,
            onNavigateBack = { destination = SchedulesHostDestination.LIST.name },
            modifier = modifier
        )
        SchedulesHostDestination.EDIT.name -> {
            val routineId = selectedRoutineId?.let { RoutineId(it) }
            if (routineId == null) {
                destination = SchedulesHostDestination.LIST.name
            } else {
                RoutineEditorRoute(
                    viewModel = editorViewModel,
                    mode = RoutineEditorMode.Edit(routineId),
                    entryToken = entryToken,
                    onNavigateBack = { destination = SchedulesHostDestination.LIST.name },
                    modifier = modifier
                )
            }
        }
        else -> destination = SchedulesHostDestination.LIST.name
    }
}

private enum class SchedulesHostDestination {
    LIST,
    CREATE,
    EDIT
}
