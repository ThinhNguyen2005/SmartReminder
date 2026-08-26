package com.smartreminder.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Root ViewModel that reactively determines app destination and theme from DataStore.
 * Centralizes root navigation actions and preferences orchestration with robust error handling.
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Called when a user logs in (e.g. Google Sign-In).
     * Preserves any existing custom rhythm/goals in repository and marks onboarding completed.
     */
    fun completeOnboardingForAuthenticatedUser() {
        viewModelScope.launch {
            try {
                val current = repository.preferences.first()
                repository.completeOnboarding(
                    wakeUpTime = current.wakeUpTime,
                    sleepTime = current.sleepTime,
                    goals = current.goals
                )
            } catch (e: IOException) {
                _errorMessage.value = e.localizedMessage ?: "Failed to save user preferences"
            }
        }
    }

    /**
     * Resets the onboarding flag while preserving previously configured rhythm and goals.
     */
    fun resetOnboarding() {
        viewModelScope.launch {
            try {
                repository.resetOnboarding()
            } catch (e: IOException) {
                _errorMessage.value = e.localizedMessage ?: "Failed to reset onboarding"
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
