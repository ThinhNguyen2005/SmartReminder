package com.smartreminder.ui.app

/**
 * Root application state driven by [AppViewModel].
 * [Loading] prevents onboarding flash while DataStore reads asynchronously.
 */
sealed interface AppState {
    data object Loading : AppState
    data object Onboarding : AppState
    data object Main : AppState
}
