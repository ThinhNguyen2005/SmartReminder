package com.smartreminder.ui.onboarding

import com.smartreminder.domain.model.preferences.UserGoal
import java.time.LocalTime

/**
 * All user actions from OnboardingScreen → OnboardingViewModel.
 * Sealed interface ensures exhaustive handling.
 */
sealed interface OnboardingAction {
    data class UpdateWakeTime(val time: LocalTime) : OnboardingAction
    data class UpdateSleepTime(val time: LocalTime) : OnboardingAction
    data class ToggleGoal(val goal: UserGoal) : OnboardingAction
    data class OpenTimePicker(val target: TimePickerTarget) : OnboardingAction
    data object DismissTimePicker : OnboardingAction
    data class NavigateToStep(val step: OnboardingStep) : OnboardingAction
    data object Complete : OnboardingAction
    data object Skip : OnboardingAction
}
