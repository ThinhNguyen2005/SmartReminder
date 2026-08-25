package com.smartreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.ui.app.AppState
import com.smartreminder.ui.app.AppViewModel
import com.smartreminder.ui.app.AppViewModelFactory
import com.smartreminder.ui.auth.AuthViewModel
import com.smartreminder.ui.onboarding.OnboardingRoute
import com.smartreminder.ui.onboarding.OnboardingViewModel
import com.smartreminder.ui.onboarding.OnboardingViewModelFactory
import com.smartreminder.ui.screens.WelcomeScreen
import com.smartreminder.ui.theme.SmartReminderTheme
import kotlinx.coroutines.launch

enum class OnboardingFlowStage {
    WELCOME,
    ONBOARDING_STEPS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as CueApplication).appContainer

        setContent {
            val appViewModel: AppViewModel = viewModel(
                factory = AppViewModelFactory(appContainer.userPreferencesRepository)
            )
            val appState by appViewModel.appState.collectAsStateWithLifecycle()
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SmartReminderTheme(darkTheme = darkTheme) {
                Crossfade(
                    targetState = appState,
                    label = "AppStateCrossfade"
                ) { state ->
                    when (state) {
                        AppState.Loading -> {
                            // Blank — prevents onboarding flash while DataStore loads asynchronously
                        }
                        AppState.Onboarding -> {
                            var flowStage by rememberSaveable { mutableStateOf(OnboardingFlowStage.WELCOME) }
                            val authViewModel: AuthViewModel = viewModel()

                            when (flowStage) {
                                OnboardingFlowStage.WELCOME -> {
                                    WelcomeScreen(
                                        viewModel = authViewModel,
                                        onLoginSuccess = {
                                            coroutineScope.launch {
                                                appContainer.userPreferencesRepository.completeOnboarding(
                                                    wakeUpTime = com.smartreminder.domain.model.UserPreferences.DEFAULT_WAKE_TIME,
                                                    sleepTime = com.smartreminder.domain.model.UserPreferences.DEFAULT_SLEEP_TIME,
                                                    goals = com.smartreminder.domain.model.UserPreferences.DEFAULT_GOALS
                                                )
                                            }
                                        },
                                        onContinueWithEmail = {
                                            flowStage = OnboardingFlowStage.ONBOARDING_STEPS
                                        },
                                        onSignInClick = {
                                            coroutineScope.launch {
                                                appContainer.userPreferencesRepository.completeOnboarding(
                                                    wakeUpTime = com.smartreminder.domain.model.UserPreferences.DEFAULT_WAKE_TIME,
                                                    sleepTime = com.smartreminder.domain.model.UserPreferences.DEFAULT_SLEEP_TIME,
                                                    goals = com.smartreminder.domain.model.UserPreferences.DEFAULT_GOALS
                                                )
                                            }
                                        }
                                    )
                                }
                                OnboardingFlowStage.ONBOARDING_STEPS -> {
                                    val onboardingViewModel: OnboardingViewModel = viewModel(
                                        factory = OnboardingViewModelFactory(appContainer.userPreferencesRepository)
                                    )
                                    OnboardingRoute(viewModel = onboardingViewModel)
                                }
                            }
                        }
                        AppState.Main -> {
                            SmartReminderApp(
                                onRestartOnboarding = {
                                    coroutineScope.launch {
                                        appContainer.userPreferencesRepository.resetOnboarding()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun SmartReminderApp(
    onRestartOnboarding: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "Cue User",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name! Welcome to Cue.",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SmartReminderTheme {
        SmartReminderApp()
    }
}