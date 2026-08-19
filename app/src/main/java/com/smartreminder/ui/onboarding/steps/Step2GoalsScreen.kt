package com.smartreminder.ui.onboarding.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.onboarding.GoalOption
import com.smartreminder.ui.onboarding.OnboardingUiState
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.SmartReminderTheme

@Composable
fun Step2GoalsScreen(
    uiState: OnboardingUiState,
    onToggleGoal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl)
    ) {
        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // Headline & narrative
        Text(
            text = stringResource(R.string.onboarding_goals_title),
            style = MaterialTheme.typography.headlineLarge,
            color = CueTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Sm))

        Text(
            text = stringResource(R.string.onboarding_goals_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // Selection Rows List
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(OnboardingUiState.DEFAULT_GOALS, key = { it.id }) { goal ->
                val isSelected = uiState.selectedGoals.contains(goal.id)

                GoalSelectionRow(
                    goal = goal,
                    isSelected = isSelected,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleGoal(goal.id)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(CueSpacing.Lg))

        // Dynamic Adaptive Feedback Badge
        AnimatedVisibility(
            visible = uiState.selectedGoals.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CueSpacing.Md))
                    .background(CueTheme.colors.accentContainer.copy(alpha = 0.6f))
                    .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Md)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_goals_configured_badge, uiState.selectedGoals.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = CueTheme.colors.accentStrong
                )
            }
        }

        Spacer(modifier = Modifier.height(CueSpacing.Xl))
    }
}

@Composable
private fun GoalSelectionRow(
    goal: GoalOption,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) CueTheme.colors.accentContainer.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "GoalRowBackground"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) CueSpacing.Xs else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "IndicatorWidth"
    )

    val icon: ImageVector = when (goal.id) {
        "tasks" -> if (isSelected) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt
        "routines" -> if (isSelected) Icons.Filled.Autorenew else Icons.Outlined.Autorenew
        "planning" -> if (isSelected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
        "study" -> if (isSelected) Icons.Filled.School else Icons.Outlined.School
        "teamwork" -> if (isSelected) Icons.Filled.Groups else Icons.Outlined.Groups
        else -> if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked
    }

    val title = stringResource(goal.titleRes)
    val description = stringResource(goal.descriptionRes)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                // Merged semantics for screen readers
            }
    ) {
        // Left Accent Bar Indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(CueTheme.colors.accent)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Lg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Lg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) CueTheme.colors.accent else CueTheme.colors.borderStrong,
                modifier = Modifier.size(22.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CueTheme.colors.textPrimary
                    )

                    // Optional AI Core Tag
                    if (goal.id == "planning") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(CueSpacing.Xs))
                                .background(CueTheme.colors.accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.goal_planning_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = CueTheme.colors.onCta
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textSecondary
                )
            }
        }

        HorizontalDivider(
            color = CueTheme.colors.border.copy(alpha = 0.6f),
            thickness = 1.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun Step2GoalsPreview() {
    SmartReminderTheme {
        Step2GoalsScreen(
            uiState = OnboardingUiState(selectedGoals = setOf("tasks", "planning")),
            onToggleGoal = {}
        )
    }
}
