package com.smartreminder.ui.schedules.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.smartreminder.R
import com.smartreminder.ui.schedules.editor.RoutineItemDraftUiModel
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RoutineTimelineEditor(
    items: List<RoutineItemDraftUiModel>,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern(context), Locale.getDefault())

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        items.forEach { item ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = CueTheme.colors.surfaceSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CueSpacing.Xxxl)
                        .padding(horizontal = CueSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
                ) {
                    Text(
                        text = item.scheduledTime.format(timeFormatter),
                        style = MaterialTheme.typography.labelLarge,
                        color = CueTheme.colors.accentStrong
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CueTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onEditItem(item.draftKey) }) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.routine_editor_edit_item)
                        )
                    }
                    IconButton(onClick = { onRemoveItem(item.draftKey) }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.routine_editor_remove_item)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(CueSpacing.Xs))
        FilledTonalButton(
            onClick = onAddItem,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(CueSpacing.Sm))
            Text(stringResource(R.string.routine_editor_add_item))
        }
    }
}

private fun timePattern(context: android.content.Context): String = if (android.text.format.DateFormat.is24HourFormat(context)) {
    "HH:mm"
} else {
    "hh:mm a"
}
