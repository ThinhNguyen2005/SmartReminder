package com.smartreminder.ui.today.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.domain.model.today.DailyProgress
import com.smartreminder.ui.theme.CueAccent
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.CueSurfaceSubtle
import com.smartreminder.ui.theme.CueTextSecondary

@Composable
fun DailyProgressSection(
    progress: DailyProgress,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 500),
        label = "DailyProgressAnimation"
    )

    val progressDesc = stringResource(
        R.string.today_progress_counter,
        progress.completedCount,
        progress.totalCount
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = progressDesc
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = stringResource(R.string.today_section_progress),
                style = MaterialTheme.typography.labelSmall,
                color = CueTextSecondary
            )
            Text(
                text = progressDesc,
                style = MaterialTheme.typography.labelLarge,
                color = CueAccent
            )
        }

        Spacer(modifier = Modifier.height(CueSpacing.Sm))

        // Custom smooth pill progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(CueSpacing.Xs))
                .background(CueSurfaceSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(CueSpacing.Xs))
                    .background(CueAccent)
            )
        }
    }
}
