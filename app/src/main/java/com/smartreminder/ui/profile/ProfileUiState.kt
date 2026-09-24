package com.smartreminder.ui.profile

import com.smartreminder.domain.model.preferences.ThemeMode
import java.io.File
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
        val routineRemindersEnabled: Boolean = true,
        val taskRemindersEnabled: Boolean = true,
        val morningBriefingEnabled: Boolean = true,
        val quietHoursEnabled: Boolean = true,
        val showSignOutDialog: Boolean = false,
        val showWakeTimePicker: Boolean = false,
        val showSleepTimePicker: Boolean = false,
        val showThemePicker: Boolean = false,
        val isSyncing: Boolean = false,
        val syncMessage: String? = null,
        val showEditProfileDialog: Boolean = false,
        val isSavingProfile: Boolean = false,
        val showNotificationSettings: Boolean = false,
        val showDataBackup: Boolean = false,
        val showPrivacySettings: Boolean = false,
        val showDeleteAccountDialog: Boolean = false,
        val backupExportedFile: File? = null,
        val errorMessage: String? = null
    ) : ProfileUiState
}
