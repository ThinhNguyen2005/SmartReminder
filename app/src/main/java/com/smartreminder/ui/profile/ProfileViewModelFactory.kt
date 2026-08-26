package com.smartreminder.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator

class ProfileViewModelFactory(
    private val repository: UserPreferencesRepository,
    private val syncCoordinator: UserPreferencesSyncCoordinator
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(repository, syncCoordinator) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
