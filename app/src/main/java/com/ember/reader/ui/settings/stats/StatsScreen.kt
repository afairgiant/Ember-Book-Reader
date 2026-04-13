package com.ember.reader.ui.settings.stats

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onNavigateBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.stats_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero summary
            item { HeroSummarySection(stats) }

            // 2. Activity (heatmap + scatter)
            item { ActivitySection(stats) }

            // 3. Time periods + estimated completion
            item { TimePeriodSection(stats) }
            stats.estimatedMinutesToFinish?.let { minutes ->
                item { EstimatedCompletionCard(minutes) }
            }

            // 4. Reading patterns (Grimmory only)
            if (stats.isGrimmoryConnected) {
                val hasPatterns = (stats.peakHours?.isNotEmpty() == true) ||
                    (stats.favoriteDays?.isNotEmpty() == true)
                if (hasPatterns) {
                    item { PatternsSection(stats) }
                }
            }

            // 5. Reading timeline (Grimmory only)
            if (stats.isGrimmoryConnected) {
                item {
                    TimelineSection(
                        stats = stats,
                        onLoadTimeline = viewModel::loadTimeline
                    )
                }
            }

            // 6. Library & genres (Grimmory only)
            if (stats.isGrimmoryConnected) {
                val hasLibrary = stats.bookDistributions != null || stats.genreStats?.isNotEmpty() == true
                if (hasLibrary) {
                    item { LibrarySection(stats) }
                }
            }

            // 7. Recent sessions
            if (stats.recentSessions.isNotEmpty()) {
                item { RecentSessionsSection(stats) }
            }

            // Empty state
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

            // 8. Footer link to Grimmory (Grimmory only)
            stats.grimmoryServerUrl?.let { serverUrl ->
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("$serverUrl/reading-stats")
                                )
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.stats_view_on_grimmory))
                        }
                    }
                }
            }
        }
    }
}
