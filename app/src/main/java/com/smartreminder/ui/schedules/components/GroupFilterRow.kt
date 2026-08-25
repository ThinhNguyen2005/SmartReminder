package com.smartreminder.ui.schedules.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smartreminder.R
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.ui.schedules.GroupFilterUiModel
import com.smartreminder.ui.theme.CueSpacing

@Composable
fun GroupFilterRow(
    groups: List<GroupFilterUiModel>,
    selectedGroupId: ScheduleGroupId?,
    onSelectGroup: (ScheduleGroupId?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All" filter chip
        FilterChip(
            selected = selectedGroupId == null,
            onClick = { onSelectGroup(null) },
            label = {
                Text(
                    text = stringResource(R.string.schedules_filter_all),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Active Group chips
        groups.forEach { group ->
            val isSelected = selectedGroupId == group.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelectGroup(group.id) },
                label = {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
