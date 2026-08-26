package com.smartreminder.ui.profile

import com.smartreminder.domain.model.preferences.ThemeMode
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
