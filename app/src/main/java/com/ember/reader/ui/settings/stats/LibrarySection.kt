package com.ember.reader.ui.settings.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ember.reader.R
import com.ember.reader.core.grimmory.GrimmoryGenreStat

@Composable
fun LibrarySection(stats: StatsData, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.library_overview))

        stats.bookDistributions?.let { distributions ->
            val statusMap = distributions.statusDistribution.associate { it.status to it.count }
            val readCount = statusMap["READ"] ?: 0
            val readingCount = statusMap["READING"] ?: 0
            val unreadCount = statusMap["UNREAD"] ?: 0
            val total = readCount + readingCount + unreadCount

            if (total > 0) {
                StatusSegmentedBar(
                    readCount = readCount,
                    readingCount = readingCount,
                    unreadCount = unreadCount,
                    total = total,
                )
            }
        }

        stats.genreStats?.let { genres ->
            if (genres.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.top_genres))
                TopGenresChart(genres)
            }
        }
    }
}

@Composable
private fun StatusSegmentedBar(
    readCount: Int,
    readingCount: Int,
    unreadCount: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val readFraction = readCount.toFloat() / total
    val readingFraction = readingCount.toFloat() / total
    val unreadFraction = unreadCount.toFloat() / total

    val readColor = MaterialTheme.colorScheme.primary
    val readingColor = MaterialTheme.colorScheme.tertiary
    val unreadColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Segmented bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                if (readFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(readFraction)
                            .height(24.dp)
                            .background(readColor),
                    )
                }
                if (readingFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(readingFraction)
                            .height(24.dp)
                            .background(readingColor),
                    )
                }
                if (unreadFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(unreadFraction)
                            .height(24.dp)
                            .background(unreadColor),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LegendItem(
                    color = readColor,
                    label = stringResource(R.string.books_read),
                    count = readCount,
                )
                LegendItem(
                    color = readingColor,
                    label = stringResource(R.string.books_reading),
                    count = readingCount,
                )
                LegendItem(
                    color = unreadColor,
                    label = stringResource(R.string.books_unread),
                    count = unreadCount,
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopGenresChart(genres: List<GrimmoryGenreStat>) {
    val maxDuration = genres.maxOfOrNull { it.totalDurationSeconds } ?: 1L

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (genre in genres) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = genre.genre,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${genre.bookCount} books",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                    ) {
                        val fraction = genre.totalDurationSeconds.toFloat() / maxDuration
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                ),
                        )
                    }
                }
            }
        }
    }
}
