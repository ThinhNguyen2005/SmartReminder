package com.smartreminder.ui.auth

import android.content.Context

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Success(val userEmail: String?) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

sealed interface AuthUiAction {
    data class SignInWithGoogle(val context: Context) : AuthUiAction
    data object DismissError : AuthUiAction
    data object Reset : AuthUiAction
}
