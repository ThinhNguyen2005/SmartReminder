package com.smartreminder.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Root ViewModel that reactively determines app destination and theme from DataStore.
 * No callbacks needed — DataStore Flow drives everything.
 */
class AppViewModel(
    repository: UserPreferencesRepository
) : ViewModel() {

    val appState: StateFlow<AppState> = repository.preferences
        .map { prefs ->
            if (prefs.onboardingCompleted) AppState.Main else AppState.Onboarding
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

    val themeMode: StateFlow<ThemeMode> = repository.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
}

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
