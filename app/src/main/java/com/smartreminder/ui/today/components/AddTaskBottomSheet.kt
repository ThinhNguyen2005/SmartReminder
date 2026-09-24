package com.smartreminder.ui.today.components

import android.app.TimePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.domain.model.task.TaskPriority
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueCta
import com.smartreminder.ui.theme.CueOnCta
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurface
import com.smartreminder.ui.theme.CueSurfaceSubtle
import com.smartreminder.ui.theme.CueTextPrimary
import com.smartreminder.ui.theme.CueTextSecondary
import com.smartreminder.ui.theme.CueTextTertiary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Bottom Sheet for creating a custom task with title, start time, duration, and priority.
 * Adheres strictly to CODING_STANDARDS.md and DESIGN.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskBottomSheet(
    initialTime: LocalTime?,
    onDismiss: () -> Unit,
    onSave: (title: String, startTime: LocalTime, durationMinutes: Int, priority: TaskPriority) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var taskTitle by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf(initialTime ?: LocalTime.now().plusMinutes(15).withSecond(0).withNano(0)) }
    var durationMinutes by remember { mutableIntStateOf(60) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CueSurface,
        shape = RoundedCornerShape(topStart = CueSpacing.Xl, topEnd = CueSpacing.Xl),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Xl)
                .padding(bottom = CueSpacing.Xl)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.today_add_task_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CueTextPrimary
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.today_add_task_cancel),
                        tint = CueTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Lg))

            // Task Title Input
            Text(
                text = stringResource(R.string.today_add_task_name_label),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CueTextSecondary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xs))

            OutlinedTextField(
                value = taskTitle,
                onValueChange = { taskTitle = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.today_add_task_name_placeholder),
                        color = CueTextTertiary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CueSpacing.Lg),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CueAccent,
                    unfocusedBorderColor = CueBorder,
                    focusedTextColor = CueTextPrimary,
                    unfocusedTextColor = CueTextPrimary,
                    cursorColor = CueAccent
                )
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // Start Time Row
            Text(
                text = stringResource(R.string.today_add_task_start_time),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CueTextSecondary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Sm))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(CueSpacing.Lg))
                    .border(1.dp, CueBorder, RoundedCornerShape(CueSpacing.Lg))
                    .clickable {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                startTime = LocalTime.of(hourOfDay, minute)
                            },
                            startTime.hour,
                            startTime.minute,
                            true
                        ).show()
                    },
                color = CueSurfaceSubtle,
                shape = RoundedCornerShape(CueSpacing.Lg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CueSpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = CueAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(CueSpacing.Md))
                        Text(
                            text = startTime.format(timeFormatter),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CueTextPrimary
                        )
                    }

                    Text(
                        text = stringResource(R.string.profile_edit),
                        style = MaterialTheme.typography.labelLarge,
                        color = CueAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // Duration Chips
            Text(
                text = stringResource(R.string.today_add_task_duration),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CueTextSecondary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
            ) {
                listOf(30, 45, 60, 90).forEach { dur ->
                    val isSelected = durationMinutes == dur
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(CueSpacing.Md))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CueAccent else CueBorder,
                                shape = RoundedCornerShape(CueSpacing.Md)
                            )
                            .clickable { durationMinutes = dur },
                        shape = RoundedCornerShape(CueSpacing.Md),
                        color = if (isSelected) CueAccentContainer else CueSurfaceSubtle
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${dur}m",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) CueAccent else CueTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Xl))

            // Priority Chips
            Text(
                text = stringResource(R.string.today_add_task_priority),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CueTextSecondary
            )

            Spacer(modifier = Modifier.height(CueSpacing.Sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md)
            ) {
                val priorities = listOf(
                    TaskPriority.NORMAL to stringResource(R.string.today_add_task_priority_normal),
                    TaskPriority.HIGH to stringResource(R.string.today_add_task_priority_high)
                )

                priorities.forEach { (prio, label) ->
                    val isSelected = priority == prio
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(CueSpacing.Md))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CueAccent else CueBorder,
                                shape = RoundedCornerShape(CueSpacing.Md)
                            )
                            .clickable { priority = prio },
                        shape = RoundedCornerShape(CueSpacing.Md),
                        color = if (isSelected) CueAccentContainer else CueSurfaceSubtle
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = CueSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Flag,
                                contentDescription = null,
                                tint = if (isSelected) CueAccent else CueTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(CueSpacing.Xs))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) CueAccent else CueTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(CueSpacing.Xxl))

            // Save CTA Button
            Button(
                onClick = {
                    if (taskTitle.isNotBlank()) {
                        onSave(taskTitle.trim(), startTime, durationMinutes, priority)
                    }
                },
                enabled = taskTitle.isNotBlank(),
                shape = RoundedCornerShape(CueSpacing.Xl),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CueCta,
                    contentColor = CueOnCta,
                    disabledContainerColor = CueSurfaceSubtle,
                    disabledContentColor = CueTextTertiary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = stringResource(R.string.today_add_task_save),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
