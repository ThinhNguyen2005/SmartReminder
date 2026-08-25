package com.smartreminder.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.smartreminder.R
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.SmartReminderTheme

@Composable
fun TodayPlaceholderScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(CueSpacing.Xl)
    ) {
        Text(
            text = stringResource(R.string.nav_today),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(CueSpacing.Sm))
        Text(
            text = stringResource(R.string.placeholder_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayPlaceholderScreenPreview() {
    SmartReminderTheme {
        TodayPlaceholderScreen()
    }
}
