package com.smartreminder.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
