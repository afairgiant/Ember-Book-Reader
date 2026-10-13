package com.ember.reader.ui.notes

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.BookMarkKind
import com.ember.reader.ui.book.components.BookMarkRow
import com.ember.reader.ui.common.BookCoverImage
import com.ember.reader.ui.common.LoadingScreen
import com.ember.reader.ui.reader.common.ReaderTarget
import com.ember.reader.ui.settings.components.SettingsChoiceChips
import com.ember.reader.ui.settings.components.SettingsGroup
import com.ember.reader.ui.settings.components.SettingsScreenHeader
import com.ember.reader.ui.settings.components.SettingsToggleRow
import java.time.Instant

/**
 * Bookmarks, highlights and notes from every book on the device, book by book.
 *
 * @param onOpenReader opens a book at one of them, or where it was left for a null target.
 */
@Composable
fun NotesScreen(
    onNavigateBack: () -> Unit,
    onOpenBook: (bookId: String) -> Unit,
    onOpenReader: (bookId: String, format: BookFormat, target: ReaderTarget?) -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    // What is being exported, while the export dialog is up: everything shown, or one book.
    var exporting by remember { mutableStateOf<List<NotesBookGroup>?>(null) }

    NotesContent(
        uiState = uiState,
        kind = kind,
        query = query,
        onKindChange = viewModel::showKind,
        onQueryChange = viewModel::search,
        onIncludeGrimmory = viewModel::includeGrimmory,
        onNavigateBack = onNavigateBack,
        onOpenBook = onOpenBook,
        onOpenMark = viewModel::open,
        onExport = { exporting = it }
    )

    exporting?.let { groups ->
        NotesExportDialog(groups = groups, onDismiss = { exporting = null })
    }

    val opening by viewModel.opening.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(opening) {
        when (val state = opening) {
            is NotesOpening.Ready -> {
                viewModel.closeOpening()
                if (state.placeMissing) {
                    Toast.makeText(context, R.string.notes_place_missing, Toast.LENGTH_LONG).show()
                }
                onOpenReader(state.bookId, state.format, state.target)
            }
            NotesOpening.DownloadFailed -> {
                viewModel.closeOpening()
                Toast.makeText(context, R.string.notes_download_failed, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }
    opening?.let { state ->
        NotesOpeningDialog(state = state, onConfirm = viewModel::confirmDownload, onClose = viewModel::closeOpening)
    }
}

/** What opening an entry has to ask or show before the reader is up; nothing for most. */
@Composable
private fun NotesOpeningDialog(state: NotesOpening, onConfirm: () -> Unit, onClose: () -> Unit) {
    when (state) {
        is NotesOpening.ConfirmDownload -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.notes_download_title)) },
            text = { Text(stringResource(R.string.notes_download_message, state.book.title)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.download)) } },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }
        )
        is NotesOpening.Downloading -> NotesWaitDialog(
            text = stringResource(R.string.notes_downloading, state.book.title),
            onClose = onClose
        )
        NotesOpening.Locating -> NotesWaitDialog(
            text = stringResource(R.string.notes_locating),
            onClose = onClose
        )
        NotesOpening.DownloadFailed, is NotesOpening.Ready -> {}
    }
}

@Composable
private fun NotesWaitDialog(text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.notes_stop_waiting)) } }
    )
}

@Composable
private fun NotesContent(
    uiState: NotesUiState,
    kind: BookMarkKind?,
    query: String,
    onKindChange: (BookMarkKind?) -> Unit,
    onQueryChange: (String) -> Unit,
    onIncludeGrimmory: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenBook: (bookId: String) -> Unit,
    onOpenMark: (Book, BookMarkItem) -> Unit,
    onExport: (List<NotesBookGroup>) -> Unit
) {
    val shown = (uiState as? NotesUiState.Ready)?.groups.orEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsScreenHeader(
            overline = stringResource(R.string.settings),
            title = stringResource(R.string.notes_screen_title),
            onNavigateBack = onNavigateBack,
            actions = {
                // Exports what the filter and search leave on screen.
                IconButton(onClick = { onExport(shown) }, enabled = shown.isNotEmpty()) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.notes_export_all_cd))
                }
            }
        )
        when (uiState) {
            NotesUiState.Loading -> LoadingScreen()
            is NotesUiState.Ready -> if (!uiState.hasAny) {
                uiState.grimmory?.let { GrimmoryRow(it, onIncludeGrimmory) }
                if (uiState.grimmory?.loading != true) NotesMessage(stringResource(R.string.notes_empty))
            } else {
                NotesSearchField(query = query, onQueryChange = onQueryChange)
                uiState.grimmory?.let { GrimmoryRow(it, onIncludeGrimmory) }
                SettingsChoiceChips(
                    options = listOf<BookMarkKind?>(null) + BookMarkKind.entries,
                    selected = kind,
                    onSelect = onKindChange,
                    labelOf = { it?.label ?: "All" }
                )
                if (uiState.groups.isEmpty()) {
                    NotesMessage(stringResource(R.string.notes_no_match))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.groups, key = { it.book.id }) { group ->
                            NotesBookCard(
                                group = group,
                                onOpenBook = { onOpenBook(group.book.id) },
                                onOpenMark = { mark -> onOpenMark(group.book, mark) },
                                onExport = { onExport(listOf(group)) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesBookCard(
    group: NotesBookGroup,
    onOpenBook: () -> Unit,
    onOpenMark: (BookMarkItem) -> Unit,
    onExport: () -> Unit
) {
    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBook)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverImage(
                book = group.book,
                modifier = Modifier
                    .width(40.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(group.book.author, countLabel(group.marks.size)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onExport) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.notes_export_book_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        group.marks.forEach { mark ->
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            BookMarkRow(mark, onClick = { onOpenMark(mark) })
        }
    }
}

/** The switch that adds what Grimmory holds, with how reading it is going underneath. */
@Composable
private fun GrimmoryRow(grimmory: GrimmoryInclusion, onInclude: (Boolean) -> Unit) {
    Column {
        SettingsToggleRow(
            icon = Icons.Outlined.Cloud,
            title = stringResource(R.string.notes_include_grimmory),
            subtitle = stringResource(R.string.notes_include_grimmory_summary),
            checked = grimmory.included,
            onCheckedChange = onInclude
        )
        if (grimmory.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (grimmory.failedServers.isNotEmpty()) {
            Text(
                text = stringResource(R.string.notes_grimmory_failed, grimmory.failedServers.joinToString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

private fun countLabel(count: Int): String = if (count == 1) "1 entry" else "$count entries"

@Composable
private fun NotesSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.notes_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.notes_clear_search_cd))
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

@Composable
private fun NotesMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun NotesContentPreview() {
    val book = Book(id = "1", title = "A Big Hand for the Doctor", author = "Eoin Colfer", format = BookFormat.EPUB)
    val marks = listOf(
        BookMarkItem("h1", BookMarkKind.NOTE, "She is so pretty, he thought.", "Susan", Instant.EPOCH, "Chapter 4"),
        BookMarkItem("b1", BookMarkKind.BOOKMARK, "Bookmark", null, Instant.EPOCH, "Chapter 1")
    )
    MaterialTheme {
        NotesContent(
            uiState = NotesUiState.Ready(listOf(NotesBookGroup(book, marks)), hasAny = true),
            kind = null,
            query = "",
            onKindChange = {},
            onQueryChange = {},
            onIncludeGrimmory = {},
            onNavigateBack = {},
            onOpenBook = {},
            onOpenMark = { _, _ -> },
            onExport = {}
        )
    }
}
