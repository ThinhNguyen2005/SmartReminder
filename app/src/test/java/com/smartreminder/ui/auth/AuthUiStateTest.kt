package com.smartreminder.ui.auth

import com.smartreminder.domain.auth.GoogleTokenResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUiStateTest {

    @Test
    fun `given idle state, when checking type, then is Idle`() {
        val state: AuthUiState = AuthUiState.Idle
        assertTrue(state is AuthUiState.Idle)
    }

    @Test
    fun `given loading state, when checking type, then is Loading`() {
        val state: AuthUiState = AuthUiState.Loading
        assertTrue(state is AuthUiState.Loading)
    }

    @Test
    fun `given success state with user email, then email is preserved`() {
        val email = "user@smartreminder.com"
        val state = AuthUiState.Success(userEmail = email)

        assertEquals(email, state.userEmail)
    }

    @Test
    fun `given error state with message, then error message is preserved`() {
        val errorMessage = "Không thể kết nối máy chủ"
        val state = AuthUiState.Error(message = errorMessage)

        assertEquals(errorMessage, state.message)
    }

    @Test
    fun `given google token result, then idToken and rawNonce are properly encapsulated`() {
        val idToken = "sample-google-id-token-abc"
        val rawNonce = "sample-raw-nonce-xyz"
        val result = GoogleTokenResult(idToken = idToken, rawNonce = rawNonce)

        assertEquals(idToken, result.idToken)
        assertEquals(rawNonce, result.rawNonce)
    }

    @Test
    fun `given distinct auth actions, then are not equal`() {
        val action1: AuthUiAction = AuthUiAction.DismissError
        val action2: AuthUiAction = AuthUiAction.Reset

        assertNotEquals(action1, action2)
    }
}
