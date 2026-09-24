package com.smartreminder.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.TaskRepository
import com.smartreminder.domain.repository.UserProfileRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator

class ProfileViewModelFactory(
    private val repository: UserPreferencesRepository,
    private val syncCoordinator: UserPreferencesSyncCoordinator,
    private val userProfileRepository: UserProfileRepository,
    private val routineRepository: RoutineRepository? = null,
    private val taskRepository: TaskRepository? = null,
    private val appContext: Context? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(
                repository = repository,
                syncCoordinator = syncCoordinator,
                userProfileRepository = userProfileRepository,
                routineRepository = routineRepository,
                taskRepository = taskRepository,
                appContext = appContext
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
