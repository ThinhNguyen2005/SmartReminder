package com.smartreminder

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartreminder.di.AppContainer
import com.smartreminder.domain.model.ThemeMode
import com.smartreminder.ui.app.AppState
import com.smartreminder.ui.app.AppViewModel
import com.smartreminder.ui.app.AppViewModelFactory
import com.smartreminder.ui.auth.AuthViewModel
import com.smartreminder.ui.onboarding.OnboardingRoute
import com.smartreminder.ui.onboarding.OnboardingViewModel
import com.smartreminder.ui.onboarding.OnboardingViewModelFactory
import com.smartreminder.ui.profile.ProfileRoute
import com.smartreminder.ui.profile.ProfileScreen
import com.smartreminder.ui.profile.ProfileUiState
import com.smartreminder.ui.profile.ProfileViewModel
import com.smartreminder.ui.profile.ProfileViewModelFactory
import com.smartreminder.ui.schedules.SchedulesRoute
import com.smartreminder.ui.schedules.SchedulesViewModel
import com.smartreminder.ui.schedules.SchedulesViewModelFactory
import com.smartreminder.ui.screens.WelcomeScreen
import com.smartreminder.ui.tasks.TasksPlaceholderScreen
import com.smartreminder.ui.theme.SmartReminderTheme
import com.smartreminder.ui.today.TodayPlaceholderScreen

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
                                            flowStage = OnboardingFlowStage.ONBOARDING_STEPS
                                        },
                                        onContinueWithEmail = {
                                            flowStage = OnboardingFlowStage.ONBOARDING_STEPS
                                        },
                                        onSignInClick = {
                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(R.string.auth_sign_in_coming_soon),
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                            val schedulesViewModel: SchedulesViewModel = viewModel(
                                factory = SchedulesViewModelFactory(
                                    scheduleGroupRepository = appContainer.scheduleGroupRepository,
                                    routineRepository = appContainer.routineRepository
                                )
                            )
                            SmartReminderApp(
                                schedulesViewModel = schedulesViewModel,
                                appContainer = appContainer,
                                onRestartOnboarding = {
                                    appViewModel.resetOnboarding()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartReminderApp(
    schedulesViewModel: SchedulesViewModel,
    appContainer: AppContainer? = null,
    onRestartOnboarding: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.TODAY) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach {
                item(
                    icon = {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = stringResource(it.labelRes)
                        )
                    },
                    label = { Text(stringResource(it.labelRes)) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestination.TODAY -> TodayPlaceholderScreen(
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestination.SCHEDULES -> SchedulesRoute(
                    viewModel = schedulesViewModel,
                    onOpenRoutine = {},
                    onCreateRoutine = {},
                    onManageGroups = {},
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestination.TASKS -> TasksPlaceholderScreen(
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestination.PROFILE -> {
                    if (appContainer != null) {
                        val profileViewModel: ProfileViewModel = viewModel(
                            factory = ProfileViewModelFactory(
                                repository = appContainer.userPreferencesRepository,
                                onSignedOut = onRestartOnboarding
                            )
                        )
                        ProfileRoute(
                            viewModel = profileViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        ProfileScreen(
                            uiState = ProfileUiState.Loaded(
                                displayName = "Alex",
                                email = "alex@email.com",
                                avatarUrl = null,
                                wakeUpTime = java.time.LocalTime.of(6, 30),
                                sleepTime = java.time.LocalTime.of(22, 30),
                                themeMode = ThemeMode.SYSTEM
                            ),
                            onAction = {},
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY(R.string.nav_today, Icons.Default.Home),
    SCHEDULES(R.string.nav_schedules, Icons.Default.DateRange),
    TASKS(R.string.nav_tasks, Icons.Default.CheckCircle),
    PROFILE(R.string.nav_profile, Icons.Default.Person),
}
