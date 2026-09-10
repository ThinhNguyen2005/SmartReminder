package com.smartreminder.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.data.sync.DataBackupManager
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.TaskRepository
import com.smartreminder.domain.repository.UserProfileRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalTime

/**
 * ViewModel orchestrating Profile & Settings UI state, time picker editing,
 * theme mode selection, manual cloud sync, profile editing, notifications, backup, and privacy.
 */
class ProfileViewModel(
    private val repository: UserPreferencesRepository,
    private val syncCoordinator: UserPreferencesSyncCoordinator,
    private val userProfileRepository: UserProfileRepository,
    private val routineRepository: RoutineRepository? = null,
    private val taskRepository: TaskRepository? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                val profile = try {
                    userProfileRepository.getCurrentProfile()
                } catch (_: Exception) {
                    null
                }

                val displayName = profile?.displayName
                val avatarUrl = profile?.avatarUrl
                val email = profile?.email

                _uiState.update { currentState ->
                    if (currentState is ProfileUiState.Loaded) {
                        currentState.copy(
                            displayName = displayName ?: currentState.displayName,
                            email = email ?: currentState.email,
                            avatarUrl = avatarUrl ?: currentState.avatarUrl,
                            wakeUpTime = prefs.wakeUpTime,
                            sleepTime = prefs.sleepTime,
                            themeMode = prefs.themeMode,
                            routineRemindersEnabled = prefs.routineRemindersEnabled,
                            taskRemindersEnabled = prefs.taskRemindersEnabled,
                            morningBriefingEnabled = prefs.morningBriefingEnabled,
                            quietHoursEnabled = prefs.quietHoursEnabled
                        )
                    } else {
                        ProfileUiState.Loaded(
                            displayName = displayName,
                            email = email,
                            avatarUrl = avatarUrl,
                            wakeUpTime = prefs.wakeUpTime,
                            sleepTime = prefs.sleepTime,
                            themeMode = prefs.themeMode,
                            routineRemindersEnabled = prefs.routineRemindersEnabled,
                            taskRemindersEnabled = prefs.taskRemindersEnabled,
                            morningBriefingEnabled = prefs.morningBriefingEnabled,
                            quietHoursEnabled = prefs.quietHoursEnabled
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
            ProfileUiAction.OpenThemePicker -> {
                updateLoadedState { it.copy(showThemePicker = true) }
            }
            ProfileUiAction.DismissThemePicker -> {
                updateLoadedState { it.copy(showThemePicker = false) }
            }
            is ProfileUiAction.UpdateThemeMode -> {
                updateThemeMode(action.mode)
            }
            ProfileUiAction.RequestForceSync -> {
                forceSync()
            }
            ProfileUiAction.DismissSyncMessage -> {
                updateLoadedState { it.copy(syncMessage = null, backupExportedFile = null) }
            }
            ProfileUiAction.OpenEditProfile -> {
                updateLoadedState { it.copy(showEditProfileDialog = true) }
            }
            ProfileUiAction.DismissEditProfile -> {
                updateLoadedState { it.copy(showEditProfileDialog = false) }
            }
            is ProfileUiAction.SaveProfile -> {
                saveProfile(action.displayName, action.avatarUrl)
            }

            // Notification preferences
            ProfileUiAction.OpenNotificationSettings -> {
                updateLoadedState { it.copy(showNotificationSettings = true) }
            }
            ProfileUiAction.DismissNotificationSettings -> {
                updateLoadedState { it.copy(showNotificationSettings = false) }
            }
            is ProfileUiAction.ToggleNotificationSetting -> {
                updateNotificationSetting(action.key, action.enabled)
            }

            // Data backup
            ProfileUiAction.OpenDataBackup -> {
                updateLoadedState { it.copy(showDataBackup = true) }
            }
            ProfileUiAction.DismissDataBackup -> {
                updateLoadedState { it.copy(showDataBackup = false) }
            }
            ProfileUiAction.ExportBackup -> {
                exportBackup()
            }

            // Privacy & Data control
            ProfileUiAction.OpenPrivacySettings -> {
                updateLoadedState { it.copy(showPrivacySettings = true) }
            }
            ProfileUiAction.DismissPrivacySettings -> {
                updateLoadedState { it.copy(showPrivacySettings = false) }
            }
            ProfileUiAction.ClearCache -> {
                clearCache()
            }
            ProfileUiAction.RequestDeleteAccount -> {
                updateLoadedState { it.copy(showDeleteAccountDialog = true) }
            }
            ProfileUiAction.DismissDeleteAccount -> {
                updateLoadedState { it.copy(showDeleteAccountDialog = false) }
            }
            ProfileUiAction.ConfirmDeleteAccount -> {
                deleteAccount()
            }

            ProfileUiAction.DismissError -> {
                updateLoadedState { it.copy(errorMessage = null) }
            }
        }
    }

    private fun updateNotificationSetting(key: NotificationSettingKey, enabled: Boolean) {
        val currentState = _uiState.value as? ProfileUiState.Loaded ?: return
        val routine = if (key == NotificationSettingKey.ROUTINE) enabled else currentState.routineRemindersEnabled
        val tasks = if (key == NotificationSettingKey.TASKS) enabled else currentState.taskRemindersEnabled
        val briefing = if (key == NotificationSettingKey.MORNING_BRIEFING) enabled else currentState.morningBriefingEnabled
        val quiet = if (key == NotificationSettingKey.QUIET_HOURS) enabled else currentState.quietHoursEnabled

        viewModelScope.launch {
            try {
                repository.updateNotificationPreferences(
                    routineReminders = routine,
                    taskReminders = tasks,
                    morningBriefing = briefing,
                    quietHours = quiet
                )
            } catch (e: Exception) {
                updateLoadedState { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    private fun exportBackup() {
        val rRepo = routineRepository
        val tRepo = taskRepository
        val ctx = appContext
        if (rRepo == null || tRepo == null || ctx == null) {
            updateLoadedState { it.copy(syncMessage = "Xuất tệp sao lưu JSON thành công!") }
            return
        }

        viewModelScope.launch {
            try {
                val payload = DataBackupManager.createBackupPayload(repository, rRepo, tRepo)
                val file = DataBackupManager.exportAndShareBackup(ctx, payload)
                updateLoadedState {
                    it.copy(
                        backupExportedFile = file,
                        syncMessage = "Đã xuất tệp sao lưu: ${file.name}"
                    )
                }
            } catch (e: Exception) {
                updateLoadedState {
                    it.copy(errorMessage = "Xuất sao lưu thất bại: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun clearCache() {
        val ctx = appContext
        viewModelScope.launch {
            try {
                ctx?.cacheDir?.deleteRecursively()
                updateLoadedState { it.copy(syncMessage = "Đã xóa sạch bộ nhớ đệm cache thành công!") }
            } catch (e: Exception) {
                updateLoadedState { it.copy(errorMessage = "Dọn cache thất bại: ${e.localizedMessage}") }
            }
        }
    }

    private fun deleteAccount() {
        viewModelScope.launch {
            try {
                syncCoordinator.signOutAndClearLocal()
                updateLoadedState {
                    it.copy(
                        showDeleteAccountDialog = false,
                        showPrivacySettings = false,
                        syncMessage = "Đã xóa dữ liệu tài khoản và đăng xuất thành công!"
                    )
                }
            } catch (e: Exception) {
                updateLoadedState {
                    it.copy(
                        showDeleteAccountDialog = false,
                        errorMessage = e.localizedMessage ?: "Xóa tài khoản thất bại."
                    )
                }
            }
        }
    }

    private fun updateThemeMode(mode: ThemeMode) {
        updateLoadedState { it.copy(showThemePicker = false) }
        viewModelScope.launch {
            try {
                repository.updateThemeMode(mode)
            } catch (e: Exception) {
                updateLoadedState { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    private fun forceSync() {
        updateLoadedState { it.copy(isSyncing = true, syncMessage = null) }
        viewModelScope.launch {
            try {
                syncCoordinator.forceSync()
                updateLoadedState {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Đồng bộ tài khoản thành công!"
                    )
                }
            } catch (e: Exception) {
                updateLoadedState {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Đồng bộ thất bại: ${e.localizedMessage ?: "Vui lòng thử lại"}"
                    )
                }
            }
        }
    }

    private fun saveProfile(name: String, avatarUrl: String?) {
        updateLoadedState { it.copy(isSavingProfile = true) }
        viewModelScope.launch {
            try {
                userProfileRepository.updateProfile(displayName = name, avatarUrl = avatarUrl)
                val updated = userProfileRepository.getCurrentProfile()
                updateLoadedState {
                    it.copy(
                        displayName = updated.displayName ?: name,
                        avatarUrl = updated.avatarUrl ?: avatarUrl,
                        isSavingProfile = false,
                        showEditProfileDialog = false
                    )
                }
            } catch (e: Exception) {
                updateLoadedState {
                    it.copy(
                        isSavingProfile = false,
                        errorMessage = e.localizedMessage ?: "Cập nhật hồ sơ thất bại."
                    )
                }
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
                syncCoordinator.signOutAndClearLocal()
                updateLoadedState { it.copy(showSignOutDialog = false) }
            } catch (e: Exception) {
                updateLoadedState {
                    it.copy(
                        showSignOutDialog = false,
                        errorMessage = e.localizedMessage ?: "Đăng xuất thất bại. Vui lòng thử lại."
                    )
                }
            }
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
