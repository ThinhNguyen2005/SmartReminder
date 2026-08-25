package com.smartreminder.ui.profile

import com.smartreminder.domain.model.ThemeMode
import java.time.LocalTime

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Loaded(
        val displayName: String?,
        val email: String?,
        val avatarUrl: String?,
        val wakeUpTime: LocalTime,
        val sleepTime: LocalTime,
        val themeMode: ThemeMode,
        val showSignOutDialog: Boolean = false,
        val showWakeTimePicker: Boolean = false,
        val showSleepTimePicker: Boolean = false,
        val errorMessage: String? = null
    ) : ProfileUiState
}

sealed interface ProfileUiAction {
    data object RequestSignOut : ProfileUiAction
    data object ConfirmSignOut : ProfileUiAction
    data object DismissSignOutDialog : ProfileUiAction
    data object OpenWakeTimePicker : ProfileUiAction
    data object DismissWakeTimePicker : ProfileUiAction
    data class UpdateWakeTime(val time: LocalTime) : ProfileUiAction
    data object OpenSleepTimePicker : ProfileUiAction
    data object DismissSleepTimePicker : ProfileUiAction
    data class UpdateSleepTime(val time: LocalTime) : ProfileUiAction
    data object DismissError : ProfileUiAction
}
