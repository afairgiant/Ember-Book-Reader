package com.ember.reader.ui.settings.stats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ember.reader.R

/** The headline figures: the streak, and what else is known about it. */
@Composable
fun HeroSummarySection(stats: StatsData, modifier: Modifier = Modifier) {
    val streak = stats.grimmoryStreak?.takeIf { stats.isGrimmoryConnected }
    val figures = if (streak != null) {
        listOf(
            streakFigure(streak.currentStreak),
            StatFigure(stringResource(R.string.longest_streak), "${streak.longestStreak}"),
            StatFigure(stringResource(R.string.total_reading_days), "${streak.totalReadingDays}")
        )
    } else {
        listOf(
            streakFigure(stats.currentStreak),
            StatFigure(stringResource(R.string.all_time), stats.allTimeSeconds.formatDuration())
        )
    }
    StatFigureRow(figures, modifier)
}

@Composable
private fun streakFigure(days: Int) = StatFigure(
    label = stringResource(R.string.day_streak),
    value = "$days",
    icon = Icons.Default.LocalFireDepartment
)
