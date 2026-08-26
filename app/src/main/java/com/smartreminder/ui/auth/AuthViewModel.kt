package com.smartreminder.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartreminder.data.remote.SupabaseManager
import com.smartreminder.domain.auth.GoogleAuthHelper
import com.smartreminder.domain.sync.RestorePreferencesResult
import com.smartreminder.domain.sync.UserPreferencesSyncCoordinator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val syncCoordinator: UserPreferencesSyncCoordinator
) : ViewModel() {

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
                    android.util.Log.d("SmartReminderAuth", "Google ID Token received successfully: ${result.idToken.take(15)}...")
                    try {
                        // 1. Xác thực với Supabase qua IDToken và Nonce
                        android.util.Log.d("SmartReminderAuth", "Starting Supabase signInWith IDToken...")
                        SupabaseManager.client.auth.signInWith(IDToken) {
                            this.idToken = result.idToken
                            this.nonce = result.rawNonce
                            this.provider = Google
                        }

                        val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                        android.util.Log.d("SmartReminderAuth", "Supabase signed in: currentUser=${currentUser?.id}, email=${currentUser?.email}")
                        if (currentUser == null) {
                            _uiState.update { AuthUiState.Error("Không tìm thấy thông tin tài khoản Supabase.") }
                            return@launch
                        }

                        // 2. Restore user preferences từ Supabase Cloud
                        try {
                            android.util.Log.d("SmartReminderAuth", "Starting restoreForUser(${currentUser.id})...")
                            when (val restoreResult = syncCoordinator.restoreForUser(currentUser.id)) {
                                RestorePreferencesResult.RestoredCompleted -> {
                                    android.util.Log.d("SmartReminderAuth", "Restore completed -> RestoredCompleted")
                                    _uiState.update {
                                        AuthUiState.Success(
                                            userEmail = currentUser.email,
                                            needsOnboarding = false
                                        )
                                    }
                                }
                                RestorePreferencesResult.NeedsOnboarding -> {
                                    android.util.Log.d("SmartReminderAuth", "Restore completed -> NeedsOnboarding")
                                    _uiState.update {
                                        AuthUiState.Success(
                                            userEmail = currentUser.email,
                                            needsOnboarding = true
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SmartReminderAuth", "restoreForUser threw exception", e)
                            // Best-effort sign out để tránh authenticated half-state khi restore lỗi
                            try {
                                SupabaseManager.client.auth.signOut()
                            } catch (_: Exception) {
                                // Ignore sign out error during recovery
                            }
                            _uiState.update {
                                AuthUiState.Error(
                                    "Không thể đồng bộ dữ liệu tài khoản (${e.javaClass.simpleName}): ${e.localizedMessage ?: e.message}"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SmartReminderAuth", "Supabase signInWith failed", e)
                        _uiState.update {
                            AuthUiState.Error("Lỗi xác thực Supabase: ${e.localizedMessage ?: e.message}")
                        }
                    }
                },
                onFailure = { error ->
                    android.util.Log.e("SmartReminderAuth", "Google sign in failed", error)
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
