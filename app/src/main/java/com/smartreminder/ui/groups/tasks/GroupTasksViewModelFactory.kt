package com.smartreminder.ui.groups.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.smartreminder.domain.repository.CollaborationRepository
import java.time.Clock

class GroupTasksViewModelFactory(
    private val repository: CollaborationRepository,
    private val clock: Clock,
    private val idGenerator: GroupTaskIdGenerator,
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(GroupTasksViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return GroupTasksViewModel(
            repository = repository,
            clock = clock,
            idGenerator = idGenerator,
            savedStateHandle = savedStateHandle ?: SavedStateHandle()
        ) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(GroupTasksViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return GroupTasksViewModel(
            repository = repository,
            clock = clock,
            idGenerator = idGenerator,
            savedStateHandle = savedStateHandle ?: extras.createSavedStateHandle()
        ) as T
    }
}
