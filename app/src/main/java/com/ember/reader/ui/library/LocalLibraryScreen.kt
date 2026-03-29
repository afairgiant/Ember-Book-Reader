package com.ember.reader.ui.library
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.common.BookCoverPlaceholderColors
import com.ember.reader.ui.common.bookCoverColorIndex
import kotlin.math.roundToInt

enum class LibraryFilter { ALL, SERVER, LOCAL }
enum class FormatFilter { ALL, BOOKS, AUDIOBOOKS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalLibraryScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    onOpenBookDetail: (bookId: String) -> Unit = {},
    viewModel: LocalLibraryViewModel = hiltViewModel()
) {
    val allBooks by viewModel.allDownloadedBooks.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val coverAuthHeaders by viewModel.coverAuthHeaders.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val operationResult by viewModel.operationResult.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val sortReversed by viewModel.sortReversed.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var formatFilter by rememberSaveable { mutableStateOf(FormatFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var relinkBookId by remember { mutableStateOf<String?>(null) }
    var deleteBookId by remember { mutableStateOf<String?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when operation result changes
    LaunchedEffect(operationResult) {
        operationResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissOperationResult()
        }
    }

    val isSelecting = selectedIds.isNotEmpty()

    val filteredBooks =
        remember(allBooks, filter, formatFilter, sortMode, sortReversed, progressMap, searchQuery) {
            val sourceFiltered = when (filter) {
                LibraryFilter.ALL -> allBooks
                LibraryFilter.SERVER -> allBooks.filter { it.serverId != null }
                LibraryFilter.LOCAL -> allBooks.filter { it.serverId == null }
            }
            val formatFiltered = when (formatFilter) {
                FormatFilter.ALL -> sourceFiltered
                FormatFilter.BOOKS -> sourceFiltered.filter {
                    it.format == com.ember.reader.core.model.BookFormat.EPUB ||
                        it.format == com.ember.reader.core.model.BookFormat.PDF
                }
                FormatFilter.AUDIOBOOKS -> sourceFiltered.filter {
                    it.format == com.ember.reader.core.model.BookFormat.AUDIOBOOK
                }
            }
            val filtered = if (searchQuery.isBlank()) {
                formatFiltered
            } else {
                formatFiltered.filter { book ->
                    book.title.contains(searchQuery, ignoreCase = true) ||
                        book.author?.contains(searchQuery, ignoreCase = true) == true
                }
            }
            val sorted = when (sortMode) {
                LibrarySortMode.RECENT -> filtered.sortedByDescending { it.downloadedAt ?: it.addedAt }
                LibrarySortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
                LibrarySortMode.AUTHOR -> filtered.sortedBy { it.author?.lowercase() ?: "" }
                LibrarySortMode.PROGRESS -> filtered.sortedByDescending { progressMap[it.id] ?: 0f }
            }
            if (sortReversed) sorted.reversed() else sorted
        }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = if (searchActive) stringResource(R.string.close_search) else stringResource(R.string.search)
                        )
                    }
                },
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
                        TextButton(onClick = { viewModel.selectAll(filteredBooks) }) {
                            Text(stringResource(R.string.select_all))
                        }
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
            // Search bar
            if (searchActive) {
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_books)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
            }

            // Format tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = formatFilter == FormatFilter.ALL,
                    onClick = { formatFilter = FormatFilter.ALL },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = formatFilter == FormatFilter.BOOKS,
                    onClick = { formatFilter = FormatFilter.BOOKS },
                    label = { Text(stringResource(R.string.filter_books)) }
                )
                FilterChip(
                    selected = formatFilter == FormatFilter.AUDIOBOOKS,
                    onClick = { formatFilter = FormatFilter.AUDIOBOOKS },
                    label = { Text(stringResource(R.string.filter_audiobooks)) }
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                // Source filter
                FilterChip(
                    selected = filter == LibraryFilter.SERVER,
                    onClick = {
                        filter = if (filter == LibraryFilter.SERVER) LibraryFilter.ALL else LibraryFilter.SERVER
                    },
                    label = { Text(stringResource(R.string.filter_server)) }
                )
                FilterChip(
                    selected = filter == LibraryFilter.LOCAL,
                    onClick = {
                        filter = if (filter == LibraryFilter.LOCAL) LibraryFilter.ALL else LibraryFilter.LOCAL
                    },
                    label = { Text(stringResource(R.string.filter_local)) }
                )
            }

            // Sort chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibrarySortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { viewModel.updateSortMode(mode) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(mode.displayName)
                                if (sortMode == mode && sortReversed) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "\u2191",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    )
                }
            }

            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            } else {
                val gridState = rememberLazyGridState()
                LaunchedEffect(sortMode, sortReversed) {
                    gridState.scrollToItem(0)
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        UnifiedBookCard(
                            book = book,
                            progress = progressMap[book.id],
                            coverAuthHeader = book.serverId?.let { coverAuthHeaders[it] },
                            isSelected = book.id in selectedIds,
                            isSelecting = isSelecting,
                            onClick = {
                                if (isSelecting) {
                                    viewModel.toggleSelection(book.id)
                                } else {
                                    onOpenBookDetail(book.id)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(book.id) },
                            onDelete = { deleteBookId = book.id },
                            onRelink = if (book.serverId == null && servers.isNotEmpty()) {
                                { relinkBookId = book.id }
                            } else {
                                null
                            },
                            onPullProgress = if (book.serverId != null) {
                                { viewModel.pullBookProgress(book) }
                            } else {
                                null
                            },
                            onPushProgress = if (book.serverId != null) {
                                { viewModel.pushBookProgress(book) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }

        // Server picker dialog for relinking
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
                            ) {
                                Text(server.name)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { relinkBookId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Single book delete confirmation
        deleteBookId?.let { bookId ->
            val bookTitle = allBooks.find { it.id == bookId }?.title ?: "this book"
            AlertDialog(
                onDismissRequest = { deleteBookId = null },
                title = { Text(stringResource(R.string.delete_book_title)) },
                text = { Text(stringResource(R.string.delete_book_message, bookTitle)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteBook(bookId)
                        deleteBookId = null
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteBookId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Batch delete confirmation
        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = { Text(stringResource(R.string.delete_books_title, selectedIds.size)) },
                text = { Text(stringResource(R.string.delete_books_message, selectedIds.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSelected()
                        showBatchDeleteConfirm = false
                    }) {
                        Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedBookCard(
    book: Book,
    progress: Float? = null,
    coverAuthHeader: String? = null,
    isSelected: Boolean = false,
    isSelecting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRelink: (() -> Unit)? = null,
    onPullProgress: (() -> Unit)? = null,
    onPushProgress: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val colorIndex = bookCoverColorIndex(book.title)
    val isFromServer = book.serverId != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                if (book.coverUrl != null) {
                    val context = LocalContext.current
                    val imageModel = remember(book.coverUrl, coverAuthHeader) {
                        val url = if (coverAuthHeader?.startsWith("jwt:") == true) {
                            val token = coverAuthHeader.removePrefix("jwt:")
                            "${book.coverUrl}?token=$token"
                        } else {
                            book.coverUrl
                        }
                        ImageRequest.Builder(context)
                            .data(url)
                            .apply {
                                if (coverAuthHeader != null && !coverAuthHeader.startsWith("jwt:")) {
                                    addHeader("Authorization", coverAuthHeader)
                                }
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
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BookCoverPlaceholderColors[colorIndex]),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = book.title.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF5D4037)
                        )
                    }
                }

                // Selection checkbox
                if (isSelecting) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    )
                } else {
                    // 3-dot menu
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (onRelink != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.relink_to_server)) },
                                    onClick = {
                                        showMenu = false
                                        onRelink()
                                    }
                                )
                            }
                            if (onPullProgress != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pull_progress)) },
                                    onClick = {
                                        showMenu = false
                                        onPullProgress()
                                    }
                                )
                            }
                            if (onPushProgress != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.push_progress)) },
                                    onClick = {
                                        showMenu = false
                                        onPushProgress()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                // Source badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isFromServer) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isFromServer) Icons.Default.CloudDone else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isFromServer) stringResource(R.string.source_server) else stringResource(R.string.source_local),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
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
                if (progress != null && progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
