package com.smartreminder.ui.onboarding.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.domain.time.TimeCalculator
import com.smartreminder.ui.onboarding.OnboardingStep
import com.smartreminder.ui.onboarding.TimePickerTarget
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Polished Theme Toggle Button with continuous rotation & alpha transition (@hieu-ung-nangcao).
 */
@Composable
fun ThemeToggleButton(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ThemeToggleRotation"
    )

    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle()
        },
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                rotationZ = rotation
            }
    ) {
        Crossfade(
            targetState = isDark,
            animationSpec = tween(durationMillis = 200),
            label = "ThemeToggleCrossfade"
        ) { dark ->
            if (dark) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = "Switch to Light Mode",
                    tint = CueTheme.colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.WbSunny,
                    contentDescription = "Switch to Dark Mode",
                    tint = CueTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Primary CTA Button for Cue with 16dp rounded corners, native ripple, and subtle tactile press feedback.
 */
@Composable
fun CuePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = CueTheme.colors.cta,
    textColor: Color = CueTheme.colors.onCta,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "CueButtonPressScale"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = backgroundColor,
        contentColor = textColor,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
        }
    }
}

/**
 * Animated Progress Dots Indicator (Step 1 of 3, 2 of 3, 3 of 3)
 */
@Composable
fun ProgressDotsIndicator(
    currentStep: OnboardingStep,
    totalSteps: Int = 3,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(totalSteps) { index ->
            val isSelected = currentStep.stepIndex == index
            val isPassed = currentStep.stepIndex > index

            val width by animateDpAsState(
                targetValue = if (isSelected) 20.dp else CueSpacing.Sm,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "DotWidth"
            )

            val color by animateColorAsState(
                targetValue = when {
                    isSelected && currentStep == OnboardingStep.TIMELINE -> CueTheme.colors.textPrimary
                    isSelected -> CueTheme.colors.accent
                    isPassed -> CueTheme.colors.accent
                    else -> CueTheme.colors.borderStrong
                },
                animationSpec = tween(durationMillis = 200),
                label = "DotColor"
            )

            Box(
                modifier = Modifier
                    .height(CueSpacing.Sm)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * 24-Hour Segmented Daily Rhythm Visualizer (Cardless, Light Editorial UI)
 * Dynamically positions the Sleep label exactly at the Planning / Quiet boundary using BoxWithConstraints.
 */
@Composable
fun CueDailyRhythm(
    wakeUpTime: LocalTime,
    sleepTime: LocalTime,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"
    val timeFormatter = remember(is24Hour) { DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()) }

    val breakdown = remember(wakeUpTime, sleepTime) {
        TimeCalculator.calculateDailyRhythmBreakdown(wakeUpTime, sleepTime)
    }

    var sleepLabelWidthPx by remember { mutableStateOf(0) }
    var nextWakeLabelWidthPx by remember { mutableStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Eyebrow
        Text(
            text = stringResource(R.string.onboarding_rhythm_daily_eyebrow),
            style = MaterialTheme.typography.labelSmall,
            color = CueTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Md))

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val density = LocalDensity.current
            val barWidthPx = with(density) { maxWidth.toPx() }
            val sleepCenterPx = barWidthPx * breakdown.planningFraction

            val sleepOffsetDp = with(density) {
                (sleepCenterPx - sleepLabelWidthPx / 2f)
                    .coerceIn(0f, (barWidthPx - sleepLabelWidthPx).coerceAtLeast(0f))
                    .toDp()
            }

            val nextWakeOffsetDp = with(density) {
                (barWidthPx - nextWakeLabelWidthPx).coerceAtLeast(0f).toDp()
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Dynamic Boundary-Aligned Time Markers Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    // Wake Marker (Aligned Left: 0%)
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = wakeUpTime.format(timeFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = CueTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.onboarding_rhythm_wake_up),
                            style = MaterialTheme.typography.labelSmall,
                            color = CueTheme.colors.textSecondary
                        )
                    }

                    // Sleep Marker (Dynamically centered at planningFraction boundary)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(x = sleepOffsetDp)
                            .onGloballyPositioned {
                                sleepLabelWidthPx = it.size.width
                            }
                    ) {
                        Text(
                            text = sleepTime.format(timeFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = CueTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.onboarding_rhythm_sleep),
                            style = MaterialTheme.typography.labelSmall,
                            color = CueTheme.colors.textSecondary
                        )
                    }

                    // Next Wake Marker (Aligned Right: 100%)
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .offset(x = nextWakeOffsetDp)
                            .onGloballyPositioned {
                                nextWakeLabelWidthPx = it.size.width
                            }
                    ) {
                        Text(
                            text = wakeUpTime.format(timeFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = CueTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.onboarding_rhythm_next_wake),
                            style = MaterialTheme.typography.labelSmall,
                            color = CueTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CueSpacing.Sm))

                // 24-Hour Segmented Timeline Track (Indigo for Planning / Neutral for Quiet) with boundary node
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Segmented Track
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    ) {
                        // Planning Window Segment
                        Box(
                            modifier = Modifier
                                .weight(breakdown.planningFraction)
                                .fillMaxHeight()
                                .background(CueTheme.colors.accent)
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        // Quiet Hours Segment
                        Box(
                            modifier = Modifier
                                .weight(breakdown.quietFraction)
                                .fillMaxHeight()
                                .background(CueTheme.colors.borderStrong)
                        )
                    }

                    // Subtle boundary marker dot at Sleep time
                    val markerOffsetDp = with(density) {
                        (sleepCenterPx - 4.dp.toPx()).toDp()
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = markerOffsetDp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CueTheme.colors.accentStrong)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CueSpacing.Md))

        // Clean Inline Legend Row (Planning · 16h 30m / Quiet · 7h 30m)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.onboarding_rhythm_planning_legend,
                    breakdown.planningDuration.hours,
                    breakdown.planningDuration.minutes
                ),
                style = MaterialTheme.typography.labelMedium,
                color = CueTheme.colors.accentStrong
            )

            Text(
                text = stringResource(
                    R.string.onboarding_rhythm_quiet_legend,
                    breakdown.quietDuration.hours,
                    breakdown.quietDuration.minutes
                ),
                style = MaterialTheme.typography.labelMedium,
                color = CueTheme.colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(CueSpacing.Lg))

        // Micro-caption explaining Cue planning behavior
        Text(
            text = stringResource(R.string.onboarding_rhythm_insight),
            style = MaterialTheme.typography.bodySmall,
            color = CueTheme.colors.textSecondary
        )
    }
}

/**
 * Polished Bottom Sheet for Quick Time Selection with device-aware 12h/24h time formatting
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CueTimePickerBottomSheet(
    target: TimePickerTarget,
    currentTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustomDial by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"
    val timeFormatter = remember(is24Hour) { DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()) }

    val isWakeUp = target == TimePickerTarget.WAKE_UP
    val title = if (isWakeUp) {
        stringResource(R.string.onboarding_dialog_wake_up_title)
    } else {
        stringResource(R.string.onboarding_dialog_sleep_title)
    }

    val presets = if (isWakeUp) {
        listOf(
            LocalTime.of(6, 0),
            LocalTime.of(6, 30),
            LocalTime.of(7, 0),
            LocalTime.of(7, 30),
            LocalTime.of(8, 0),
            LocalTime.of(8, 30)
        )
    } else {
        listOf(
            LocalTime.of(22, 0),
            LocalTime.of(22, 30),
            LocalTime.of(23, 0),
            LocalTime.of(23, 30),
            LocalTime.of(0, 0),
            LocalTime.of(0, 30)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CueTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
                .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Md)
                .navigationBarsPadding()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Lg))

            if (!showCustomDial) {
                Text(
                    text = stringResource(R.string.onboarding_quick_select_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = CueTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(CueSpacing.Md))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(CueSpacing.Md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { preset ->
                        val isSelected = preset.hour == currentTime.hour && preset.minute == currentTime.minute
                        val backgroundColor = if (isSelected) CueTheme.colors.accentContainer else CueTheme.colors.surfaceSubtle
                        val textColor = if (isSelected) CueTheme.colors.accentStrong else CueTheme.colors.textPrimary
                        val borderColor = if (isSelected) CueTheme.colors.accent else CueTheme.colors.border

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTimeSelected(preset)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(CueSpacing.Md),
                            color = backgroundColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                        ) {
                            Text(
                                text = preset.format(timeFormatter),
                                style = MaterialTheme.typography.labelLarge,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Md)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(CueSpacing.Xl))

                // Custom Time Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCustomDial = true
                    },
                    shape = RoundedCornerShape(CueSpacing.Md),
                    color = CueTheme.colors.surfaceSubtle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = CueSpacing.Md)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = CueTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(CueSpacing.Sm))
                        Text(
                            text = stringResource(R.string.onboarding_custom_time),
                            style = MaterialTheme.typography.labelLarge,
                            color = CueTheme.colors.textPrimary
                        )
                    }
                }
            } else {
                val timePickerState = rememberTimePickerState(
                    initialHour = currentTime.hour,
                    initialMinute = currentTime.minute,
                    is24Hour = is24Hour
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = CueTheme.colors.surfaceSubtle,
                            clockDialUnselectedContentColor = CueTheme.colors.textPrimary,
                            clockDialSelectedContentColor = CueTheme.colors.onCta,
                            selectorColor = CueTheme.colors.accent,
                            periodSelectorBorderColor = CueTheme.colors.border,
                            periodSelectorSelectedContainerColor = CueTheme.colors.accentContainer,
                            periodSelectorSelectedContentColor = CueTheme.colors.accentStrong,
                            periodSelectorUnselectedContentColor = CueTheme.colors.textSecondary,
                            timeSelectorSelectedContainerColor = CueTheme.colors.accentContainer,
                            timeSelectorSelectedContentColor = CueTheme.colors.accentStrong,
                            timeSelectorUnselectedContainerColor = CueTheme.colors.surfaceSubtle,
                            timeSelectorUnselectedContentColor = CueTheme.colors.textPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(CueSpacing.Lg))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showCustomDial = false }) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                style = MaterialTheme.typography.labelLarge,
                                color = CueTheme.colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(CueSpacing.Sm))
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.action_ok),
                                style = MaterialTheme.typography.labelLarge,
                                color = CueTheme.colors.accent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Md))
        }
    }
}
