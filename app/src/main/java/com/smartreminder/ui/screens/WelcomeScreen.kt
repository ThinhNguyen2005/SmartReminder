package com.smartreminder.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartreminder.R
import com.smartreminder.ui.auth.AuthUiAction
import com.smartreminder.ui.auth.AuthUiState
import com.smartreminder.ui.auth.AuthViewModel
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueBackground
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueCta
import com.smartreminder.ui.theme.CueOnCta
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurface
import com.smartreminder.ui.theme.CueSurfaceSubtle
import com.smartreminder.ui.theme.CueTextPrimary
import com.smartreminder.ui.theme.CueTextSecondary
import com.smartreminder.ui.theme.SmartReminderTheme

private const val HERO_IMAGE_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuCXVW6N1fI7o5OzsFONm4AenNrb3wxPc-MuNBVdFzfFXxJ-knESUiaLjtqTOM9KVzHzn1mt570ic7Ril7v-ygsmesqbM9Fe606X_QCT_nSOIHBYkJH738JoKgMfvcOS2erCFNTAqULlGlLh-AcVZNQ25tlyalVftTM6H0EeA0FaCLn0Ag-GcdTNEkKZLQfr8fegxQVgF0IbviphmKhhmsQMmB4KDNwN4mPXE6wHPvHtOzh3inBxLG4"

@Composable
fun WelcomeScreen(
    onContinueWithEmail: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onLoginSuccess: (needsOnboarding: Boolean) -> Unit = {},
    viewModel: AuthViewModel? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel?.uiState?.collectAsState() ?: remember {
        androidx.compose.runtime.mutableStateOf(AuthUiState.Idle)
    }

    WelcomeScreenContent(
        uiState = uiState,
        onAction = { action -> viewModel?.onAction(action) },
        onContinueWithEmail = onContinueWithEmail,
        onSignInClick = onSignInClick,
        onLoginSuccess = onLoginSuccess,
        modifier = modifier
    )
}

@Composable
fun WelcomeScreenContent(
    uiState: AuthUiState,
    onAction: (AuthUiAction) -> Unit,
    onContinueWithEmail: () -> Unit,
    onSignInClick: () -> Unit,
    onLoginSuccess: (needsOnboarding: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Error -> {
                snackbarHostState.showSnackbar(uiState.message)
                onAction(AuthUiAction.DismissError)
            }
            is AuthUiState.Success -> {
                val toastMessage = context.getString(
                    R.string.auth_success_toast,
                    uiState.userEmail ?: ""
                )
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                onLoginSuccess(uiState.needsOnboarding)
                onAction(AuthUiAction.Reset)
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CueBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            WelcomeBottomActions(
                uiState = uiState,
                onGoogleSignIn = {
                    onAction(AuthUiAction.SignInWithGoogle(context))
                },
                onContinueWithEmail = onContinueWithEmail,
                onSignInClick = onSignInClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Lg)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
                .padding(horizontal = CueSpacing.Xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // ================== LOGO BADGE ==================
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(CueSpacing.Lg),
                color = CueCta
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.auth_logo_cue_description),
                        tint = CueAccentContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // ================== HEADLINE & SUBTITLE ==================
            Text(
                text = stringResource(R.string.auth_welcome_headline),
                style = MaterialTheme.typography.headlineLarge,
                color = CueTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(CueSpacing.Sm))

            Text(
                text = stringResource(R.string.auth_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = CueTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = CueSpacing.Sm)
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xxl))

            // ================== HERO 3D CARD ==================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                shape = RoundedCornerShape(CueSpacing.Xl),
                color = CueSurface,
                border = BorderStroke(1.dp, CueBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(CueSurface, CueSurfaceSubtle)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(HERO_IMAGE_URL)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.auth_hero_image_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(CueSpacing.Xl))
                    )
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Xl))
        }
    }
}

@Composable
private fun WelcomeBottomActions(
    uiState: AuthUiState,
    onGoogleSignIn: () -> Unit,
    onContinueWithEmail: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = uiState is AuthUiState.Loading

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)
    ) {
        // 1. Continue with Google (Primary CTA)
        Button(
            onClick = { if (!isLoading) onGoogleSignIn() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(CueSpacing.Lg),
            colors = ButtonDefaults.buttonColors(
                containerColor = CueCta,
                contentColor = CueOnCta,
                disabledContainerColor = CueCta.copy(alpha = 0.7f),
                disabledContentColor = CueOnCta.copy(alpha = 0.7f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = CueOnCta,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = stringResource(R.string.auth_google_logo_description),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(CueSpacing.Sm))
                    Text(
                        text = stringResource(R.string.auth_btn_google),
                        style = MaterialTheme.typography.labelLarge,
                        color = CueOnCta,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. Continue with Email (Secondary Outline CTA)
        OutlinedButton(
            onClick = onContinueWithEmail,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(CueSpacing.Lg),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CueSurface,
                contentColor = CueTextPrimary
            ),
            border = BorderStroke(1.dp, CueBorder)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mail,
                    contentDescription = stringResource(R.string.auth_email_logo_description),
                    tint = CueTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(CueSpacing.Sm))
                Text(
                    text = stringResource(R.string.auth_btn_email),
                    style = MaterialTheme.typography.labelLarge,
                    color = CueTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. Footer link: Already have an account? Sign in
        Row(
            modifier = Modifier.padding(top = CueSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.auth_have_account_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = CueTextSecondary
            )
            Text(
                text = stringResource(R.string.auth_sign_in_action),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = CueAccent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onSignInClick()
                }
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun WelcomeScreenPreview() {
    SmartReminderTheme {
        WelcomeScreenContent(
            uiState = AuthUiState.Idle,
            onAction = {},
            onContinueWithEmail = {},
            onSignInClick = {},
            onLoginSuccess = {}
        )
    }
}
