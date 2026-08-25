package com.smartreminder.ui.schedules.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.smartreminder.R
import com.smartreminder.ui.theme.CueSpacing

@Composable
fun SchedulesEmptyState(
    groupName: String?,
    onCreateRoutine: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = CueSpacing.Xxl,
                vertical = CueSpacing.Xxxl + CueSpacing.Md
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = if (groupName != null) {
            stringResource(R.string.schedules_empty_filtered_title, groupName)
        } else {
            stringResource(R.string.schedules_empty_all_title)
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (groupName == null) {
            Spacer(modifier = Modifier.height(CueSpacing.Sm))
            Text(
                text = stringResource(R.string.schedules_empty_all_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(CueSpacing.Xl))

        Button(
            onClick = onCreateRoutine
        ) {
            Text(text = stringResource(R.string.schedules_create_routine_button))
        }
    }
}
