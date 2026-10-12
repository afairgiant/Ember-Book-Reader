package com.ember.reader.ui.server
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.common.CoverImage
import com.ember.reader.ui.settings.components.SettingsGroup
import com.ember.reader.ui.settings.components.SettingsLabel
import java.time.LocalTime
import kotlin.math.roundToInt

private val ShelfCoverShape = RoundedCornerShape(12.dp)
private val ShelfCardWidth = 116.dp
private val ScreenEdge = 16.dp

/** "Good morning", "Good afternoon" or "Good evening" for an hour of the day (0-23). */
internal fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun ServerListScreen(
    onOpenSettings: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit = { _, _ -> },
    onOpenBookDetail: (bookId: String) -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: ServerListViewModel = hiltViewModel()
) {
    val recentlyReading by viewModel.recentlyReading.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val hasGrimmoryServer by viewModel.hasGrimmoryServer.collectAsStateWithLifecycle()
    val recentlyAddedLoading by viewModel.recentlyAddedLoading.collectAsStateWithLifecycle()
    val quickStats by viewModel.quickStats.collectAsStateWithLifecycle()
    val networkAvailable by com.ember.reader.ui.common.rememberNetworkAvailable()

    // The nav host already pads for the system bars.
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            com.ember.reader.ui.common.OfflineBanner(isOffline = !networkAvailable)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { HomeHeader(onOpenSettings = onOpenSettings) }

                // The book last read gets the hero; the rest of what is in progress follows below.
                val current = recentlyReading.firstOrNull()
                if (current != null) {
                    item(key = "hero") {
                        HomeHeroCard(
                            recent = current,
                            onContinue = { onOpenReader(current.book.id, current.book.format) },
                            modifier = Modifier.padding(horizontal = ScreenEdge)
                        )
                    }
                }

                quickStats?.let { stats ->
                    item(key = "stats") { QuickStatsCard(stats = stats, onClick = onOpenStats) }
                }

                val alsoReading = recentlyReading.drop(1)
                if (alsoReading.isNotEmpty()) {
                    item { ShelfLabel("Also reading") }
                    // Keyed on its first book so the row starts from the left again when a
                    // different book takes the hero, instead of staying scrolled part-way.
                    item(key = "also-${alsoReading.first().book.id}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenEdge),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(alsoReading, key = { it.book.id }) { recent ->
                                ContinueReadingCard(
                                    book = recent.book,
                                    percentage = recent.percentage,
                                    onClick = { onOpenReader(recent.book.id, recent.book.format) }
                                )
                            }
                        }
                    }
                }

                // Recently Added from Grimmory
                if (recentlyAdded.isNotEmpty() || (hasGrimmoryServer && recentlyAddedLoading)) {
                    item { ShelfLabel("Recently added") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenEdge),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (recentlyAdded.isNotEmpty()) {
                                items(recentlyAdded, key = { it.localBookId ?: "grimmory-${it.summary.id}" }) { recent ->
                                    RecentlyAddedCard(
                                        title = recent.summary.title,
                                        authors = recent.summary.authors.joinToString(", "),
                                        coverUrl = recent.coverUrl,
                                        onClick = {
                                            recent.localBookId?.let { onOpenBookDetail(it) }
                                        }
                                    )
                                }
                            } else {
                                items(4) {
                                    RecentlyAddedPlaceholderCard()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A greeting over the app's name, with the way into Settings. It scrolls with the page. */
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    val greeting = remember { greetingFor(LocalTime.now().hour) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenEdge, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SettingsLabel(greeting)
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

@Composable
private fun ShelfLabel(text: String) {
    SettingsLabel(text, modifier = Modifier.padding(start = ScreenEdge + 4.dp, top = 8.dp))
}

/** A cover with its title under it, as the shelves on Home show a book. */
@Composable
private fun ShelfCard(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    belowTitle: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .width(ShelfCardWidth)
            .clip(ShelfCoverShape)
            .clickable(onClick = onClick)
    ) {
        CoverImage(
            coverUrl = coverUrl,
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(ShelfCoverShape)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        belowTitle()
    }
}

@Composable
private fun ContinueReadingCard(book: Book, percentage: Float, onClick: () -> Unit) {
    ShelfCard(title = book.title, coverUrl = book.coverUrl, onClick = onClick) {
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 6.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Text(
            text = "${(percentage * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RecentlyAddedCard(
    title: String,
    authors: String,
    coverUrl: String,
    onClick: () -> Unit
) {
    ShelfCard(title = title, coverUrl = coverUrl, onClick = onClick) {
        if (authors.isNotBlank()) {
            Text(
                text = authors,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun RecentlyAddedPlaceholderCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Column(modifier = Modifier.width(ShelfCardWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(ShelfCoverShape)
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerColor)
        )
    }
}

/** Reading time this week and the current streak; tapping it opens the statistics screen. */
@Composable
private fun QuickStatsCard(stats: QuickStats, onClick: () -> Unit = {}) {
    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hours = stats.weekSeconds / 3600
            val minutes = (stats.weekSeconds % 3600) / 60
            QuickStat(
                value = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                label = "This week",
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            QuickStat(
                value = "${stats.currentStreak}",
                label = "Day streak",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
