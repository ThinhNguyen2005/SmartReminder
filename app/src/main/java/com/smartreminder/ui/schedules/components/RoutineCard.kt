package com.smartreminder.ui.schedules.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.smartreminder.R
import com.smartreminder.ui.schedules.RoutineCardUiModel
import com.smartreminder.ui.schedules.formatter.RecurrenceFormatter
import com.smartreminder.ui.schedules.formatter.RecurrenceLabels
import com.smartreminder.ui.theme.CueSpacing
import java.time.DayOfWeek

@Composable
fun RoutineCard(
    routine: RoutineCardUiModel,
    onOpen: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = RecurrenceLabels(
        everyDay = stringResource(R.string.schedules_every_day),
        dayAbbreviations = mapOf(
            DayOfWeek.MONDAY to stringResource(R.string.day_mon_short),
            DayOfWeek.TUESDAY to stringResource(R.string.day_tue_short),
            DayOfWeek.WEDNESDAY to stringResource(R.string.day_wed_short),
            DayOfWeek.THURSDAY to stringResource(R.string.day_thu_short),
            DayOfWeek.FRIDAY to stringResource(R.string.day_fri_short),
            DayOfWeek.SATURDAY to stringResource(R.string.day_sat_short),
            DayOfWeek.SUNDAY to stringResource(R.string.day_sun_short)
        )
    )
    val recurrenceSummary = RecurrenceFormatter.format(routine.recurrence, labels)
    val itemsSummary = pluralStringResource(
        id = R.plurals.schedules_items_count,
        count = routine.itemCount,
        routine.itemCount
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CueSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sibling 1: Clickable Content (OpenRoutine)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = CueSpacing.Xxxl)
                    .clickable(onClick = onOpen)
                    .padding(end = CueSpacing.Md)
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(CueSpacing.Xs))
                Text(
                    text = recurrenceSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(CueSpacing.Xs))
                Text(
                    text = itemsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Sibling 2: Switch (SetRoutineEnabled)
            Switch(
                checked = routine.enabled,
                onCheckedChange = onSetEnabled,
                modifier = Modifier.defaultMinSize(
                    minWidth = CueSpacing.Xxxl,
                    minHeight = CueSpacing.Xxxl
                )
            )
        }
    }
}
