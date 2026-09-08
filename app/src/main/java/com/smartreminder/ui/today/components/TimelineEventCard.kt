package com.smartreminder.ui.today.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartreminder.R
import com.smartreminder.domain.model.task.TaskPriority
import com.smartreminder.domain.model.today.TodayTimelineItem.RoutineEvent
import com.smartreminder.domain.model.today.TodayTimelineItem.SuggestedFocusBlock
import com.smartreminder.domain.model.today.TodayTimelineItem.TaskEvent
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueError
import com.smartreminder.ui.theme.CueErrorContainer
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurface
import com.smartreminder.ui.theme.CueSurfaceSubtle
import com.smartreminder.ui.theme.CueTextMuted
import com.smartreminder.ui.theme.CueTextPrimary
import com.smartreminder.ui.theme.CueTextSecondary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TimelineEventCard(
    startTime: LocalTime,
    title: String,
    categoryTag: String?,
    isPast: Boolean,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
    priority: TaskPriority = TaskPriority.NORMAL,
    isVirtual: Boolean = false,
    attendees: Int = 0,
    onItemClick: () -> Unit = {},
    onToggleCompletion: ((Boolean) -> Unit)? = null
) {
    val timeFormatted = startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    val alpha = if (isPast && !isCompleted) 0.6f else 1.0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(vertical = CueSpacing.Xs),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline axis column (Time label + Dot node)
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = if (isPast) CueTextMuted else CueTextSecondary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xs))

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) CueAccent else if (isPast) CueBorder else CueAccent)
            )
        }

        Spacer(modifier = Modifier.width(CueSpacing.Sm))

        // Content Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, CueBorder, RoundedCornerShape(CueSpacing.Lg))
                .clickable(
                    role = Role.Button,
                    onClick = onItemClick
                ),
            shape = RoundedCornerShape(CueSpacing.Lg),
            color = CueSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CueSpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Optional completion toggle button for tasks
                if (onToggleCompletion != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, if (isCompleted) CueAccent else CueBorder, CircleShape)
                            .background(if (isCompleted) CueAccent else CueSurface)
                            .clickable { onToggleCompletion(!isCompleted) }
                            .semantics { contentDescription = "Toggle $title" },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = CueSurface,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(CueSpacing.Md))
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CueTextPrimary,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )

                    Spacer(modifier = Modifier.height(CueSpacing.Xs))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!categoryTag.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CueSpacing.Sm))
                                    .background(CueSurfaceSubtle)
                                    .padding(horizontal = CueSpacing.Sm, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = CueTextSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = categoryTag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = CueTextSecondary
                                )
                            }
                        }

                        if (isVirtual) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CueSpacing.Sm))
                                    .background(CueSurfaceSubtle)
                                    .padding(horizontal = CueSpacing.Sm, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Videocam,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = CueTextSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.today_virtual),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = CueTextSecondary
                                )
                            }
                        }

                        if (attendees > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CueSpacing.Sm))
                                    .background(CueSurfaceSubtle)
                                    .padding(horizontal = CueSpacing.Sm, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = CueTextSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.today_members_count, attendees),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = CueTextSecondary
                                )
                            }
                        }

                        if (priority == TaskPriority.HIGH) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CueSpacing.Sm))
                                    .background(CueErrorContainer)
                                    .padding(horizontal = CueSpacing.Sm, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CueError)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.today_priority_high),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = CueError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
