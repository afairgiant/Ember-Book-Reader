package com.ember.reader.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.ember.reader.ui.organize.OrganizeFilesSheet
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.ui.common.BookCoverImage
import androidx.compose.ui.res.stringResource
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    serverId: Long,
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit = { _, _ -> },
    onOpenBookDetail: (bookId: String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val grimmoryFilter by viewModel.grimmoryFilter.collectAsStateWithLifecycle()
    val grimmoryFilterOptions by viewModel.grimmoryFilterOptions.collectAsStateWithLifecycle()
    val selectedBookIds by viewModel.selectedBookIds.collectAsStateWithLifecycle()
    val isSelecting by viewModel.isSelecting.collectAsStateWithLifecycle()
    val currentServer by viewModel.currentServer.collectAsStateWithLifecycle()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    var showOrganizeSheet by remember { mutableStateOf(false) }
    var actionMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = isSelecting) { viewModel.clearSelection() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelecting) {
                TopAppBar(
                    title = { Text("${selectedBookIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { actionMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = actionMenuOpen,
                            onDismissRequest = { actionMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Organize files…") },
                                enabled = currentServer?.canMoveOrganizeFiles == true,
                                onClick = {
                                    actionMenuOpen = false
                                    showOrganizeSheet = true
                                },
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = { filterSheetOpen = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Sort and filter",
                                tint = if (grimmoryFilter.isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        IconButton(onClick = viewModel::toggleViewMode) {
                            Icon(
                                if (viewMode == ViewMode.GRID) {
                                    Icons.Default.ViewList
                                } else {
                                    Icons.Default.GridView
                                },
                                contentDescription = stringResource(R.string.toggle_view)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    placeholder = { Text(stringResource(R.string.search_books)) }
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (state.books.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.no_books_found),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (viewMode == ViewMode.GRID) {
                            BookGrid(
                                books = state.books,
                                downloadingIds = state.downloadingBookIds,
                                isSelecting = isSelecting,
                                selectedIds = selectedBookIds,
                                onBookClick = { book ->
                                    if (isSelecting) viewModel.toggleSelection(book.id)
                                    else onOpenBookDetail(book.id)
                                },
                                onBookLongClick = { book -> viewModel.enterSelectionWith(book.id) },
                                onDownloadClick = viewModel::downloadBook,
                                loadingMore = loadingMore,
                                onLoadMore = if (hasMore) viewModel::loadMore else null
                            )
                        } else {
                            BookList(
                                books = state.books,
                                downloadingIds = state.downloadingBookIds,
                                isSelecting = isSelecting,
                                selectedIds = selectedBookIds,
                                onBookClick = { book ->
                                    if (isSelecting) viewModel.toggleSelection(book.id)
                                    else onOpenBookDetail(book.id)
                                },
                                onBookLongClick = { book -> viewModel.enterSelectionWith(book.id) },
                                onDownloadClick = viewModel::downloadBook,
                                loadingMore = loadingMore,
                                onLoadMore = if (hasMore) viewModel::loadMore else null
                            )
                        }
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        com.ember.reader.ui.library.components.GrimmoryFilterSheet(
            filter = grimmoryFilter,
            filterOptions = grimmoryFilterOptions,
            onApply = viewModel::updateGrimmoryFilter,
            onReset = viewModel::resetGrimmoryFilter,
            onDismiss = { filterSheetOpen = false },
        )
    }

    if (showOrganizeSheet) {
        val server = currentServer
        val successState = uiState as? LibraryUiState.Success
        // Map selected local-book IDs to their Grimmory backend IDs. Books with no
        // grimmoryBookId (local-only, OPDS, etc.) are filtered out — we can't move them.
        val grimmoryBookIds: List<Long> = successState
            ?.books
            ?.filter { it.id in selectedBookIds }
            ?.mapNotNull { it.grimmoryBookId }
            .orEmpty()

        if (server != null && grimmoryBookIds.isNotEmpty()) {
            val organizeVm = remember(grimmoryBookIds) {
                viewModel.organizeFilesViewModelFactory.create(
                    baseUrl = server.url,
                    serverId = server.id,
                    bookIds = grimmoryBookIds,
                )
            }
            OrganizeFilesSheet(
                viewModel = organizeVm,
                onDismiss = { showOrganizeSheet = false },
                onSuccess = { count, libName ->
                    val plural = if (count == 1) "book" else "books"
                    scope.launch {
                        snackbarHostState.showSnackbar("Moved $count $plural to $libName")
                    }
                    viewModel.clearSelection()
                    viewModel.refresh()
                    showOrganizeSheet = false
                },
            )
        } else {
            // Nothing movable — just close the sheet silently.
            showOrganizeSheet = false
        }
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    downloadingIds: Set<String>,
    isSelecting: Boolean,
    selectedIds: Set<String>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onDownloadClick: (Book) -> Unit,
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val gridState = rememberLazyGridState()

    // Trigger load more when near end
    if (onLoadMore != null) {
        LaunchedEffect(gridState) {
            snapshotFlow {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = gridState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 6
            }.collect { nearEnd ->
                if (nearEnd) onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookGridItem(
                book = book,
                isDownloading = book.id in downloadingIds,
                isSelecting = isSelecting,
                isSelected = book.id in selectedIds,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) },
                onDownload = { onDownloadClick(book) }
            )
        }
        if (loadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: Book,
    isDownloading: Boolean,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                BookCoverImage(
                    book = book,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                )

                if (!book.isDownloaded && !isSelecting) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = stringResource(R.string.download),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
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
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    isSelecting: Boolean,
    selectedIds: Set<String>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onDownloadClick: (Book) -> Unit,
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()

    if (onLoadMore != null) {
        LaunchedEffect(listState) {
            snapshotFlow {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 4
            }.collect { nearEnd ->
                if (nearEnd) onLoadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookListItem(
                book = book,
                isDownloading = book.id in downloadingIds,
                isSelecting = isSelecting,
                isSelected = book.id in selectedIds,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) },
                onDownload = { onDownloadClick(book) }
            )
        }
        if (loadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListItem(
    book: Book,
    isDownloading: Boolean,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelecting) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (book.coverUrl != null) {
                BookCoverImage(
                    book = book,
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
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = book.format.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (!book.isDownloaded && !isSelecting) {
                IconButton(onClick = onDownload) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "Download"
                        )
                    }
                }
            }
        }
    }
}
