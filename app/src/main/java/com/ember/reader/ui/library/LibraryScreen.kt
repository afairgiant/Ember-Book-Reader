package com.ember.reader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.common.BookCoverPlaceholderColors
import com.ember.reader.ui.common.bookCoverColorIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    serverId: Long,
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit = { _, _ -> },
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val coverAuthHeader by viewModel.coverAuthHeader.collectAsStateWithLifecycle()
    var searchActive by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { searchActive = !searchActive }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.Default.ViewList
                            else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearch = { searchActive = false },
                    active = false,
                    onActiveChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search books...") },
                ) {}
            }

            when (val state = uiState) {
                LibraryUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is LibraryUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.books.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No books found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (viewMode == ViewMode.GRID) {
                            BookGrid(
                                books = state.books,
                                downloadingIds = state.downloadingBookIds,
                                coverAuthHeader = coverAuthHeader,
                                onBookClick = { book ->
                                if (book.isDownloaded) onOpenReader(book.id, book.format)
                            },
                                onDownloadClick = viewModel::downloadBook,
                            )
                        } else {
                            BookList(
                                books = state.books,
                                downloadingIds = state.downloadingBookIds,
                                coverAuthHeader = coverAuthHeader,
                                onBookClick = { book ->
                                if (book.isDownloaded) onOpenReader(book.id, book.format)
                            },
                                onDownloadClick = viewModel::downloadBook,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    downloadingIds: Set<String>,
    coverAuthHeader: String?,
    onBookClick: (Book) -> Unit,
    onDownloadClick: (Book) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookGridItem(
                book = book,
                isDownloading = book.id in downloadingIds,
                coverAuthHeader = coverAuthHeader,
                onClick = { onBookClick(book) },
                onDownload = { onDownloadClick(book) },
            )
        }
    }
}

@Composable
private fun BookGridItem(
    book: Book,
    isDownloading: Boolean,
    coverAuthHeader: String?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val placeholderColor = BookCoverPlaceholderColors[bookCoverColorIndex(book.title)]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f),
            ) {
                if (book.coverUrl != null) {
                    val context = LocalContext.current
                    val imageModel = remember(book.coverUrl, coverAuthHeader) {
                        ImageRequest.Builder(context)
                            .data(book.coverUrl)
                            .apply {
                                coverAuthHeader?.let { addHeader("Authorization", it) }
                            }
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageModel,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(placeholderColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = book.title.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF5D4037),
                        )
                    }
                }

                if (!book.isDownloaded) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookList(
    books: List<Book>,
    downloadingIds: Set<String>,
    coverAuthHeader: String?,
    onBookClick: (Book) -> Unit,
    onDownloadClick: (Book) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookListItem(
                book = book,
                isDownloading = book.id in downloadingIds,
                coverAuthHeader = coverAuthHeader,
                onClick = { onBookClick(book) },
                onDownload = { onDownloadClick(book) },
            )
        }
    }
}

@Composable
private fun BookListItem(
    book: Book,
    isDownloading: Boolean,
    coverAuthHeader: String?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (book.coverUrl != null) {
                val context = LocalContext.current
                val imageModel = remember(book.coverUrl, coverAuthHeader) {
                    ImageRequest.Builder(context)
                        .data(book.coverUrl)
                        .apply {
                            coverAuthHeader?.let { addHeader("Authorization", it) }
                        }
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 48.dp, height = 72.dp)
                        .clip(MaterialTheme.shapes.small),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = book.format.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (!book.isDownloaded) {
                IconButton(onClick = onDownload) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "Download",
                        )
                    }
                }
            }
        }
    }
}
