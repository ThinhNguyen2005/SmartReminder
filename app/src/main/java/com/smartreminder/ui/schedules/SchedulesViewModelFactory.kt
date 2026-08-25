package com.smartreminder.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository

class SchedulesViewModelFactory(
    private val scheduleGroupRepository: ScheduleGroupRepository,
    private val routineRepository: RoutineRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SchedulesViewModel::class.java)) {
            return SchedulesViewModel(scheduleGroupRepository, routineRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
