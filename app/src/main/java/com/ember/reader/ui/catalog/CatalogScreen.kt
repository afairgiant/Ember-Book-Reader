package com.ember.reader.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val state = uiState) {
                            is CatalogUiState.Success -> state.feed.title
                            else -> "Catalog"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { searchActive = !searchActive }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (searchActive) {
                            androidx.compose.material3.OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search catalog...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = {
                                        if (searchQuery.isNotBlank()) {
                                            onNavigateToBooks("grimmory:search=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")
                                            searchActive = false
                                            searchQuery = ""
                                        }
                                    },
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                                ),
                            )
                        }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(state.feed.entries, key = { it.id }) { entry ->
                            CatalogEntryCard(
                                entry = entry,
                                onClick = {
                                    when {
                                        // Grimmory App API entries — all go to books view
                                        entry.href.startsWith("grimmory:") -> {
                                            onNavigateToBooks(entry.href)
                                        }
                                        // OPDS acquisition feeds
                                        entry.href.contains("catalog") ||
                                            entry.href.contains("?page=") ||
                                            entry.href.contains("recent") ||
                                            entry.href.contains("surprise") -> {
                                            onNavigateToBooks(entry.href)
                                        }
                                        // OPDS navigation feeds
                                        else -> {
                                            onNavigateToFeed(entry.href)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    } // Column
                }
            }
        }
    }
}

private fun iconForEntry(title: String): ImageVector = when {
    title.contains("librar", ignoreCase = true) -> Icons.Default.LibraryBooks
    title.contains("shelv", ignoreCase = true) -> Icons.Default.CollectionsBookmark
    title.contains("magic", ignoreCase = true) -> Icons.Default.AutoStories
    title.contains("author", ignoreCase = true) -> Icons.Default.Person
    title.contains("series", ignoreCase = true) -> Icons.Default.CollectionsBookmark
    title.contains("recent", ignoreCase = true) -> Icons.Default.History
    title.contains("surprise", ignoreCase = true) -> Icons.Default.Shuffle
    title.contains("all", ignoreCase = true) -> Icons.Default.LibraryBooks
    else -> Icons.Default.Explore
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconForEntry(entry.title),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
