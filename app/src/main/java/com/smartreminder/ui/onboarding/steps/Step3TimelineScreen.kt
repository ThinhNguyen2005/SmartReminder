package com.smartreminder.ui.onboarding.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartreminder.R
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueAccentStrong
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTextPrimary
import com.smartreminder.ui.theme.CueTextSecondary
import com.smartreminder.ui.theme.CueTextTertiary
import com.smartreminder.ui.theme.SmartReminderTheme

@Composable
fun Step3TimelineScreen(
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showAiReasoning by remember { mutableStateOf(false) }

    val aiSuggestionAnimAlpha = remember { Animatable(0f) }
    val aiSuggestionAnimY = remember { Animatable(10f) }
    val aiSuggestionScale = remember { Animatable(0.94f) }

    LaunchedEffect(Unit) {
        aiSuggestionAnimAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        aiSuggestionAnimY.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(Unit) {
        aiSuggestionScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CueSpacing.Xl)
    ) {
        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        // Headline & narrative
        Text(
            text = stringResource(R.string.onboarding_timeline_title),
            style = MaterialTheme.typography.headlineLarge,
            color = CueTextPrimary
        )

        Spacer(modifier = Modifier.height(CueSpacing.Sm))

        Text(
            text = stringResource(R.string.onboarding_timeline_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTextSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Timeline Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CueSpacing.Xs)
        ) {
            // 09:00 Class
            TimelineItem(
                time = "09:00",
                title = stringResource(R.string.timeline_item_class),
                lineType = TimelineLineType.SOLID,
                isAi = false
            )

            // 14:00 Project meeting
            TimelineItem(
                time = "14:00",
                title = stringResource(R.string.timeline_item_meeting),
                lineType = TimelineLineType.SOLID,
                isAi = false
            )

            // 15:00 AI Suggestion (Interactive)
            TimelineItem(
                time = "15:00",
                title = stringResource(R.string.timeline_item_ai_suggestion),
                lineType = TimelineLineType.DASHED,
                isAi = true,
                aiAlpha = aiSuggestionAnimAlpha.value,
                aiOffsetY = aiSuggestionAnimY.value,
                aiScale = aiSuggestionScale.value,
                onAiClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showAiReasoning = !showAiReasoning
                }
            )

            // Collapsible AI Reasoning Card
            AnimatedVisibility(
                visible = showAiReasoning,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp, bottom = CueSpacing.Md)
                        .clip(RoundedCornerShape(CueSpacing.Md))
                        .background(CueAccentContainer)
                        .padding(CueSpacing.Md)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Xs)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = CueAccentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.timeline_ai_tooltip_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = CueAccentStrong
                            )
                        }

                        Spacer(modifier = Modifier.height(CueSpacing.Xs))

                        Text(
                            text = stringResource(R.string.timeline_ai_tooltip_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = CueTextPrimary
                        )
                    }
                }
            }

            // 19:00 Workout
            TimelineItem(
                time = "19:00",
                title = stringResource(R.string.timeline_item_workout),
                lineType = TimelineLineType.NONE,
                isAi = false
            )
        }

        Spacer(modifier = Modifier.height(CueSpacing.Lg))

        // Trust Message & Hint
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.onboarding_timeline_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = CueTextTertiary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xs))

            Text(
                text = stringResource(R.string.timeline_tap_hint),
                style = MaterialTheme.typography.labelSmall,
                color = CueAccent,
                modifier = Modifier.clickable {
                    showAiReasoning = !showAiReasoning
                }
            )
        }

        Spacer(modifier = Modifier.height(CueSpacing.Xl))
    }
}

enum class TimelineLineType {
    SOLID,
    DASHED,
    NONE
}

@Composable
private fun TimelineItem(
    time: String,
    title: String,
    lineType: TimelineLineType,
    isAi: Boolean,
    modifier: Modifier = Modifier,
    aiAlpha: Float = 1f,
    aiOffsetY: Float = 0f,
    aiScale: Float = 1f,
    onAiClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = CueSpacing.Sm)
    ) {
        // Time label column (fixed width)
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium,
            color = CueTextSecondary,
            modifier = Modifier
                .width(56.dp)
                .padding(top = if (isAi) CueSpacing.Sm else 2.dp)
        )

        // Connector Column with vertical line
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .weight(1f)
                .padding(start = CueSpacing.Sm)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Vertical line canvas
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (isAi) 58.dp else 44.dp)
                ) {
                    when (lineType) {
                        TimelineLineType.SOLID -> {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawLine(
                                    color = CueBorder,
                                    start = Offset(1.dp.toPx(), 0f),
                                    end = Offset(1.dp.toPx(), size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        TimelineLineType.DASHED -> {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawLine(
                                    color = CueTextTertiary,
                                    start = Offset(1.dp.toPx(), 0f),
                                    end = Offset(1.dp.toPx(), size.height),
                                    strokeWidth = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(10f, 10f),
                                        0f
                                    )
                                )
                            }
                        }
                        TimelineLineType.NONE -> {
                            // No line below the last item
                        }
                    }
                }

                Spacer(modifier = Modifier.width(CueSpacing.Lg))

                // Content slot
                if (isAi) {
                    // Interactive AI Highlight Box
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = aiAlpha
                                translationY = aiOffsetY.dp.toPx()
                                scaleX = aiScale
                                scaleY = aiScale
                            }
                            .clip(RoundedCornerShape(CueSpacing.Md))
                            .background(CueAccentContainer)
                            .clickable(onClick = onAiClick)
                            .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Sm)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Xs)
                        ) {
                            Text(
                                text = "✦",
                                color = CueAccentStrong,
                                fontSize = 14.sp
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = CueAccentStrong
                            )
                        }
                    }
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CueTextPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF9)
@Composable
private fun Step3TimelinePreview() {
    SmartReminderTheme {
        Step3TimelineScreen()
    }
}
