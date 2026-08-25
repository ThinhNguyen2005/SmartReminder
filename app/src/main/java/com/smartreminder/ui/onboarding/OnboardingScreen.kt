package com.smartreminder.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.onboarding.components.CuePrimaryButton
import com.smartreminder.ui.onboarding.components.CueTimePickerBottomSheet
import com.smartreminder.ui.onboarding.components.ProgressDotsIndicator
import com.smartreminder.ui.onboarding.steps.Step1RhythmScreen
import com.smartreminder.ui.onboarding.steps.Step2GoalsScreen
import com.smartreminder.ui.onboarding.steps.Step3TimelineScreen
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.SmartReminderTheme
import kotlinx.coroutines.launch

/**
 * Stateless onboarding screen — receives [uiState] and dispatches [onAction].
 * All state management lives in [OnboardingViewModel]; this composable only renders.
 * PagerState is a local rendering mechanism, synced with [OnboardingUiState.currentStep].
 */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Sync PagerState with ViewModel's currentStep (ViewModel is source of truth)
    LaunchedEffect(uiState.currentStep) {
        val targetPage = uiState.currentStep.stepIndex
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    val currentStep = uiState.currentStep

    Scaffold(
        containerColor = CueTheme.colors.background,
        topBar = {
            OnboardingHeader(
                currentStep = currentStep,
                onBack = {
                    currentStep.previousStep?.let { prev ->
                        onAction(OnboardingAction.NavigateToStep(prev))
                    }
                },
                onSkip = { onAction(OnboardingAction.Skip) }
            )
        },
        bottomBar = {
            OnboardingFooter(
                currentStep = currentStep,
                isSaving = uiState.isSaving,
                onContinue = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val nextStep = currentStep.nextStep
                    if (nextStep != null) {
                        onAction(OnboardingAction.NavigateToStep(nextStep))
                    } else {
                        onAction(OnboardingAction.Complete)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Lg)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> {
                    Step1RhythmScreen(
                        uiState = uiState,
                        onRequestTimePicker = { target ->
                            onAction(OnboardingAction.OpenTimePicker(target))
                        }
                    )
                }
                1 -> {
                    Step2GoalsScreen(
                        uiState = uiState,
                        onToggleGoal = { goal ->
                            onAction(OnboardingAction.ToggleGoal(goal))
                        }
                    )
                }
                2 -> {
                    Step3TimelineScreen()
                }
            }
        }
    }

    // Time Picker Bottom Sheet
    uiState.activeTimePicker?.let { target ->
        val currentTime = when (target) {
            TimePickerTarget.WAKE_UP -> uiState.wakeUpTime
            TimePickerTarget.SLEEP -> uiState.sleepTime
        }

        CueTimePickerBottomSheet(
            target = target,
            currentTime = currentTime,
            onTimeSelected = { newTime ->
                when (target) {
                    TimePickerTarget.WAKE_UP -> onAction(OnboardingAction.UpdateWakeTime(newTime))
                    TimePickerTarget.SLEEP -> onAction(OnboardingAction.UpdateSleepTime(newTime))
                }
            },
            onDismiss = { onAction(OnboardingAction.DismissTimePicker) }
        )
    }
}

@Composable
private fun OnboardingHeader(
    currentStep: OnboardingStep,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = CueSpacing.Md)
    ) {
        if (currentStep != OnboardingStep.RHYTHM) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = CueTheme.colors.textPrimary
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }

        if (currentStep == OnboardingStep.RHYTHM) {
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.action_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = CueTheme.colors.accent
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun OnboardingFooter(
    currentStep: OnboardingStep,
    isSaving: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Lg),
        modifier = modifier
    ) {
        ProgressDotsIndicator(currentStep = currentStep)

        val buttonText = when (currentStep) {
            OnboardingStep.RHYTHM, OnboardingStep.GOALS -> stringResource(R.string.action_continue)
            OnboardingStep.TIMELINE -> stringResource(R.string.action_start_using_cue)
        }

        CuePrimaryButton(
            text = buttonText,
            onClick = onContinue,
            enabled = !isSaving,
            backgroundColor = CueTheme.colors.cta,
            textColor = CueTheme.colors.onCta
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    SmartReminderTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(),
            onAction = {}
        )
    }
}
