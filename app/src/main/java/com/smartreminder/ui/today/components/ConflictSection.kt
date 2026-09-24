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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.domain.model.today.ScheduleConflict
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueCta
import com.smartreminder.ui.theme.CueError
import com.smartreminder.ui.theme.CueErrorContainer
import com.smartreminder.ui.theme.CueOnCta
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurface
import com.smartreminder.ui.theme.CueSurfaceSubtle
import com.smartreminder.ui.theme.CueTextPrimary
import java.time.format.DateTimeFormatter

@Composable
fun ConflictSection(
    conflict: ScheduleConflict,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatted = conflict.conflictTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Sm),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline axis column with Red error node
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CueError
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xs))

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(CueError)
            )
        }

        Spacer(modifier = Modifier.width(CueSpacing.Sm))

        // Conflict Content Container
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Conflict Banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = CueSpacing.Sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = CueError,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(CueSpacing.Xs))
                Text(
                    text = stringResource(R.string.today_conflict_banner_title),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CueError
                )
            }

            // Primary Card (e.g. Project Meeting)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CueError, RoundedCornerShape(CueSpacing.Lg)),
                shape = RoundedCornerShape(CueSpacing.Lg),
                color = CueErrorContainer
            ) {
                Column(
                    modifier = Modifier.padding(CueSpacing.Md)
                ) {
                    Text(
                        text = conflict.primaryTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CueError
                    )
                    if (!conflict.locationOrDetails.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = conflict.locationOrDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = CueError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Sm))

            // Overlapping Card (e.g. Study Session)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CueBorder, RoundedCornerShape(CueSpacing.Lg)),
                shape = RoundedCornerShape(CueSpacing.Lg),
                color = CueSurfaceSubtle
            ) {
                Column(
                    modifier = Modifier.padding(CueSpacing.Md)
                ) {
                    Text(
                        text = conflict.conflictingTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = CueTextPrimary
                    )
                }
            }

            // Intelligence Layer: Resolution Suggestion
            if (conflict.suggestedMoveTime != null && conflict.suggestedItemTitle != null) {
                Spacer(modifier = Modifier.height(CueSpacing.Md))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CueAccent, RoundedCornerShape(CueSpacing.Lg)),
                    shape = RoundedCornerShape(CueSpacing.Lg),
                    color = CueAccentContainer
                ) {
                    Row(
                        modifier = Modifier.padding(CueSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CueSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = CueAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(CueSpacing.Md))

                        val moveTimeFormatted = conflict.suggestedMoveTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                        Text(
                            text = stringResource(R.string.today_conflict_suggestion, conflict.suggestedItemTitle, moveTimeFormatted),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = CueTextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(CueSpacing.Sm))

                        Button(
                            onClick = onReviewClick,
                            shape = RoundedCornerShape(CueSpacing.Xl),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CueCta,
                                contentColor = CueOnCta
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.today_conflict_action_review),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
