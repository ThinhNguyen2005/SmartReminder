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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartreminder.ui.auth.AuthViewModel
import com.smartreminder.ui.onboarding.OnboardingScreen
import com.smartreminder.ui.screens.WelcomeScreen
import com.smartreminder.ui.theme.SmartReminderTheme

enum class AppFlowState {
    WELCOME,
    ONBOARDING,
    MAIN_APP
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartReminderTheme {
                MainRootView()
            }
        }
    }
}

@Composable
fun MainRootView() {
    var flowState by rememberSaveable { mutableStateOf(AppFlowState.WELCOME) }
    val authViewModel: AuthViewModel = viewModel()

    Crossfade(
        targetState = flowState,
        label = "RootViewCrossfade"
    ) { state ->
        when (state) {
            AppFlowState.WELCOME -> {
                WelcomeScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        flowState = AppFlowState.MAIN_APP
                    },
                    onContinueWithEmail = {
                        flowState = AppFlowState.ONBOARDING
                    },
                    onSignInClick = {
                        flowState = AppFlowState.MAIN_APP
                    }
                )
            }
            AppFlowState.ONBOARDING -> {
                OnboardingScreen(
                    onFinishOnboarding = {
                        flowState = AppFlowState.MAIN_APP
                    }
                )
            }
            AppFlowState.MAIN_APP -> {
                SmartReminderApp(
                    onRestartOnboarding = {
                        flowState = AppFlowState.WELCOME
                    }
                )
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
        MainRootView()
    }
}