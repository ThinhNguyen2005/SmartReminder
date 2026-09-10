package com.smartreminder.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.smartreminder.domain.repository.CollaborationRepository

class GroupsViewModelFactory(
    private val repository: CollaborationRepository,
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(GroupsViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return GroupsViewModel(
            repository = repository,
            savedStateHandle = savedStateHandle ?: SavedStateHandle()
        ) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(GroupsViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return GroupsViewModel(
            repository = repository,
            savedStateHandle = savedStateHandle ?: extras.createSavedStateHandle()
        ) as T
    }
}
