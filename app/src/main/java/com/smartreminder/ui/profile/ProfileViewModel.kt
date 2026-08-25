package com.smartreminder.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.domain.repository.UserPreferencesRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.LocalTime

/**
 * ViewModel orchestrating Profile & Settings UI state, time picker editing, and sign-out flow.
 */
class ProfileViewModel(
    private val repository: UserPreferencesRepository,
    private val onSignedOut: () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                val currentUser = try {
                    SupabaseManager.client.auth.currentUserOrNull()
                } catch (e: Exception) {
                    null
                }

                val displayName = currentUser?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: currentUser?.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                val avatarUrl = currentUser?.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                    ?: currentUser?.userMetadata?.get("picture")?.jsonPrimitive?.contentOrNull
                val email = currentUser?.email

                _uiState.update { currentState ->
                    if (currentState is ProfileUiState.Loaded) {
                        currentState.copy(
                            displayName = displayName,
                            email = email,
                            avatarUrl = avatarUrl,
                            wakeUpTime = prefs.wakeUpTime,
                            sleepTime = prefs.sleepTime,
                            themeMode = prefs.themeMode
                        )
                    } else {
                        ProfileUiState.Loaded(
                            displayName = displayName,
                            email = email,
                            avatarUrl = avatarUrl,
                            wakeUpTime = prefs.wakeUpTime,
                            sleepTime = prefs.sleepTime,
                            themeMode = prefs.themeMode
                        )
                    }
                }
            }
        }
    }

    fun onAction(action: ProfileUiAction) {
        when (action) {
            ProfileUiAction.RequestSignOut -> {
                updateLoadedState { it.copy(showSignOutDialog = true) }
            }
            ProfileUiAction.DismissSignOutDialog -> {
                updateLoadedState { it.copy(showSignOutDialog = false) }
            }
            ProfileUiAction.ConfirmSignOut -> {
                signOut()
            }
            ProfileUiAction.OpenWakeTimePicker -> {
                updateLoadedState { it.copy(showWakeTimePicker = true) }
            }
            ProfileUiAction.DismissWakeTimePicker -> {
                updateLoadedState { it.copy(showWakeTimePicker = false) }
            }
            is ProfileUiAction.UpdateWakeTime -> {
                updateWakeTime(action.time)
            }
            ProfileUiAction.OpenSleepTimePicker -> {
                updateLoadedState { it.copy(showSleepTimePicker = true) }
            }
            ProfileUiAction.DismissSleepTimePicker -> {
                updateLoadedState { it.copy(showSleepTimePicker = false) }
            }
            is ProfileUiAction.UpdateSleepTime -> {
                updateSleepTime(action.time)
            }
            ProfileUiAction.DismissError -> {
                updateLoadedState { it.copy(errorMessage = null) }
            }
        }
    }

    private fun updateWakeTime(time: LocalTime) {
        val currentState = _uiState.value as? ProfileUiState.Loaded ?: return
        viewModelScope.launch {
            try {
                repository.updateRhythm(wakeUpTime = time, sleepTime = currentState.sleepTime)
                updateLoadedState { it.copy(showWakeTimePicker = false) }
            } catch (e: IOException) {
                updateLoadedState { it.copy(showWakeTimePicker = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private fun updateSleepTime(time: LocalTime) {
        val currentState = _uiState.value as? ProfileUiState.Loaded ?: return
        viewModelScope.launch {
            try {
                repository.updateRhythm(wakeUpTime = currentState.wakeUpTime, sleepTime = time)
                updateLoadedState { it.copy(showSleepTimePicker = false) }
            } catch (e: IOException) {
                updateLoadedState { it.copy(showSleepTimePicker = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            try {
                SupabaseManager.client.auth.signOut()
            } catch (ignored: Exception) {
                // Session teardown on device takes precedence
            }
            updateLoadedState { it.copy(showSignOutDialog = false) }
            onSignedOut()
        }
    }

    private fun updateLoadedState(transform: (ProfileUiState.Loaded) -> ProfileUiState.Loaded) {
        _uiState.update { currentState ->
            if (currentState is ProfileUiState.Loaded) {
                transform(currentState)
            } else {
                currentState
            }
        }
    }
}

class ProfileViewModelFactory(
    private val repository: UserPreferencesRepository,
    private val onSignedOut: () -> Unit = {}
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(repository, onSignedOut) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
