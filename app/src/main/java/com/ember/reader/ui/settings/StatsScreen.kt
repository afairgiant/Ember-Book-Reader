package com.ember.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.grimmory.GrimmoryFavoriteDay
import com.ember.reader.core.grimmory.GrimmoryPeakHour
import com.ember.reader.core.grimmory.GrimmoryStreakDay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onNavigateBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Local Stats (This Device) ──

            // Streak + All Time
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${stats.currentStreak}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.day_streak),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stats.allTimeSeconds.formatDuration(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.all_time),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Time cards: Today / Week / Month
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeCard(stringResource(R.string.today), stats.todaySeconds, Modifier.weight(1f))
                    TimeCard(stringResource(R.string.this_week), stats.weekSeconds, Modifier.weight(1f))
                    TimeCard(stringResource(R.string.this_month), stats.monthSeconds, Modifier.weight(1f))
                }
            }

            // Estimated completion
            stats.estimatedMinutesToFinish?.let { minutes ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
            }

            // Reading Activity (local 12-week calendar)
            item {
                Text(
                    text = stringResource(R.string.reading_activity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                StreakCalendar(readingDays = stats.readingDays)
            }

            // Recent sessions
            if (stats.recentSessions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recent_sessions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(stats.recentSessions) { session ->
                    SessionCard(
                        session = session,
                        bookTitle = stats.bookTitles[session.bookId] ?: "Unknown"
                    )
                }
            }

            if (stats.recentSessions.isEmpty() && stats.allTimeSeconds == 0L) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_sessions_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Grimmory Stats (Cross-Device) ──

            if (stats.isGrimmoryConnected) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Grimmory section title
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.grimmory_stats),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Streak: current, longest, total reading days
                stats.grimmoryStreak?.let { streak ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMiniCard(
                                label = stringResource(R.string.day_streak),
                                value = "${streak.currentStreak}",
                                modifier = Modifier.weight(1f),
                            )
                            StatMiniCard(
                                label = stringResource(R.string.longest_streak),
                                value = "${streak.longestStreak}",
                                modifier = Modifier.weight(1f),
                            )
                            StatMiniCard(
                                label = stringResource(R.string.total_reading_days),
                                value = "${streak.totalReadingDays}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // 52-week activity calendar
                    if (streak.last52Weeks.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.reading_activity),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            GrimmoryStreakCalendar(weeks = streak.last52Weeks)
                        }
                    }
                }

                // Library Overview
                stats.bookDistributions?.let { distributions ->
                    val statusMap = distributions.statusDistribution.associate { it.status to it.count }
                    val readCount = statusMap["READ"] ?: 0
                    val readingCount = statusMap["READING"] ?: 0
                    val unreadCount = statusMap["UNREAD"] ?: 0

                    item {
                        Text(
                            text = stringResource(R.string.library_overview),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMiniCard(
                                label = stringResource(R.string.books_read),
                                value = "$readCount",
                                modifier = Modifier.weight(1f),
                            )
                            StatMiniCard(
                                label = stringResource(R.string.books_reading),
                                value = "$readingCount",
                                modifier = Modifier.weight(1f),
                            )
                            StatMiniCard(
                                label = stringResource(R.string.books_unread),
                                value = "$unreadCount",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Peak Reading Hours
                stats.peakHours?.let { hours ->
                    if (hours.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.peak_reading_hours),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            PeakHoursChart(hours)
                        }
                    }
                }

                // Favorite Days
                stats.favoriteDays?.let { days ->
                    if (days.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.favorite_days),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            FavoriteDaysChart(days)
                        }
                    }
                }

                // Top Genres
                stats.genreStats?.let { genres ->
                    if (genres.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.top_genres),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            TopGenresCard(genres)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeCard(label: String, seconds: Long, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = seconds.formatDuration(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GrimmoryStreakCalendar(weeks: List<GrimmoryStreakDay>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text("Sun", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Group the 52-week data into columns of 7 (weeks)
            // Data is ordered chronologically, we want most recent on the right
            val daysByWeek = weeks.chunked(7)

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                reverseLayout = false
            ) {
                items(daysByWeek.size) { weekIdx ->
                    val weekDays = daysByWeek[weekIdx]
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (day in weekDays) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (day.active) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        }
                                    )
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
    val weeks = 12

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text("Sun", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                reverseLayout = true
            ) {
                items(weeks) { weekIdx ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (dayOfWeek in 0 until 7) {
                            val daysAgo = weekIdx * 7 + (6 - dayOfWeek)
                            val date = today.minusDays(daysAgo.toLong())
                            val epochDay = date.toEpochDay()
                            val hasActivity = epochDay in readingDays ||
                                readingDays.any {
                                    it == date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() / 86400000
                                }

                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (hasActivity) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeakHoursChart(hours: List<GrimmoryPeakHour>) {
    val maxDuration = hours.maxOfOrNull { it.totalDurationSeconds } ?: 1L

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Show all 24 hours as a compact bar chart
            val hourMap = hours.associate { it.hourOfDay to it.totalDurationSeconds }
            for (hour in 0..23) {
                val duration = hourMap[hour] ?: 0L
                if (duration > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = String.format("%02d:00", hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(44.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                        ) {
                            val fraction = duration.toFloat() / maxDuration
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = duration.formatDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteDaysChart(days: List<GrimmoryFavoriteDay>) {
    val maxDuration = days.maxOfOrNull { it.totalDurationSeconds } ?: 1L
    // Sort by dayOfWeek (1=Sunday in Grimmory, but display Mon-Sun)
    val orderedDays = days.sortedBy {
        // Grimmory: 1=Sunday, 2=Monday ... 7=Saturday
        // Reorder to Mon(2), Tue(3), Wed(4), Thu(5), Fri(6), Sat(7), Sun(1)
        if (it.dayOfWeek == 1) 8 else it.dayOfWeek
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (day in orderedDays) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = day.dayName.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                    ) {
                        val fraction = day.totalDurationSeconds.toFloat() / maxDuration
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = day.totalDurationSeconds.formatDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopGenresCard(genres: List<com.ember.reader.core.grimmory.GrimmoryGenreStat>) {
    val maxDuration = genres.maxOfOrNull { it.totalDurationSeconds } ?: 1L

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (genre in genres) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = genre.genre,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${genre.bookCount} books",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                    ) {
                        val fraction = genre.totalDurationSeconds.toFloat() / maxDuration
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }
    }
}

private val sessionDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

@Composable
private fun SessionCard(session: com.ember.reader.core.model.ReadingSession, bookTitle: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.startTime
                        .atZone(ZoneId.systemDefault())
                        .format(sessionDateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = session.durationSeconds.formatDuration(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val delta = ((session.endProgress - session.startProgress) * 100).roundToInt()
                if (delta > 0) {
                    Text(
                        text = "+$delta%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
