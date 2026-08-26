package com.smartreminder.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.domain.model.preferences.UserGoal
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalTime

/**
 * Manages onboarding state and persists via [UserPreferencesSyncCoordinator].
 *
 * Key design decisions:
 * - **Hydrates from repository on init**: If user previously completed onboarding then reset,
 *   they see their previously saved rhythm/goals, not hardcoded defaults.
 * - **No Completed event**: Navigation is purely reactive via DataStore Flow → AppViewModel.
 *   completeOnboarding() writes to Cloud & DataStore → preferences Flow emits → AppState.Main.
 * - **Error handling**: Any Exception on cloud or local save is caught and exposed via [OnboardingUiState.saveError].
 */
class OnboardingViewModel(
    private val repository: UserPreferencesRepository,
    private val syncCoordinator: UserPreferencesSyncCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        hydrateFromRepository()
    }

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.UpdateWakeTime -> updateWakeTime(action.time)
            is OnboardingAction.UpdateSleepTime -> updateSleepTime(action.time)
            is OnboardingAction.ToggleGoal -> toggleGoal(action.goal)
            is OnboardingAction.OpenTimePicker -> openTimePicker(action.target)
            is OnboardingAction.DismissTimePicker -> dismissTimePicker()
            is OnboardingAction.NavigateToStep -> navigateToStep(action.step)
            is OnboardingAction.Complete -> completeOnboarding()
            is OnboardingAction.Skip -> completeOnboarding()
        }
    }

    /**
     * Load existing preferences snapshot on init.
     * If DataStore has data (e.g. reset onboarding), those values are used.
     * If DataStore is empty, UserPreferences defaults apply (07:00 / 23:30 / TASKS+PLANNING).
     */
    private fun hydrateFromRepository() {
        viewModelScope.launch {
            try {
                val saved = repository.preferences.first()
                _uiState.update { state ->
                    state.copy(
                        wakeUpTime = saved.wakeUpTime,
                        sleepTime = saved.sleepTime,
                        selectedGoals = saved.goals
                    )
                }
            } catch (_: IOException) {
                // Keep hardcoded defaults — DataStore unavailable on first read
            }
        }
    }

    private fun updateWakeTime(time: LocalTime) {
        _uiState.update { it.copy(wakeUpTime = time, activeTimePicker = null) }
    }

    private fun updateSleepTime(time: LocalTime) {
        _uiState.update { it.copy(sleepTime = time, activeTimePicker = null) }
    }

    private fun toggleGoal(goal: UserGoal) {
        _uiState.update { state ->
            val updated = if (goal in state.selectedGoals) {
                state.selectedGoals - goal
            } else {
                state.selectedGoals + goal
            }
            state.copy(selectedGoals = updated)
        }
    }

    private fun openTimePicker(target: TimePickerTarget) {
        _uiState.update { it.copy(activeTimePicker = target) }
    }

    private fun dismissTimePicker() {
        _uiState.update { it.copy(activeTimePicker = null) }
    }

    private fun navigateToStep(step: OnboardingStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    /**
     * Atomically persists current snapshot via [UserPreferencesSyncCoordinator].
     * If authenticated, writes to Cloud first, then to DataStore.
     * If unauthenticated, writes to DataStore directly.
     */
    private fun completeOnboarding() {
        val snapshot = _uiState.value
        if (snapshot.isSaving) return

        _uiState.update { it.copy(isSaving = true, saveError = false) }

        viewModelScope.launch {
            try {
                syncCoordinator.completeOnboarding(
                    wakeUpTime = snapshot.wakeUpTime,
                    sleepTime = snapshot.sleepTime,
                    goals = snapshot.selectedGoals
                )
                _uiState.update { it.copy(isSaving = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = true) }
            }
        }
    }
}
