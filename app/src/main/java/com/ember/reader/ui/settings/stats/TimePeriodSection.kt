package com.ember.reader.ui.settings.stats

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ember.reader.R

@Composable
fun TimePeriodSection(stats: StatsData, modifier: Modifier = Modifier) {
    StatFigureRow(
        figures = listOf(
            StatFigure(stringResource(R.string.today), stats.todaySeconds.formatDuration()),
            StatFigure(stringResource(R.string.this_week), stats.weekSeconds.formatDuration()),
            StatFigure(stringResource(R.string.this_month), stats.monthSeconds.formatDuration())
        ),
        modifier = modifier
    )
}

@Composable
fun EstimatedCompletionCard(minutes: Long, modifier: Modifier = Modifier) {
    StatCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.estimated_time_to_finish),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = (minutes * 60).formatDuration(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
