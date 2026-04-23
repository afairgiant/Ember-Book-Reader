package com.ember.reader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.repository.LibraryDensity
import com.ember.reader.core.repository.LibraryFormat
import com.ember.reader.core.repository.LibrarySourceFilter
import com.ember.reader.core.repository.LibraryStatus
import com.ember.reader.core.repository.LibraryViewMode
import com.ember.reader.ui.library.components.ActiveFilterChip
import com.ember.reader.ui.library.components.CardInfoToggles
import com.ember.reader.ui.library.components.ContinueReadingCarousel
import com.ember.reader.ui.library.components.LibraryFilterSheet
import com.ember.reader.ui.library.components.LibraryLayoutSheet
import com.ember.reader.ui.library.components.LibraryToolbar
import com.ember.reader.ui.library.components.UnifiedBookCard
import com.ember.reader.ui.library.components.UnifiedBookListRow
import com.ember.reader.ui.theme.LocalAccentColor
import com.ember.reader.ui.theme.serverAccentColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalLibraryScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    onOpenBookDetail: (bookId: String) -> Unit = {},
    viewModel: LocalLibraryViewModel = hiltViewModel()
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val appearances by viewModel.appearances.collectAsStateWithLifecycle()
    val operationResult by viewModel.operationResult.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var filterSheetVisible by remember { mutableStateOf(false) }
    var layoutSheetVisible by remember { mutableStateOf(false) }
    var relinkBookId by remember { mutableStateOf<String?>(null) }
    var deleteBookId by remember { mutableStateOf<String?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(operationResult) {
        operationResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissOperationResult()
        }
    }

    val isSelecting = selectedIds.isNotEmpty()

    val activeChips: List<ActiveFilterChip> = buildList {
        val sourceFilter = prefs.sourceFilter
        if (sourceFilter != LibrarySourceFilter.All) {
            val sourceName = when (sourceFilter) {
                LibrarySourceFilter.All -> ""
                LibrarySourceFilter.Local -> stringResource(R.string.filter_local)
                is LibrarySourceFilter.Server -> appearances[sourceFilter.serverId]?.name ?: ""
            }
            val label = stringResource(R.string.chip_source, sourceName)
            add(ActiveFilterChip("source", label) { viewModel.setSource(LibrarySourceFilter.All) })
        }
        if (prefs.formatFilter != LibraryFormat.ALL) {
            val label = when (prefs.formatFilter) {
                LibraryFormat.BOOKS -> stringResource(R.string.filter_books)
                LibraryFormat.AUDIOBOOKS -> stringResource(R.string.filter_audiobooks)
                LibraryFormat.ALL -> ""
            }
            add(ActiveFilterChip("format", label) { viewModel.setFormat(LibraryFormat.ALL) })
        }
        if (prefs.statusFilter != LibraryStatus.ALL) {
            val label = when (prefs.statusFilter) {
                LibraryStatus.READING -> stringResource(R.string.status_reading)
                LibraryStatus.UNREAD -> stringResource(R.string.status_unread)
                LibraryStatus.FINISHED -> stringResource(R.string.status_finished)
                LibraryStatus.ALL -> ""
            }
            add(ActiveFilterChip("status", label) { viewModel.setStatus(LibraryStatus.ALL) })
        }
    }

    val hasActiveFilters = activeChips.isNotEmpty() || searchQuery.isNotBlank()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importBook(it) } }

    val cardInfo = CardInfoToggles(
        showProgress = prefs.cardShowProgress,
        showAuthor = prefs.cardShowAuthor,
        showFormatBadge = prefs.cardShowFormatBadge,
        showMetadata = prefs.cardShowMetadata,
    )

    val gridMinSize = when (prefs.density) {
        LibraryDensity.SMALL -> 90.dp
        LibraryDensity.MEDIUM -> 120.dp
        LibraryDensity.LARGE -> 160.dp
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isSelecting) {
                FloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("application/epub+zip", "application/pdf"))
                    }
                ) {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_book))
                    }
                }
            }
        },
        bottomBar = {
            if (isSelecting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            viewModel.selectAll(viewState.items.filterIsInstance<LibraryListItem.BookEntry>().map { it.book })
                        }) { Text(stringResource(R.string.select_all)) }
                        TextButton(onClick = viewModel::clearSelection) {
                            Text(stringResource(R.string.clear_selection))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = viewModel::syncSelectedProgress,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.sync_count, selectedIds.size))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showBatchDeleteConfirm = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.delete_count, selectedIds.size))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LibraryToolbar(
                resultCount = viewState.totalCount,
                activeChips = activeChips,
                searchActive = searchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearch,
                onSearchToggle = {
                    if (searchActive) viewModel.clearSearch()
                    searchActive = !searchActive
                },
                onFilterClick = { filterSheetVisible = true },
                onLayoutClick = { layoutSheetVisible = true },
                viewMode = prefs.viewMode
            )

            if (viewState.items.isEmpty()) {
                EmptyState(
                    hasActiveFilters = hasActiveFilters,
                    onClearFilters = {
                        viewModel.clearAllFilters()
                        if (searchActive) {
                            viewModel.clearSearch()
                            searchActive = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LibraryContent(
                    viewState = viewState,
                    viewMode = prefs.viewMode,
                    gridMinSize = gridMinSize,
                    showContinueReading = prefs.showContinueReading,
                    progressMap = progressMap,
                    cardInfo = cardInfo,
                    appearances = appearances,
                    selectedIds = selectedIds,
                    isSelecting = isSelecting,
                    onBookClick = { book ->
                        if (isSelecting) {
                            viewModel.toggleSelection(book.id)
                        } else {
                            onOpenBookDetail(book.id)
                        }
                    },
                    onBookLongClick = { book -> viewModel.toggleSelection(book.id) },
                    onBookDelete = { book -> deleteBookId = book.id },
                    onBookRelink = { book ->
                        if (book.serverId == null && servers.isNotEmpty()) relinkBookId = book.id
                    },
                    canRelink = { book -> book.serverId == null && servers.isNotEmpty() },
                    onPullProgress = { book -> viewModel.pullBookProgress(book) },
                    onPushProgress = { book -> viewModel.pushBookProgress(book) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (filterSheetVisible) {
            val sourceOptions: List<LibrarySourceFilter> = remember(servers) {
                buildList {
                    add(LibrarySourceFilter.All)
                    add(LibrarySourceFilter.Local)
                    servers.forEach { add(LibrarySourceFilter.Server(it.id)) }
                }
            }
            LibraryFilterSheet(
                prefs = prefs,
                sourceOptions = sourceOptions,
                appearances = appearances,
                onDismiss = { filterSheetVisible = false },
                onSortSelected = viewModel::setSort,
                onGroupSelected = viewModel::setGroupBy,
                onSourceChange = viewModel::setSource,
                onFormatChange = viewModel::setFormat,
                onStatusChange = viewModel::setStatus,
                onApplyPreset = viewModel::applyPreset,
                onClearAll = viewModel::clearAllFilters
            )
        }

        if (layoutSheetVisible) {
            LibraryLayoutSheet(
                prefs = prefs,
                onDismiss = { layoutSheetVisible = false },
                onViewModeChange = viewModel::setViewMode,
                onDensityChange = viewModel::setDensity,
                onShowContinueReadingChange = viewModel::setShowContinueReading,
                onCardShowProgressChange = viewModel::setCardShowProgress,
                onCardShowAuthorChange = viewModel::setCardShowAuthor,
                onCardShowFormatBadgeChange = viewModel::setCardShowFormatBadge,
                onCardShowMetadataChange = viewModel::setCardShowMetadata
            )
        }

        relinkBookId?.let { bookId ->
            AlertDialog(
                onDismissRequest = { relinkBookId = null },
                title = { Text(stringResource(R.string.relink_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.relink_prompt))
                        Spacer(modifier = Modifier.height(12.dp))
                        servers.forEach { server ->
                            TextButton(
                                onClick = {
                                    viewModel.relinkBook(bookId, server.id)
                                    relinkBookId = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(server.name) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { relinkBookId = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        deleteBookId?.let { bookId ->
            val bookTitle = viewState.items
                .filterIsInstance<LibraryListItem.BookEntry>()
                .firstOrNull { it.book.id == bookId }?.book?.title ?: "this book"
            AlertDialog(
                onDismissRequest = { deleteBookId = null },
                title = { Text(stringResource(R.string.delete_book_title)) },
                text = { Text(stringResource(R.string.delete_book_message, bookTitle)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteBook(bookId)
                        deleteBookId = null
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteBookId = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = { Text(stringResource(R.string.delete_books_title, selectedIds.size)) },
                text = { Text(stringResource(R.string.delete_books_message, selectedIds.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSelected()
                        showBatchDeleteConfirm = false
                    }) { Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryContent(
    viewState: LibraryViewState,
    viewMode: LibraryViewMode,
    gridMinSize: androidx.compose.ui.unit.Dp,
    showContinueReading: Boolean,
    progressMap: Map<String, Float>,
    cardInfo: CardInfoToggles,
    appearances: Map<Long, com.ember.reader.core.repository.ServerAppearance>,
    selectedIds: Set<String>,
    isSelecting: Boolean,
    onBookClick: (com.ember.reader.core.model.Book) -> Unit,
    onBookLongClick: (com.ember.reader.core.model.Book) -> Unit,
    onBookDelete: (com.ember.reader.core.model.Book) -> Unit,
    onBookRelink: (com.ember.reader.core.model.Book) -> Unit,
    canRelink: (com.ember.reader.core.model.Book) -> Boolean,
    onPullProgress: (com.ember.reader.core.model.Book) -> Unit,
    onPushProgress: (com.ember.reader.core.model.Book) -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        LibraryViewMode.GRID -> {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(gridMinSize),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
            ) {
                if (showContinueReading && viewState.inProgress.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "continue-reading") {
                        ContinueReadingCarousel(
                            books = viewState.inProgress,
                            progressMap = progressMap,
                            onBookClick = onBookClick
                        )
                    }
                }
                viewState.items.forEach { item ->
                    when (item) {
                        is LibraryListItem.Header -> {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header-${item.key}") {
                                GroupHeader(
                                    label = item.label,
                                    count = item.count,
                                    dotColor = item.sourceDotColor()
                                )
                            }
                        }
                        is LibraryListItem.BookEntry -> {
                            item(key = item.book.id) {
                                val book = item.book
                                UnifiedBookCard(
                                    book = book,
                                    progress = progressMap[book.id],
                                    isSelected = book.id in selectedIds,
                                    isSelecting = isSelecting,
                                    info = cardInfo,
                                    onClick = { onBookClick(book) },
                                    onLongClick = { onBookLongClick(book) },
                                    onDelete = { onBookDelete(book) },
                                    onRelink = if (canRelink(book)) {
                                        { onBookRelink(book) }
                                    } else {
                                        null
                                    },
                                    onPullProgress = if (book.serverId != null) {
                                        { onPullProgress(book) }
                                    } else {
                                        null
                                    },
                                    onPushProgress = if (book.serverId != null) {
                                        { onPushProgress(book) }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        LibraryViewMode.LIST, LibraryViewMode.COMPACT_LIST -> {
            val compact = viewMode == LibraryViewMode.COMPACT_LIST
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
                modifier = modifier
            ) {
                if (showContinueReading && viewState.inProgress.isNotEmpty()) {
                    item(key = "continue-reading") {
                        ContinueReadingCarousel(
                            books = viewState.inProgress,
                            progressMap = progressMap,
                            onBookClick = onBookClick
                        )
                    }
                }
                viewState.items.forEach { item ->
                    when (item) {
                        is LibraryListItem.Header -> {
                            item(key = "header-${item.key}") {
                                GroupHeader(
                                    label = item.label,
                                    count = item.count,
                                    dotColor = item.sourceDotColor()
                                )
                            }
                        }
                        is LibraryListItem.BookEntry -> {
                            item(key = item.book.id) {
                                val book = item.book
                                UnifiedBookListRow(
                                    book = book,
                                    progress = progressMap[book.id],
                                    isSelected = book.id in selectedIds,
                                    isSelecting = isSelecting,
                                    compact = compact,
                                    info = cardInfo,
                                    onClick = { onBookClick(book) },
                                    onLongClick = { onBookLongClick(book) },
                                    onDelete = { onBookDelete(book) },
                                    onRelink = if (canRelink(book)) {
                                        { onBookRelink(book) }
                                    } else {
                                        null
                                    },
                                    onPullProgress = if (book.serverId != null) {
                                        { onPullProgress(book) }
                                    } else {
                                        null
                                    },
                                    onPushProgress = if (book.serverId != null) {
                                        { onPushProgress(book) }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    dotColor: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Palette color for the dot rendered beside a SOURCE group header; null for non-source groups. */
private fun LibraryListItem.Header.sourceDotColor(): androidx.compose.ui.graphics.Color? = when {
    isLocalSource -> LocalAccentColor
    colorSlot != null -> serverAccentColor(colorSlot)
    else -> null
}

@Composable
private fun EmptyState(
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasActiveFilters) {
                Text(
                    text = stringResource(R.string.no_matches),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.clear_filters))
                }
            } else {
                Text(
                    text = stringResource(R.string.no_books_yet),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.no_books_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
