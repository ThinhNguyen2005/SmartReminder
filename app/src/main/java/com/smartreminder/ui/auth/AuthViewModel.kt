package com.smartreminder.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.domain.auth.GoogleAuthHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onAction(action: AuthUiAction) {
        when (action) {
            is AuthUiAction.SignInWithGoogle -> signInWithGoogle(action.context)
            AuthUiAction.DismissError -> resetState()
            AuthUiAction.Reset -> resetState()
        }
    }

    private fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.update { AuthUiState.Loading }

            val webClientId = SupabaseManager.GOOGLE_WEB_CLIENT_ID

            // Kiểm tra xem Web Client ID đã được cấu hình hay chưa
            if (webClientId == "YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com") {
                _uiState.update {
                    AuthUiState.Error(
                        "Vui lòng điền GOOGLE_WEB_CLIENT_ID trong SupabaseClient.kt để kích hoạt đăng nhập Google."
                    )
                }
                return@launch
            }

            val tokenResult = GoogleAuthHelper.getGoogleIdToken(context, webClientId)
            tokenResult.fold(
                onSuccess = { result ->
                    try {
                        // Xác thực với Supabase qua IDToken và Nonce
                        SupabaseManager.client.auth.signInWith(IDToken) {
                            this.idToken = result.idToken
                            this.nonce = result.rawNonce
                            this.provider = Google
                        }

                        val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                        _uiState.update { AuthUiState.Success(currentUser?.email) }
                    } catch (e: Exception) {
                        _uiState.update {
                            AuthUiState.Error("Lỗi xác thực Supabase: ${e.localizedMessage ?: e.message}")
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        AuthUiState.Error(error.localizedMessage ?: "Đăng nhập Google thất bại")
                    }
                }
            )
        }
    }

    fun resetState() {
        _uiState.update { AuthUiState.Idle }
    }
}
