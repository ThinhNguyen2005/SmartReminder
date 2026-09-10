package com.smartreminder.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SecurityUpdateGood
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartreminder.R
import com.smartreminder.data.local.storage.AvatarImageStorage
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.ui.onboarding.TimePickerTarget
import com.smartreminder.ui.onboarding.components.CueTimePickerBottomSheet
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.SmartReminderTheme
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onAction: (ProfileUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Loaded) {
            uiState.syncMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                onAction(ProfileUiAction.DismissSyncMessage)
            }
            uiState.errorMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                onAction(ProfileUiAction.DismissError)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CueTheme.colors.background)
    ) {
        when (uiState) {
            ProfileUiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        color = CueTheme.colors.accent
                    )
                }
            }

            is ProfileUiState.Loaded -> {
                val context = LocalContext.current
                val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
                val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"
                val timeFormatter = remember(is24Hour) {
                    DateTimeFormatter.ofPattern(timePattern, Locale.getDefault())
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top App Bar
                    ProfileTopAppBar(
                        avatarUrl = uiState.avatarUrl
                    )

                    Spacer(modifier = Modifier.height(CueSpacing.Sm))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CueSpacing.Xl)
                    ) {
                        // 1. Profile Header (Avatar, Name, Email, Edit Button)
                        ProfileHeaderSection(
                            displayName = uiState.displayName,
                            email = uiState.email,
                            avatarUrl = uiState.avatarUrl,
                            onEditClick = { onAction(ProfileUiAction.OpenEditProfile) }
                        )

                        Spacer(modifier = Modifier.height(CueSpacing.Xl))

                        // 2. Daily Routine Context Card (Wake Time / Sleep Time)
                        DailyRoutineSection(
                            wakeUpTime = uiState.wakeUpTime,
                            sleepTime = uiState.sleepTime,
                            timeFormatter = timeFormatter,
                            onWakeTimeClick = { onAction(ProfileUiAction.OpenWakeTimePicker) },
                            onSleepTimeClick = { onAction(ProfileUiAction.OpenSleepTimePicker) }
                        )

                        Spacer(modifier = Modifier.height(CueSpacing.Xl))

                        // 3. Preferences Section
                        Text(
                            text = stringResource(R.string.profile_section_preferences),
                            style = MaterialTheme.typography.labelSmall,
                            color = CueTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = CueSpacing.Xs)
                        )

                        Spacer(modifier = Modifier.height(CueSpacing.Sm))

                        Surface(
                            color = CueTheme.colors.surfaceSubtle,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                SettingsRow(
                                    icon = Icons.Outlined.Notifications,
                                    label = stringResource(R.string.profile_notification_preferences),
                                    onClick = { onAction(ProfileUiAction.OpenNotificationSettings) }
                                )
                                HorizontalDivider(
                                    color = CueTheme.colors.border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = CueSpacing.Lg)
                                )
                                SettingsRow(
                                    icon = Icons.Outlined.Sync,
                                    label = stringResource(R.string.profile_account_sync),
                                    onClick = {
                                        if (!uiState.isSyncing) {
                                            onAction(ProfileUiAction.RequestForceSync)
                                        }
                                    },
                                    trailing = if (uiState.isSyncing) {
                                        {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = CueTheme.colors.accent,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    } else null
                                )
                                HorizontalDivider(
                                    color = CueTheme.colors.border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = CueSpacing.Lg)
                                )
                                SettingsRow(
                                    icon = Icons.Outlined.CloudUpload,
                                    label = stringResource(R.string.profile_data_backup),
                                    onClick = { onAction(ProfileUiAction.OpenDataBackup) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(CueSpacing.Xl))

                        // 4. App Settings Section
                        Text(
                            text = stringResource(R.string.profile_section_app_settings),
                            style = MaterialTheme.typography.labelSmall,
                            color = CueTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = CueSpacing.Xs)
                        )

                        Spacer(modifier = Modifier.height(CueSpacing.Sm))

                        Surface(
                            color = CueTheme.colors.surfaceSubtle,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                SettingsRow(
                                    icon = Icons.Outlined.SmartToy,
                                    label = stringResource(R.string.profile_ai_preferences),
                                    onClick = { /* Placeholder action */ }
                                )
                                HorizontalDivider(
                                    color = CueTheme.colors.border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = CueSpacing.Lg)
                                )
                                val themeText = when (uiState.themeMode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.profile_theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.profile_theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.profile_theme_dark)
                                }
                                SettingsRow(
                                    icon = Icons.Outlined.Palette,
                                    label = stringResource(R.string.profile_appearance),
                                    onClick = { onAction(ProfileUiAction.OpenThemePicker) },
                                    trailing = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
                                        ) {
                                            Text(
                                                text = themeText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CueTheme.colors.textSecondary
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                contentDescription = null,
                                                tint = CueTheme.colors.borderStrong,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                                HorizontalDivider(
                                    color = CueTheme.colors.border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = CueSpacing.Lg)
                                )
                                SettingsRow(
                                    icon = Icons.Outlined.Lock,
                                    label = stringResource(R.string.profile_privacy),
                                    onClick = { onAction(ProfileUiAction.OpenPrivacySettings) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(CueSpacing.Xl))

                        // 5. Sign Out Button
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CueTheme.colors.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onAction(ProfileUiAction.RequestSignOut) }
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = CueTheme.colors.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(CueSpacing.Sm))
                                Text(
                                    text = stringResource(R.string.profile_sign_out),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CueTheme.colors.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(CueSpacing.Xxxl))
                    }
                }

                // Theme Mode Bottom Sheet (Replaces ugly dialog)
                if (uiState.showThemePicker) {
                    ThemeModeBottomSheet(
                        currentTheme = uiState.themeMode,
                        onSelectTheme = { onAction(ProfileUiAction.UpdateThemeMode(it)) },
                        onDismiss = { onAction(ProfileUiAction.DismissThemePicker) }
                    )
                }

                // Edit Profile Bottom Sheet (Replaces ugly dialog)
                if (uiState.showEditProfileDialog) {
                    EditProfileBottomSheet(
                        currentDisplayName = uiState.displayName.orEmpty(),
                        currentAvatarUrl = uiState.avatarUrl.orEmpty(),
                        isSaving = uiState.isSavingProfile,
                        onSave = { name, avatar ->
                            onAction(ProfileUiAction.SaveProfile(name, avatar))
                        },
                        onDismiss = { onAction(ProfileUiAction.DismissEditProfile) }
                    )
                }

                // Notification Settings Bottom Sheet
                if (uiState.showNotificationSettings) {
                    NotificationSettingsBottomSheet(
                        routineEnabled = uiState.routineRemindersEnabled,
                        tasksEnabled = uiState.taskRemindersEnabled,
                        morningBriefingEnabled = uiState.morningBriefingEnabled,
                        quietHoursEnabled = uiState.quietHoursEnabled,
                        onToggle = { key, enabled ->
                            onAction(ProfileUiAction.ToggleNotificationSetting(key, enabled))
                        },
                        onDismiss = { onAction(ProfileUiAction.DismissNotificationSettings) }
                    )
                }

                // Data Backup Bottom Sheet
                if (uiState.showDataBackup) {
                    DataBackupBottomSheet(
                        isSyncing = uiState.isSyncing,
                        onSyncNow = { onAction(ProfileUiAction.RequestForceSync) },
                        onExportBackup = { onAction(ProfileUiAction.ExportBackup) },
                        onDismiss = { onAction(ProfileUiAction.DismissDataBackup) }
                    )
                }

                // Privacy & Data Control Bottom Sheet
                if (uiState.showPrivacySettings) {
                    PrivacySettingsBottomSheet(
                        showDeleteDialog = uiState.showDeleteAccountDialog,
                        onClearCache = { onAction(ProfileUiAction.ClearCache) },
                        onRequestDeleteAccount = { onAction(ProfileUiAction.RequestDeleteAccount) },
                        onConfirmDeleteAccount = { onAction(ProfileUiAction.ConfirmDeleteAccount) },
                        onDismissDeleteDialog = { onAction(ProfileUiAction.DismissDeleteAccount) },
                        onDismiss = { onAction(ProfileUiAction.DismissPrivacySettings) }
                    )
                }

                // Sign Out Confirmation Dialog
                if (uiState.showSignOutDialog) {
                    AlertDialog(
                        onDismissRequest = { onAction(ProfileUiAction.DismissSignOutDialog) },
                        title = {
                            Text(
                                text = stringResource(R.string.profile_sign_out_confirm_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = CueTheme.colors.textPrimary
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.profile_sign_out_confirm_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CueTheme.colors.textSecondary
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { onAction(ProfileUiAction.ConfirmSignOut) }
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_sign_out),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CueTheme.colors.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { onAction(ProfileUiAction.DismissSignOutDialog) }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CueTheme.colors.textSecondary
                                )
                            }
                        },
                        containerColor = CueTheme.colors.surface
                    )
                }

                // TimePicker Bottom Sheet for Wake Time
                if (uiState.showWakeTimePicker) {
                    CueTimePickerBottomSheet(
                        target = TimePickerTarget.WAKE_UP,
                        currentTime = uiState.wakeUpTime,
                        onTimeSelected = { onAction(ProfileUiAction.UpdateWakeTime(it)) },
                        onDismiss = { onAction(ProfileUiAction.DismissWakeTimePicker) }
                    )
                }

                // TimePicker Bottom Sheet for Sleep Time
                if (uiState.showSleepTimePicker) {
                    CueTimePickerBottomSheet(
                        target = TimePickerTarget.SLEEP,
                        currentTime = uiState.sleepTime,
                        onTimeSelected = { onAction(ProfileUiAction.UpdateSleepTime(it)) },
                        onDismiss = { onAction(ProfileUiAction.DismissSleepTimePicker) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CueSpacing.Xl)
        )
    }
}

/**
 * Modern Cue BottomSheet for selecting application Theme Mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeBottomSheet(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CueTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = CueSpacing.Md, bottom = CueSpacing.Sm)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CueTheme.colors.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Xl)
                .padding(bottom = CueSpacing.Xxl)
        ) {
            Text(
                text = stringResource(R.string.profile_theme_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Lg))

            ThemeOptionCard(
                title = stringResource(R.string.profile_theme_system),
                subtitle = stringResource(R.string.profile_theme_system_desc),
                icon = Icons.Outlined.BrightnessAuto,
                isSelected = currentTheme == ThemeMode.SYSTEM,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTheme(ThemeMode.SYSTEM)
                }
            )

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            ThemeOptionCard(
                title = stringResource(R.string.profile_theme_light),
                subtitle = stringResource(R.string.profile_theme_light_desc),
                icon = Icons.Outlined.LightMode,
                isSelected = currentTheme == ThemeMode.LIGHT,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTheme(ThemeMode.LIGHT)
                }
            )

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            ThemeOptionCard(
                title = stringResource(R.string.profile_theme_dark),
                subtitle = stringResource(R.string.profile_theme_dark_desc),
                icon = Icons.Outlined.DarkMode,
                isSelected = currentTheme == ThemeMode.DARK,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTheme(ThemeMode.DARK)
                }
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CueTheme.colors.accent else CueTheme.colors.border
    val backgroundColor = if (isSelected) CueTheme.colors.accentContainer.copy(alpha = 0.35f) else CueTheme.colors.surfaceSubtle

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(CueSpacing.Lg)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) CueTheme.colors.accent else CueTheme.colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) CueTheme.colors.surface else CueTheme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(CueSpacing.Lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CueTheme.colors.textSecondary
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CueTheme.colors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Modern Cue BottomSheet for Editing Profile with Gallery Photo Picker and live compression
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileBottomSheet(
    currentDisplayName: String,
    currentAvatarUrl: String,
    isSaving: Boolean,
    onSave: (displayName: String, avatarUrl: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var name by remember(currentDisplayName) { mutableStateOf(currentDisplayName) }
    var avatarUrl by remember(currentAvatarUrl) { mutableStateOf(currentAvatarUrl) }
    var isProcessingImage by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessingImage = true
                try {
                    val compressedUrl = AvatarImageStorage.saveAndCompressAvatar(context, uri)
                    avatarUrl = compressedUrl
                } catch (_: Exception) {
                    avatarUrl = uri.toString()
                } finally {
                    isProcessingImage = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSaving && !isProcessingImage) onDismiss()
        },
        sheetState = sheetState,
        containerColor = CueTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = CueSpacing.Md, bottom = CueSpacing.Sm)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CueTheme.colors.borderStrong)
            )
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Xl)
                .padding(bottom = CueSpacing.Xxl)
                .imePadding()
        ) {
            Text(
                text = stringResource(R.string.profile_edit_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // Avatar with camera badge
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(CueTheme.colors.surfaceSubtle)
                        .border(2.5.dp, CueTheme.colors.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.profile_avatar_description),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = CueTheme.colors.accentStrong,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    if (isProcessingImage) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CueTheme.colors.surface.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = CueTheme.colors.accent,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                }

                // Camera Badge Button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CueTheme.colors.accent)
                        .border(2.dp, CueTheme.colors.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = stringResource(R.string.profile_edit_change_photo),
                        tint = CueTheme.colors.surface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Sm))

            Text(
                text = stringResource(R.string.profile_edit_change_photo),
                style = MaterialTheme.typography.bodySmall,
                color = CueTheme.colors.accent
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // Display Name Input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_edit_name_label)) },
                placeholder = { Text(stringResource(R.string.profile_edit_name_placeholder)) },
                singleLine = true,
                enabled = !isSaving && !isProcessingImage,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (name.isNotEmpty()) {
                        IconButton(onClick = { name = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear text",
                                tint = CueTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CueTheme.colors.accent,
                    unfocusedBorderColor = CueTheme.colors.border,
                    focusedContainerColor = CueTheme.colors.surfaceSubtle.copy(alpha = 0.5f),
                    unfocusedContainerColor = CueTheme.colors.surfaceSubtle.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xxl))

            // Save Changes Button
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        avatarUrl.trim().ifEmpty { null }
                    )
                },
                enabled = !isSaving && !isProcessingImage && name.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CueTheme.colors.accent,
                    disabledContainerColor = CueTheme.colors.accent.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = CueTheme.colors.surface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.profile_edit_save),
                        style = MaterialTheme.typography.labelLarge,
                        color = CueTheme.colors.surface
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTopAppBar(
    avatarUrl: String?,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = CueSpacing.Xl)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CueTheme.colors.surfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.profile_avatar_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = CueTheme.colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )
        }

        IconButton(
            onClick = { /* Notification view placeholder */ },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.profile_notifications_description),
                tint = CueTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    displayName: String?,
    email: String?,
    avatarUrl: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nameText = displayName?.ifBlank { null } ?: stringResource(R.string.profile_guest_name)
    val emailText = email?.ifBlank { null } ?: stringResource(R.string.profile_guest_email)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Large Avatar
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(CueTheme.colors.surfaceSubtle)
                .border(2.dp, CueTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.profile_avatar_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = CueTheme.colors.accentStrong,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(CueSpacing.Md))

        // Name
        Text(
            text = nameText,
            style = MaterialTheme.typography.titleLarge,
            color = CueTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(CueSpacing.Xs))

        // Email
        Text(
            text = emailText,
            style = MaterialTheme.typography.bodyMedium,
            color = CueTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(CueSpacing.Md))

        // Edit Profile Button
        Surface(
            shape = CircleShape,
            color = CueTheme.colors.surfaceSubtle,
            modifier = Modifier
                .height(36.dp)
                .clickable(role = Role.Button, onClick = onEditClick)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = CueSpacing.Xl)
            ) {
                Text(
                    text = stringResource(R.string.profile_edit),
                    style = MaterialTheme.typography.labelMedium,
                    color = CueTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun DailyRoutineSection(
    wakeUpTime: LocalTime,
    sleepTime: LocalTime,
    timeFormatter: DateTimeFormatter,
    onWakeTimeClick: () -> Unit,
    onSleepTimeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CueTheme.colors.surfaceSubtle,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CueSpacing.Lg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = CueTheme.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.profile_routine_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = CueTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            Row(
                horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Wake Time Card
                RoutineTimeCard(
                    title = stringResource(R.string.profile_wake_time),
                    timeFormatted = wakeUpTime.format(timeFormatter),
                    icon = Icons.Outlined.WbSunny,
                    onClick = onWakeTimeClick,
                    modifier = Modifier.weight(1f)
                )

                // Sleep Time Card
                RoutineTimeCard(
                    title = stringResource(R.string.profile_sleep_time),
                    timeFormatted = sleepTime.format(timeFormatter),
                    icon = Icons.Outlined.Bedtime,
                    onClick = onSleepTimeClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RoutineTimeCard(
    title: String,
    timeFormatted: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CueTheme.colors.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CueTheme.colors.border),
        modifier = modifier
            .height(72.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $timeFormatted"
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CueSpacing.Md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CueTheme.colors.accentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CueTheme.colors.accentStrong,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(CueSpacing.Md))

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = CueTheme.colors.textSecondary
                )
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CueSpacing.Lg)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Lg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CueTheme.colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = CueTheme.colors.textPrimary
            )
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = CueTheme.colors.borderStrong,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Notification Settings BottomSheet — toggle switches for each notification category
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsBottomSheet(
    routineEnabled: Boolean,
    tasksEnabled: Boolean,
    morningBriefingEnabled: Boolean,
    quietHoursEnabled: Boolean,
    onToggle: (NotificationSettingKey, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CueTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = CueSpacing.Md, bottom = CueSpacing.Sm)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CueTheme.colors.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Xl)
                .padding(bottom = CueSpacing.Xxl)
        ) {
            Text(
                text = stringResource(R.string.profile_notification_settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Lg))

            NotificationToggleCard(
                title = stringResource(R.string.profile_notif_routine_title),
                description = stringResource(R.string.profile_notif_routine_desc),
                icon = Icons.Outlined.WbSunny,
                checked = routineEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(NotificationSettingKey.ROUTINE, it)
                }
            )

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            NotificationToggleCard(
                title = stringResource(R.string.profile_notif_tasks_title),
                description = stringResource(R.string.profile_notif_tasks_desc),
                icon = Icons.Outlined.Notifications,
                checked = tasksEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(NotificationSettingKey.TASKS, it)
                }
            )

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            NotificationToggleCard(
                title = stringResource(R.string.profile_notif_morning_title),
                description = stringResource(R.string.profile_notif_morning_desc),
                icon = Icons.Outlined.AutoAwesome,
                checked = morningBriefingEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(NotificationSettingKey.MORNING_BRIEFING, it)
                }
            )

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            NotificationToggleCard(
                title = stringResource(R.string.profile_notif_quiet_title),
                description = stringResource(R.string.profile_notif_quiet_desc),
                icon = Icons.Outlined.Bedtime,
                checked = quietHoursEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(NotificationSettingKey.QUIET_HOURS, it)
                }
            )
        }
    }
}

@Composable
private fun NotificationToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = CueTheme.colors.surfaceSubtle,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked) CueTheme.colors.accentContainer
                        else CueTheme.colors.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) CueTheme.colors.accent else CueTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(CueSpacing.Md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CueTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = CueTheme.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(CueSpacing.Sm))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CueTheme.colors.surface,
                    checkedTrackColor = CueTheme.colors.accent,
                    uncheckedThumbColor = CueTheme.colors.borderStrong,
                    uncheckedTrackColor = CueTheme.colors.surface,
                    uncheckedBorderColor = CueTheme.colors.border
                )
            )
        }
    }
}

/**
 * Data Backup BottomSheet — cloud sync + offline JSON export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataBackupBottomSheet(
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onExportBackup: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CueTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = CueSpacing.Md, bottom = CueSpacing.Sm)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CueTheme.colors.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Xl)
                .padding(bottom = CueSpacing.Xxl)
        ) {
            Text(
                text = stringResource(R.string.profile_backup_title),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Lg))

            // Cloud Sync Card
            Surface(
                color = CueTheme.colors.surfaceSubtle,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CueSpacing.Lg)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CueTheme.colors.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = null,
                                tint = CueTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_backup_cloud_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = CueTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.profile_backup_cloud_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = CueTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CueSpacing.Md))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSyncNow()
                        },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CueTheme.colors.accent,
                            disabledContainerColor = CueTheme.colors.accent.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = CueTheme.colors.surface
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(CueSpacing.Sm))
                            Text(
                                text = stringResource(R.string.profile_backup_sync_now),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            // Export Backup Card
            Surface(
                color = CueTheme.colors.surfaceSubtle,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CueSpacing.Lg)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CueTheme.colors.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = null,
                                tint = CueTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_backup_export_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = CueTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.profile_backup_export_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = CueTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CueSpacing.Md))

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExportBackup()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CueTheme.colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = CueTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(CueSpacing.Sm))
                        Text(
                            text = stringResource(R.string.profile_backup_export_btn),
                            style = MaterialTheme.typography.labelLarge,
                            color = CueTheme.colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Privacy & Data Control BottomSheet — permissions, cache, danger zone
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySettingsBottomSheet(
    showDeleteDialog: Boolean,
    onClearCache: () -> Unit,
    onRequestDeleteAccount: () -> Unit,
    onConfirmDeleteAccount: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CueTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = CueSpacing.Md, bottom = CueSpacing.Sm)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CueTheme.colors.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Xl)
                .padding(bottom = CueSpacing.Xxl)
        ) {
            Text(
                text = stringResource(R.string.profile_privacy_title),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Lg))

            // Device Permissions Card
            Surface(
                color = CueTheme.colors.surfaceSubtle,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CueSpacing.Lg)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CueTheme.colors.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SecurityUpdateGood,
                                contentDescription = null,
                                tint = CueTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_privacy_permissions_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = CueTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.profile_privacy_permissions_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = CueTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CueSpacing.Md))

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CueTheme.colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_privacy_open_settings),
                            style = MaterialTheme.typography.labelLarge,
                            color = CueTheme.colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Md))

            // Storage & Cache Card
            Surface(
                color = CueTheme.colors.surfaceSubtle,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CueSpacing.Lg)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CueTheme.colors.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storage,
                                contentDescription = null,
                                tint = CueTheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_privacy_cache_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = CueTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.profile_privacy_cache_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = CueTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CueSpacing.Md))

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClearCache()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CueTheme.colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_privacy_clear_cache_btn),
                            style = MaterialTheme.typography.labelLarge,
                            color = CueTheme.colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // Danger Zone
            Text(
                text = stringResource(R.string.profile_privacy_danger_title),
                style = MaterialTheme.typography.labelSmall,
                color = CueTheme.colors.error,
                modifier = Modifier.padding(horizontal = CueSpacing.Xs)
            )

            Spacer(modifier = Modifier.height(CueSpacing.Sm))

            Surface(
                color = CueTheme.colors.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CueSpacing.Lg)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CueTheme.colors.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = CueTheme.colors.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_privacy_delete_account_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = CueTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.profile_privacy_delete_account_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = CueTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CueSpacing.Md))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRequestDeleteAccount()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CueTheme.colors.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_privacy_delete_confirm_btn),
                            style = MaterialTheme.typography.labelLarge,
                            color = CueTheme.colors.surface
                        )
                    }
                }
            }
        }
    }

    // Delete Account Confirmation Dialog (shown on top of sheet)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = {
                Text(
                    text = stringResource(R.string.profile_privacy_delete_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = CueTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.profile_privacy_delete_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDeleteAccount) {
                    Text(
                        text = stringResource(R.string.profile_privacy_delete_confirm_btn),
                        style = MaterialTheme.typography.labelLarge,
                        color = CueTheme.colors.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = CueTheme.colors.textSecondary
                    )
                }
            },
            containerColor = CueTheme.colors.surface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    SmartReminderTheme {
        ProfileScreen(
            uiState = ProfileUiState.Loaded(
                displayName = "Alex",
                email = "alex@email.com",
                avatarUrl = null,
                wakeUpTime = LocalTime.of(6, 30),
                sleepTime = LocalTime.of(22, 30),
                themeMode = ThemeMode.SYSTEM
            ),
            onAction = {}
        )
    }
}
