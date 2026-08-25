package com.smartreminder.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Root ViewModel that reactively determines app destination and theme from DataStore.
 * Centralizes root navigation actions and preferences orchestration.
 */
class AppViewModel(
    private val repository: UserPreferencesRepository
) : ViewModel() {

    val appState: StateFlow<AppState> = repository.preferences
        .map { prefs ->
            if (prefs.onboardingCompleted) AppState.Main else AppState.Onboarding
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

    val themeMode: StateFlow<ThemeMode> = repository.preferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /**
     * Called when a user logs in (e.g. Google Sign-In).
     * Preserves any existing custom rhythm/goals in repository and marks onboarding completed.
     */
    fun completeOnboardingForAuthenticatedUser() {
        viewModelScope.launch {
            val current = repository.preferences.first()
            repository.completeOnboarding(
                wakeUpTime = current.wakeUpTime,
                sleepTime = current.sleepTime,
                goals = current.goals
            )
        }
    }

    /**
     * Resets the onboarding flag while preserving previously configured rhythm and goals.
     */
    fun resetOnboarding() {
        viewModelScope.launch {
            repository.resetOnboarding()
        }
    }
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
