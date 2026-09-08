package com.smartreminder.ui.today.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smartreminder.domain.model.today.AiSuggestion
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurface
import com.smartreminder.ui.theme.CueTextPrimary

@Composable
fun AiSuggestionCard(
    suggestion: AiSuggestion,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CueBorder, RoundedCornerShape(CueSpacing.Lg)),
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueSurface
    ) {
        Row(
            modifier = Modifier.padding(CueSpacing.Lg),
            verticalAlignment = Alignment.Top
        ) {
            // Sparkle icon badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CueAccentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = CueAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(CueSpacing.Lg))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                val displayMessage = when {
                    suggestion.focusWindowStart != null && suggestion.focusWindowEnd != null -> {
                        androidx.compose.ui.res.stringResource(
                            com.smartreminder.R.string.today_ai_focus_message,
                            suggestion.durationMinutes,
                            suggestion.focusWindowStart.format(timeFormatter),
                            suggestion.focusWindowEnd.format(timeFormatter)
                        )
                    }
                    suggestion.focusWindowStart != null -> {
                        androidx.compose.ui.res.stringResource(
                            com.smartreminder.R.string.today_ai_free_message,
                            suggestion.durationMinutes,
                            suggestion.focusWindowStart.format(timeFormatter)
                        )
                    }
                    else -> suggestion.message
                }

                Text(
                    text = displayMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTextPrimary
                )

                Spacer(modifier = Modifier.height(CueSpacing.Sm))

                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(CueSpacing.Xl),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CueAccentContainer,
                        contentColor = CueAccent
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.smartreminder.R.string.today_view_plan),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
