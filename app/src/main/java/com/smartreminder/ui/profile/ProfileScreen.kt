package com.smartreminder.ui.profile

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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.smartreminder.domain.model.preferences.ThemeMode
import com.smartreminder.ui.onboarding.TimePickerTarget
import com.smartreminder.ui.onboarding.components.CueTimePickerBottomSheet
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onAction: (ProfileUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
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
                            avatarUrl = uiState.avatarUrl
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
                                    onClick = { /* Placeholder action */ }
                                )
                                HorizontalDivider(
                                    color = CueTheme.colors.border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = CueSpacing.Lg)
                                )
                                SettingsRow(
                                    icon = Icons.Outlined.Sync,
                                    label = stringResource(R.string.profile_account_sync),
                                    onClick = { /* Placeholder action */ }
                                )
                                HorizontalDivider(
                                    color = CueTheme.colors.border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = CueSpacing.Lg)
                                )
                                SettingsRow(
                                    icon = Icons.Outlined.CloudUpload,
                                    label = stringResource(R.string.profile_data_backup),
                                    onClick = { /* Placeholder action */ }
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
                                SettingsRow(
                                    icon = Icons.Outlined.Palette,
                                    label = stringResource(R.string.profile_appearance),
                                    onClick = { /* Placeholder action */ }
                                )
                                HorizontalDivider(
                                    color = CueTheme.colors.border,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = CueSpacing.Lg)
                                )
                                SettingsRow(
                                    icon = Icons.Outlined.Lock,
                                    label = stringResource(R.string.profile_privacy),
                                    onClick = { /* Placeholder action */ }
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

        // Edit Profile Button (Placeholder)
        Surface(
            shape = CircleShape,
            color = CueTheme.colors.surfaceSubtle,
            modifier = Modifier
                .height(36.dp)
                .clickable(role = Role.Button) { /* Edit profile action placeholder */ }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = CueSpacing.Xl)
            ) {
                Text(
                    text = stringResource(R.string.profile_edit),
                    style = MaterialTheme.typography.labelLarge,
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
    modifier: Modifier = Modifier
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

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = CueTheme.colors.borderStrong,
            modifier = Modifier.size(16.dp)
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
