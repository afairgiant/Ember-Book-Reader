package com.ember.reader.ui.settings.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.grimmory.GrimmorySessionScatter
import com.ember.reader.core.grimmory.GrimmoryStreakDay
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ActivitySection(stats: StatsData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.reading_activity))

        if (stats.isGrimmoryConnected && stats.grimmoryStreak != null) {
            val weeks = stats.grimmoryStreak.last52Weeks
            if (weeks.isNotEmpty()) {
                GrimmoryStreakCalendar(weeks = weeks)
            }
        } else {
            StreakCalendar(readingDays = stats.readingDays)
        }

        stats.sessionScatter?.let { scatter ->
            if (scatter.isNotEmpty()) {
                SessionHeatmap(scatter)
            }
        }
    }
}

private val dayLabelsShort = listOf("", "M", "", "W", "", "F", "")

@Composable
private fun GrimmoryStreakCalendar(weeks: List<GrimmoryStreakDay>) {
    val daysByWeek = remember(weeks) { weeks.chunked(7) }

    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Day-of-week labels on the left
            Column(
                modifier = Modifier.padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (label in dayLabelsShort) {
                    Box(
                        modifier = Modifier.size(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Grid scrollable from right (most recent)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(
                        state = rememberScrollState(Int.MAX_VALUE),
                    ),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (weekDays in daysByWeek) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (day in weekDays) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (day.active) activeColor else inactiveColor,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCalendar(readingDays: Set<Long>) {
    val today = LocalDate.now()
    val numWeeks = 12

    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Day-of-week labels on the left
            Column(
                modifier = Modifier.padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (label in dayLabelsShort) {
                    Box(
                        modifier = Modifier.size(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Grid — 12 weeks fits without scrolling on most phones
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (weekIdx in (numWeeks - 1) downTo 0) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        for (dayOfWeek in 0 until 7) {
                            val daysAgo = weekIdx * 7 + (6 - dayOfWeek)
                            val date = today.minusDays(daysAgo.toLong())
                            val epochDay = date.toEpochDay()
                            val hasActivity = epochDay in readingDays ||
                                readingDays.any {
                                    it == date.atStartOfDay(ZoneId.systemDefault())
                                        .toInstant().toEpochMilli() / 86400000
                                }

                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (hasActivity) activeColor else inactiveColor,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Heatmap: rows = days of week (Mon–Sun), columns = 4-hour time buckets (6 buckets)
// Cell intensity = total duration in that bucket
@Composable
private fun SessionHeatmap(scatter: List<GrimmorySessionScatter>) {
    val bucketLabels = listOf("12a–4a", "4a–8a", "8a–12p", "12p–4p", "4p–8p", "8p–12a")
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Build grid: [dayIndex 0=Mon..6=Sun][bucketIndex 0..5] = total minutes
    val grid = remember(scatter) {
        val g = Array(7) { DoubleArray(6) }
        for (point in scatter) {
            // Grimmory dayOfWeek: 1=Sun, 2=Mon ... 7=Sat
            val dayIndex = when (point.dayOfWeek) {
                1 -> 6    // Sun → row 6
                else -> point.dayOfWeek - 2  // Mon=0, Tue=1, ..., Sat=5
            }
            val bucketIndex = (point.hourOfDay.toInt() / 4).coerceIn(0, 5)
            if (dayIndex in 0..6) {
                g[dayIndex][bucketIndex] += point.durationMinutes
            }
        }
        g
    }

    val maxValue = remember(grid) {
        grid.maxOf { row -> row.max() }.coerceAtLeast(1.0)
    }

    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    val fullColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_session_patterns),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Column headers (time buckets)
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(32.dp)) // space for row labels
                for (label in bucketLabels) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Heatmap rows
            for (dayIndex in 0 until 7) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dayLabels[dayIndex],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                    for (bucketIndex in 0 until 6) {
                        val value = grid[dayIndex][bucketIndex]
                        val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
                        val cellColor = if (value > 0.0) {
                            lerp(emptyColor, fullColor.copy(alpha = 0.85f), fraction)
                        } else {
                            emptyColor
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(cellColor),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Intensity legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                for (i in 0..4) {
                    val fraction = i / 4f
                    val color = if (i == 0) {
                        emptyColor
                    } else {
                        lerp(emptyColor, fullColor.copy(alpha = 0.85f), fraction)
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color),
                    )
                    if (i < 4) Spacer(modifier = Modifier.width(2.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
