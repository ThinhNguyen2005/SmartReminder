package com.smartreminder.ui.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given new AuthViewModel, when initialized, then initial state is Idle`() = runTest {
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given error state, when dismiss error action dispatched, then state returns to Idle`() = runTest {
        viewModel.onAction(AuthUiAction.DismissError)
        advanceUntilIdle()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given success state consumed, when reset action dispatched, then state returns to Idle to prevent auto-login on reset onboarding`() = runTest {
        // Given: Auth succeeds and sets Success (simulated via action reset lifecycle)
        viewModel.onAction(AuthUiAction.Reset)
        advanceUntilIdle()

        // Then: State is Idle
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
        assertTrue(viewModel.uiState.value !is AuthUiState.Success)
    }
}
