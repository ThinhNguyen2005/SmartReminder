package com.smartreminder.ui.schedules.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smartreminder.R
import com.smartreminder.ui.theme.CueSpacing
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekdaySelector(
    selectedDays: Set<DayOfWeek>,
    onDayToggled: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selectedDays,
                onClick = { onDayToggled(day) },
                label = { Text(stringResource(day.shortLabelResource())) }
            )
        }
    }
}

private fun DayOfWeek.shortLabelResource(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_mon_short
    DayOfWeek.TUESDAY -> R.string.day_tue_short
    DayOfWeek.WEDNESDAY -> R.string.day_wed_short
    DayOfWeek.THURSDAY -> R.string.day_thu_short
    DayOfWeek.FRIDAY -> R.string.day_fri_short
    DayOfWeek.SATURDAY -> R.string.day_sat_short
    DayOfWeek.SUNDAY -> R.string.day_sun_short
}
