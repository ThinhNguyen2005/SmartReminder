package com.smartreminder.ui.today.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.today.TodayViewMode
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueAccentContainer
import com.smartreminder.ui.theme.CueBorder
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurface
import com.smartreminder.ui.theme.CueTextPrimary
import com.smartreminder.ui.theme.CueTextSecondary

@Composable
fun TodayViewModeSelector(
    selectedMode: TodayViewMode,
    onSelectMode: (TodayViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        TodayViewMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            val titleRes = when (mode) {
                TodayViewMode.OVERVIEW -> R.string.today_view_mode_overview
                TodayViewMode.TIMELINE -> R.string.today_view_mode_timeline
                TodayViewMode.MINIMAL_STREAM -> R.string.today_view_mode_minimal
                TodayViewMode.BUSY_CONFLICT -> R.string.today_view_mode_conflict
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(CueSpacing.Md))
                    .background(if (isSelected) CueAccentContainer else CueSurface)
                    .border(
                        1.dp,
                        if (isSelected) CueAccent else CueBorder,
                        RoundedCornerShape(CueSpacing.Md)
                    )
                    .clickable(
                        role = Role.Tab,
                        onClick = { onSelectMode(mode) }
                    )
                    .padding(vertical = CueSpacing.Sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) CueAccent else CueTextSecondary
                )
            }
        }
    }
}
