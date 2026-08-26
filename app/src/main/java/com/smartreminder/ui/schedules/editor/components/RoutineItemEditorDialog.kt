package com.smartreminder.ui.schedules.editor.components

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.smartreminder.R
import com.smartreminder.ui.schedules.editor.RoutineEditorAction
import com.smartreminder.ui.schedules.editor.RoutineItemEditorUiState
import com.smartreminder.ui.theme.CueTheme
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineItemEditorDialog(
    itemEditor: RoutineItemEditorUiState,
    onAction: (RoutineEditorAction) -> Unit
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)

    key(itemEditor.draftKey) {
        val timePickerState = rememberTimePickerState(
            initialHour = itemEditor.scheduledTime.hour,
            initialMinute = itemEditor.scheduledTime.minute,
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { onAction(RoutineEditorAction.DismissItemEditor) },
            title = {
                Text(
                    stringResource(
                        if (itemEditor.draftKey == null) {
                            R.string.routine_editor_add_item
                        } else {
                            R.string.routine_editor_edit_item
                        }
                    )
                )
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = itemEditor.title,
                        onValueChange = { onAction(RoutineEditorAction.ChangeItemTitle(it)) },
                        label = { Text(stringResource(R.string.routine_editor_item_title_label)) },
                        isError = itemEditor.titleError != null,
                        supportingText = {
                            if (itemEditor.titleError != null) {
                                Text(stringResource(R.string.routine_editor_item_title_error_required))
                            }
                        }
                    )
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = CueTheme.colors.surfaceSubtle,
                            selectorColor = CueTheme.colors.accent,
                            clockDialSelectedContentColor = CueTheme.colors.onCta,
                            clockDialUnselectedContentColor = CueTheme.colors.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(
                            RoutineEditorAction.ChangeItemTime(
                                LocalTime.of(timePickerState.hour, timePickerState.minute)
                            )
                        )
                        onAction(RoutineEditorAction.ConfirmItemEditor)
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(RoutineEditorAction.DismissItemEditor) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
