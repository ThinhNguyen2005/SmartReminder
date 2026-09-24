package com.smartreminder.ui.today.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.theme.CueBorderStrong
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueTextMuted
import com.smartreminder.ui.theme.CueTextSecondary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun FreeSlotCard(
    startTime: LocalTime,
    durationMinutes: Int,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatted = startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    val hours = durationMinutes / 60
    val mins = durationMinutes % 60
    val durationText = when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline axis column
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = CueTextMuted
            )

            Spacer(modifier = Modifier.height(CueSpacing.Xs))

            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = CueTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.width(CueSpacing.Sm))

        // Dashed styled surface
        Surface(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, CueBorderStrong, RoundedCornerShape(CueSpacing.Lg))
                .clickable(
                    role = Role.Button,
                    onClick = onAddClick
                ),
            shape = RoundedCornerShape(CueSpacing.Lg),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.today_free_block_label, durationText),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                    color = CueTextSecondary
                )

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(CueSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.today_free_slot_action_add),
                        tint = CueTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.today_free_slot_action_add),
                        style = MaterialTheme.typography.labelMedium,
                        color = CueTextSecondary
                    )
                }
            }
        }
    }
}
