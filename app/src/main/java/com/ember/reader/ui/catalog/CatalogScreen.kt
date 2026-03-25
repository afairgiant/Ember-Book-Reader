package com.ember.reader.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.core.opds.OpdsFeedEntry
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeed: (path: String) -> Unit,
    onNavigateToBooks: (path: String) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val state = uiState) {
                            is CatalogUiState.Success -> state.feed.title
                            else -> "Catalog"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            CatalogUiState.Loading -> LoadingScreen(Modifier.padding(padding))
            is CatalogUiState.Error -> ErrorScreen(
                message = state.message,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::refresh,
            )
            is CatalogUiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(state.feed.entries, key = { it.id }) { entry ->
                            CatalogEntryCard(
                                entry = entry,
                                onClick = {
                                    if (entry.href.contains("catalog") ||
                                        entry.href.contains("?page=")
                                    ) {
                                        onNavigateToBooks(entry.href)
                                    } else {
                                        onNavigateToFeed(entry.href)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogEntryCard(
    entry: OpdsFeedEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.content?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
