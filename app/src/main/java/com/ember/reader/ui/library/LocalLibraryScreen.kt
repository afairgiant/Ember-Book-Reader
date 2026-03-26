package com.ember.reader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat

enum class LibraryFilter { ALL, SERVER, LOCAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLibraryScreen(
    onNavigateBack: () -> Unit,
    onOpenReader: (bookId: String, format: BookFormat) -> Unit,
    viewModel: LocalLibraryViewModel = hiltViewModel(),
) {
    val allBooks by viewModel.allDownloadedBooks.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val coverAuthHeaders by viewModel.coverAuthHeaders.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val operationResult by viewModel.operationResult.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var relinkBookId by remember { mutableStateOf<String?>(null) }

    val filteredBooks = when (filter) {
        LibraryFilter.ALL -> allBooks
        LibraryFilter.SERVER -> allBooks.filter { it.serverId != null }
        LibraryFilter.LOCAL -> allBooks.filter { it.serverId == null }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("application/epub+zip", "application/pdf"))
                },
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Import Book")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == LibraryFilter.ALL,
                    onClick = { filter = LibraryFilter.ALL },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = filter == LibraryFilter.SERVER,
                    onClick = { filter = LibraryFilter.SERVER },
                    label = { Text("Server") },
                )
                FilterChip(
                    selected = filter == LibraryFilter.LOCAL,
                    onClick = { filter = LibraryFilter.LOCAL },
                    label = { Text("Local") },
                )
            }

            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No books yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Download from a server or tap + to import",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        UnifiedBookCard(
                            book = book,
                            progress = progressMap[book.id],
                            coverAuthHeader = book.serverId?.let { coverAuthHeaders[it] },
                            onClick = { onOpenReader(book.id, book.format) },
                            onDelete = { viewModel.deleteBook(book.id) },
                            onRelink = if (book.serverId == null && servers.isNotEmpty()) {
                                { relinkBookId = book.id }
                            } else null,
                            onSyncProgress = if (book.serverId != null && book.fileHash != null) {
                                { viewModel.syncBookProgress(book) }
                            } else null,
                        )
                    }
                }
            }
        }

        // Server picker dialog for relinking
        relinkBookId?.let { bookId ->
            AlertDialog(
                onDismissRequest = { relinkBookId = null },
                title = { Text("Relink to Server") },
                text = {
                    Column {
                        Text("Choose a server to search for this book:")
                        Spacer(modifier = Modifier.height(12.dp))
                        servers.forEach { server ->
                            TextButton(
                                onClick = {
                                    viewModel.relinkBook(bookId, server.id)
                                    relinkBookId = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(server.name)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { relinkBookId = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Relink result snackbar
        operationResult?.let { result ->
            AlertDialog(
                onDismissRequest = viewModel::dismissOperationResult,
                text = { Text(result) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissOperationResult) {
                        Text("OK")
                    }
                },
            )
        }
    }
}

private val placeholderColors = listOf(
    Color(0xFFFFE0D0), Color(0xFFE8D5C8), Color(0xFFFFF0E0),
    Color(0xFFD4E8D0), Color(0xFFD8D8E8), Color(0xFFE8D0D8),
)

@Composable
private fun UnifiedBookCard(
    book: Book,
    progress: Float? = null,
    coverAuthHeader: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onRelink: (() -> Unit)? = null,
    onSyncProgress: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val colorIndex = book.title.hashCode().mod(placeholderColors.size).let {
        if (it < 0) it + placeholderColors.size else it
    }
    val isFromServer = book.serverId != null

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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverUrl)
                            .apply {
                                coverAuthHeader?.let { addHeader("Authorization", it) }
                            }
                            .crossfade(true)
                            .build(),
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
                            .background(placeholderColors[colorIndex]),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = book.title.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF5D4037),
                        )
                    }
                }

                // 3-dot menu
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (onRelink != null) {
                            DropdownMenuItem(
                                text = { Text("Relink to Server") },
                                onClick = {
                                    showMenu = false
                                    onRelink()
                                },
                            )
                        }
                        if (onSyncProgress != null) {
                            DropdownMenuItem(
                                text = { Text("Sync Progress") },
                                onClick = {
                                    showMenu = false
                                    onSyncProgress()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }

                // Source badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isFromServer) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isFromServer) Icons.Default.CloudDone else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isFromServer) "Server" else "Local",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
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
                if (progress != null && progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
