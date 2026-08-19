package com.smartreminder.ui.onboarding.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.onboarding.OnboardingUiState
import com.smartreminder.ui.onboarding.TimePickerTarget
import com.smartreminder.ui.onboarding.components.CueDailyRhythm
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurfaceSubtle
import com.smartreminder.ui.theme.CueTextPrimary
import com.smartreminder.ui.theme.CueTextSecondary
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Step1RhythmScreen(
    uiState: OnboardingUiState,
    onRequestTimePicker: (TimePickerTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"
    val timeFormatter = remember(is24Hour) { DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CueSpacing.Xl)
    ) {
        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // Headline & narrative
        Text(
            text = stringResource(R.string.onboarding_rhythm_title),
            style = MaterialTheme.typography.headlineLarge,
            color = CueTextPrimary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Sm))

        Text(
            text = stringResource(R.string.onboarding_rhythm_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTextSecondary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // Section Eyebrow (Used sparingly per design rules)
        Text(
            text = stringResource(R.string.onboarding_rhythm_eyebrow),
            style = MaterialTheme.typography.labelSmall,
            color = CueTextSecondary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Sm))

        // Routine Selection Container with editorial inset dividers
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(color = CueBorder, thickness = 1.dp)

            // Wake Time Row (Whole Row Clickable with Haptics)
            RoutineTimeRow(
                icon = Icons.Outlined.WbSunny,
                title = stringResource(R.string.onboarding_rhythm_wake_up),
                timeFormatted = uiState.wakeUpTime.format(timeFormatter),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRequestTimePicker(TimePickerTarget.WAKE_UP)
                }
            )

            HorizontalDivider(
                color = CueBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(start = 36.dp)
            )

            // Sleep Time Row (Whole Row Clickable with Haptics)
            RoutineTimeRow(
                icon = Icons.Outlined.Bedtime,
                title = stringResource(R.string.onboarding_rhythm_sleep),
                timeFormatted = uiState.sleepTime.format(timeFormatter),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRequestTimePicker(TimePickerTarget.SLEEP)
                }
            )

            HorizontalDivider(color = CueBorder, thickness = 1.dp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 24-Hour Segmented Daily Rhythm (Cardless)
        CueDailyRhythm(
            wakeUpTime = uiState.wakeUpTime,
            sleepTime = uiState.sleepTime
        )

        Spacer(modifier = Modifier.height(CueSpacing.Xl))
    }
}

@Composable
private fun RoutineTimeRow(
    icon: ImageVector,
    title: String,
    timeFormatted: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $timeFormatted"
            }
            .padding(vertical = CueSpacing.Lg)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CueTextSecondary,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CueTextPrimary
            )
        }

        // Tonal Time Pill (No nested click target)
        Surface(
            shape = RoundedCornerShape(CueSpacing.Sm),
            color = CueSurfaceSubtle
        ) {
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelLarge,
                color = CueTextPrimary,
                modifier = Modifier.padding(
                    horizontal = CueSpacing.Lg,
                    vertical = CueSpacing.Sm
                )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF9)
@Composable
private fun Step1RhythmPreview() {
    SmartReminderTheme {
        Step1RhythmScreen(
            uiState = OnboardingUiState(),
            onRequestTimePicker = {}
        )
    }
}
