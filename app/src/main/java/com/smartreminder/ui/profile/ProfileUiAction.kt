package com.smartreminder.ui.profile

import com.smartreminder.domain.model.preferences.ThemeMode
import java.time.LocalTime

enum class NotificationSettingKey {
    ROUTINE,
    TASKS,
    MORNING_BRIEFING,
    QUIET_HOURS
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
    data object OpenThemePicker : ProfileUiAction
    data object DismissThemePicker : ProfileUiAction
    data class UpdateThemeMode(val mode: ThemeMode) : ProfileUiAction
    data object RequestForceSync : ProfileUiAction
    data object DismissSyncMessage : ProfileUiAction
    data object OpenEditProfile : ProfileUiAction
    data object DismissEditProfile : ProfileUiAction
    data class SaveProfile(val displayName: String, val avatarUrl: String?) : ProfileUiAction

    // Notification Preferences
    data object OpenNotificationSettings : ProfileUiAction
    data object DismissNotificationSettings : ProfileUiAction
    data class ToggleNotificationSetting(val key: NotificationSettingKey, val enabled: Boolean) : ProfileUiAction

    // Data Backup
    data object OpenDataBackup : ProfileUiAction
    data object DismissDataBackup : ProfileUiAction
    data object ExportBackup : ProfileUiAction

    // Privacy & Data Control
    data object OpenPrivacySettings : ProfileUiAction
    data object DismissPrivacySettings : ProfileUiAction
    data object ClearCache : ProfileUiAction
    data object RequestDeleteAccount : ProfileUiAction
    data object ConfirmDeleteAccount : ProfileUiAction
    data object DismissDeleteAccount : ProfileUiAction

    data object DismissError : ProfileUiAction
}
