package com.smartreminder.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful route that bridges [OnboardingViewModel] to stateless [OnboardingScreen].
 * No onCompleted callback — navigation is purely reactive:
 * completeOnboarding() → DataStore → preferences Flow → AppViewModel → AppState.Main.
 */
@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScreen(
        uiState = uiState,
        onAction = viewModel::onAction
    )
}
