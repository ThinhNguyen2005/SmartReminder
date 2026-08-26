package com.smartreminder.ui.schedules.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.schedules.editor.components.GroupSelector
import com.smartreminder.ui.schedules.editor.components.RoutineItemEditorDialog
import com.smartreminder.ui.schedules.editor.components.RoutineTimelineEditor
import com.smartreminder.ui.schedules.editor.components.WeekdaySelector
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    uiState: RoutineEditorUiState,
    onAction: (RoutineEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = !uiState.isLoading) {
        onAction(RoutineEditorAction.BackPressed)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CueTheme.colors.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen Header / Top Bar (pinned, unified without nested Scaffold or TopAppBar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = CueSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = { onAction(RoutineEditorAction.BackPressed) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.routine_editor_back_description),
                            tint = CueTheme.colors.textPrimary
                        )
                    }
                    Text(
                        text = stringResource(
                            if (uiState.mode is RoutineEditorMode.Edit) {
                                R.string.routine_editor_edit_title
                            } else {
                                R.string.routine_editor_create_title
                            }
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = CueTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = { onAction(RoutineEditorAction.Save) },
                    enabled = !uiState.isLoading && !uiState.isSaving
                ) {
                    Text(
                        text = stringResource(R.string.routine_editor_save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!uiState.isLoading && !uiState.isSaving) {
                            CueTheme.colors.accent
                        } else {
                            CueTheme.colors.textTertiary
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CueTheme.colors.accent)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(CueSpacing.Lg)
                ) {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { onAction(RoutineEditorAction.ChangeName(it)) },
                        label = { Text(stringResource(R.string.routine_editor_name_label)) },
                        isError = uiState.nameError != null,
                        supportingText = {
                            if (uiState.nameError != null) {
                                Text(stringResource(R.string.routine_editor_name_error_required))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    GroupSelector(
                        selectedGroupId = uiState.selectedGroupId,
                        groups = uiState.groups,
                        onGroupSelected = { onAction(RoutineEditorAction.SelectGroup(it)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)) {
                        Text(
                            text = stringResource(R.string.routine_editor_recurrence_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = CueTheme.colors.textPrimary
                        )
                        WeekdaySelector(
                            selectedDays = uiState.selectedDays,
                            onDayToggled = { onAction(RoutineEditorAction.ToggleDay(it)) }
                        )
                        if (uiState.daysError != null) {
                            Text(
                                text = stringResource(R.string.routine_editor_recurrence_error_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = CueTheme.colors.error
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.routine_editor_enabled_label),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.enabled,
                            onCheckedChange = { onAction(RoutineEditorAction.SetEnabled(it)) }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)) {
                        Text(
                            text = stringResource(R.string.routine_editor_timeline_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = CueTheme.colors.textPrimary
                        )
                        RoutineTimelineEditor(
                            items = uiState.items,
                            onAddItem = { onAction(RoutineEditorAction.OpenAddItem) },
                            onEditItem = { onAction(RoutineEditorAction.OpenEditItem(it)) },
                            onRemoveItem = { onAction(RoutineEditorAction.RemoveItem(it)) }
                        )
                    }

                    if (uiState.saveError != null) {
                        Text(
                            text = stringResource(
                                when (uiState.saveError) {
                                    RoutineEditorError.LOAD_FAILED -> R.string.routine_editor_error_load
                                    RoutineEditorError.SAVE_FAILED -> R.string.routine_editor_error_save
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = CueTheme.colors.error
                        )
                    }

                    if (uiState.isSaving) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = CueTheme.colors.accent,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(stringResource(R.string.routine_editor_saving))
                        }
                    }

                    Spacer(modifier = Modifier.height(CueSpacing.Xl))
                }
            }
        }
    }

    uiState.itemEditor?.let { itemEditor ->
        RoutineItemEditorDialog(itemEditor = itemEditor, onAction = onAction)
    }

    if (uiState.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { onAction(RoutineEditorAction.CancelDiscard) },
            title = { Text(stringResource(R.string.routine_editor_discard_title)) },
            text = { Text(stringResource(R.string.routine_editor_discard_message)) },
            confirmButton = {
                Button(onClick = { onAction(RoutineEditorAction.ConfirmDiscard) }) {
                    Text(stringResource(R.string.routine_editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(RoutineEditorAction.CancelDiscard) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineEditorScreenCreatePreview() {
    SmartReminderTheme {
        RoutineEditorScreen(
            uiState = RoutineEditorUiState(
                mode = RoutineEditorMode.Create,
                name = "Morning Routine",
                selectedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                enabled = true
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineEditorScreenLoadingPreview() {
    SmartReminderTheme {
        RoutineEditorScreen(
            uiState = RoutineEditorUiState(
                mode = RoutineEditorMode.Create,
                isLoading = true
            ),
            onAction = {}
        )
    }
}
