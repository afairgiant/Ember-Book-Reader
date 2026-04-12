package com.ember.reader.ui.settings.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.grimmory.GrimmoryFavoriteDay
import com.ember.reader.core.grimmory.GrimmoryPeakHour

@Composable
fun PatternsSection(stats: StatsData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.stats_reading_patterns))

        stats.peakHours?.let { hours ->
            if (hours.isNotEmpty()) {
                PeakHoursChart(hours)
            }
        }

        stats.favoriteDays?.let { days ->
            if (days.isNotEmpty()) {
                FavoriteDaysChart(days)
            }
        }
    }
}

@Composable
private fun PeakHoursChart(hours: List<GrimmoryPeakHour>) {
    val entries = (0..23).mapNotNull { hour ->
        val peakHour = hours.find { it.hourOfDay == hour }
        if (peakHour != null && peakHour.totalDurationSeconds > 0) {
            BarChartEntry(
                label = String.format("%02d:00", hour),
                value = peakHour.totalDurationSeconds,
                trailingText = peakHour.totalDurationSeconds.formatDuration(),
            )
        } else {
            null
        }
    }

    HorizontalBarChart(
        entries = entries,
        barColor = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun FavoriteDaysChart(days: List<GrimmoryFavoriteDay>) {
    val orderedDays = days.sortedBy {
        if (it.dayOfWeek == 1) 8 else it.dayOfWeek
    }

    val entries = orderedDays.map { day ->
        BarChartEntry(
            label = day.dayName.take(3),
            value = day.totalDurationSeconds,
            trailingText = day.totalDurationSeconds.formatDuration(),
        )
    }

    HorizontalBarChart(
        entries = entries,
        barColor = MaterialTheme.colorScheme.tertiary,
        barHeight = 20.dp,
        labelWidth = 32.dp,
    )
}
