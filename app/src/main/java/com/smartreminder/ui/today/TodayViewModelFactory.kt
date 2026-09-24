package com.smartreminder.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.TaskRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import java.time.LocalDate
import java.time.LocalTime

class TodayViewModelFactory(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository,
    private val timeProvider: () -> LocalTime = { LocalTime.now() },
    private val dateProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            return TodayViewModel(
                userPreferencesRepository = userPreferencesRepository,
                routineRepository = routineRepository,
                taskRepository = taskRepository,
                timeProvider = timeProvider,
                dateProvider = dateProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
