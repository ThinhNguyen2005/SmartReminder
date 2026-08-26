package com.smartreminder.ui.schedules.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import java.time.Clock

class RoutineEditorViewModelFactory(
    private val scheduleGroupRepository: ScheduleGroupRepository,
    private val routineRepository: RoutineRepository,
    private val idGenerator: RoutineEditorIdGenerator,
    private val clock: Clock
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoutineEditorViewModel::class.java)) {
            return RoutineEditorViewModel(
                scheduleGroupRepository = scheduleGroupRepository,
                routineRepository = routineRepository,
                idGenerator = idGenerator,
                clock = clock
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
