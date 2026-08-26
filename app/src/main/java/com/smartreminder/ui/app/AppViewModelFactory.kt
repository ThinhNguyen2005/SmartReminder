package com.smartreminder.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartreminder.domain.repository.UserPreferencesRepository

class AppViewModelFactory(
    private val repository: UserPreferencesRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
