package com.smartreminder.ui.today.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueSpacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun NowTimeIndicator(
    currentTime: LocalTime,
    modifier: Modifier = Modifier
) {
    val formattedTime = currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot container aligned with timeline axis (width 48dp)
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(CueAccent)
            )
        }

        Spacer(modifier = Modifier.width(CueSpacing.Sm))

        // "10:12 — Now" text badge
        Text(
            text = stringResource(R.string.today_now_label, formattedTime),
            style = MaterialTheme.typography.labelSmall,
            color = CueAccent
        )

        Spacer(modifier = Modifier.width(CueSpacing.Sm))

        // Horizontal line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(CueAccent)
        )
    }
}
