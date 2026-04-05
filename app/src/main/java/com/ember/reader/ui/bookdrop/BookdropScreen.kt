package com.ember.reader.ui.bookdrop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.core.grimmory.BookdropMetadata
import com.ember.reader.core.grimmory.GrimmoryAppLibraryWithPaths
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookdropScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookdropViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Drop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = viewModel::rescan) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState) {
            is BookdropUiState.Loading -> LoadingScreen(Modifier.padding(padding))

            is BookdropUiState.NoServer -> ErrorScreen(
                message = "No Grimmory server connected",
                modifier = Modifier.padding(padding),
            )

            is BookdropUiState.Error -> ErrorScreen(
                message = state.message,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::refresh,
            )

            is BookdropUiState.Success -> {
                BookdropContent(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onToggleExpanded = viewModel::toggleExpanded,
                    onToggleChecked = viewModel::toggleChecked,
                    onSelectAll = viewModel::selectAll,
                    onDeselectAll = viewModel::deselectAll,
                    onSelectFileLibrary = viewModel::selectFileLibrary,
                    onSelectFilePath = viewModel::selectFilePath,
                    onApplyFetchedField = viewModel::applyFetchedField,
                    onUpdateField = viewModel::updateField,
                    onFinalize = viewModel::finalizeSelected,
                    onDiscard = viewModel::discardSelected,
                )
            }
        }
    }
}

@Composable
private fun BookdropContent(
    state: BookdropUiState.Success,
    modifier: Modifier = Modifier,
    onToggleExpanded: (Long) -> Unit,
    onToggleChecked: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSelectFileLibrary: (Long, Long?) -> Unit,
    onSelectFilePath: (Long, Long?) -> Unit,
    onApplyFetchedField: (Long, String) -> Unit,
    onUpdateField: (Long, String, String) -> Unit,
    onFinalize: () -> Unit,
    onDiscard: () -> Unit,
) {
    val checkedCount = state.files.count { it.isChecked }
    val allCheckedHaveLibrary = state.files.filter { it.isChecked }.all { it.libraryId != null }

    Column(modifier = modifier.fillMaxSize()) {
        // Controls bar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("All", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onDeselectAll,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("None", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onDiscard,
                    enabled = checkedCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Discard", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onFinalize,
                    enabled = checkedCount > 0 && allCheckedHaveLibrary,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (checkedCount > 0) "Finalize ($checkedCount)" else "Finalize",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (state.files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No pending files",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.files, key = { it.file.id }) { fileState ->
                    BookdropFileCard(
                        fileState = fileState,
                        libraries = state.libraries,
                        onToggleExpanded = { onToggleExpanded(fileState.file.id) },
                        onToggleChecked = { onToggleChecked(fileState.file.id) },
                        onSelectLibrary = { libraryId -> onSelectFileLibrary(fileState.file.id, libraryId) },
                        onSelectPath = { pathId -> onSelectFilePath(fileState.file.id, pathId) },
                        onApplyFetchedField = { field -> onApplyFetchedField(fileState.file.id, field) },
                        onUpdateField = { field, value -> onUpdateField(fileState.file.id, field, value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookdropFileCard(
    fileState: BookdropFileState,
    libraries: List<GrimmoryAppLibraryWithPaths>,
    onToggleExpanded: () -> Unit,
    onToggleChecked: () -> Unit,
    onSelectLibrary: (Long?) -> Unit,
    onSelectPath: (Long?) -> Unit,
    onApplyFetchedField: (String) -> Unit,
    onUpdateField: (String, String) -> Unit,
) {
    val file = fileState.file
    val metadata = fileState.editedMetadata
    val authors = metadata.authors?.joinToString(", ") ?: ""
    val format = file.fileName?.substringAfterLast(".")?.uppercase() ?: ""

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Collapsed header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onToggleChecked,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        if (fileState.isChecked) Icons.Default.CheckBox
                        else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (fileState.isChecked) "Deselect" else "Select",
                        tint = if (fileState.isChecked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        metadata.title ?: file.fileName ?: "Untitled",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (authors.isNotEmpty() || format.isNotEmpty()) {
                        Text(
                            listOfNotNull(
                                authors.ifEmpty { null },
                                format.ifEmpty { null },
                            ).joinToString(" \u00B7 "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Icon(
                    if (fileState.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (fileState.isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = fileState.isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    // Per-file library + path
                    val selectedLibrary = libraries.firstOrNull { it.id == fileState.libraryId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryDropdown(
                            libraries = libraries,
                            selectedLibraryId = fileState.libraryId,
                            onSelect = onSelectLibrary,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedLibrary != null && selectedLibrary.paths.isNotEmpty()) {
                            PathDropdown(
                                paths = selectedLibrary.paths,
                                selectedPathId = fileState.pathId,
                                onSelect = onSelectPath,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    MetadataSection(
                        editedMetadata = metadata,
                        fetchedMetadata = file.fetchedMetadata,
                        onApplyFetched = onApplyFetchedField,
                        onUpdateField = onUpdateField,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataSection(
    editedMetadata: BookdropMetadata,
    fetchedMetadata: BookdropMetadata?,
    onApplyFetched: (String) -> Unit,
    onUpdateField: (String, String) -> Unit,
) {
    val fields = remember(editedMetadata, fetchedMetadata) { listOf(
        MetadataField("title", "Title", editedMetadata.title, fetchedMetadata?.title),
        MetadataField("subtitle", "Subtitle", editedMetadata.subtitle, fetchedMetadata?.subtitle),
        MetadataField("authors", "Authors", editedMetadata.authors?.joinToString(", "), fetchedMetadata?.authors?.joinToString(", ")),
        MetadataField("publisher", "Publisher", editedMetadata.publisher, fetchedMetadata?.publisher),
        MetadataField("publishedDate", "Published", editedMetadata.publishedDate, fetchedMetadata?.publishedDate),
        MetadataField("seriesName", "Series", editedMetadata.seriesName, fetchedMetadata?.seriesName),
        MetadataField("seriesNumber", "Book #", editedMetadata.seriesNumber?.toString(), fetchedMetadata?.seriesNumber?.toString()),
        MetadataField("seriesTotal", "Total Books", editedMetadata.seriesTotal?.toString(), fetchedMetadata?.seriesTotal?.toString()),
        MetadataField("language", "Language", editedMetadata.language, fetchedMetadata?.language),
        MetadataField("isbn10", "ISBN-10", editedMetadata.isbn10, fetchedMetadata?.isbn10),
        MetadataField("isbn13", "ISBN-13", editedMetadata.isbn13, fetchedMetadata?.isbn13),
        MetadataField("pageCount", "Pages", editedMetadata.pageCount?.toString(), fetchedMetadata?.pageCount?.toString()),
        MetadataField("asin", "ASIN", editedMetadata.asin, fetchedMetadata?.asin),
        MetadataField("googleId", "Google ID", editedMetadata.googleId, fetchedMetadata?.googleId),
        MetadataField("amazonRating", "Amazon \u2605", editedMetadata.amazonRating?.toString(), fetchedMetadata?.amazonRating?.toString()),
        MetadataField("amazonReviewCount", "Amazon #", editedMetadata.amazonReviewCount?.toString(), fetchedMetadata?.amazonReviewCount?.toString()),
        MetadataField("goodreadsId", "Goodreads ID", editedMetadata.goodreadsId, fetchedMetadata?.goodreadsId),
        MetadataField("goodreadsRating", "Goodreads \u2605", editedMetadata.goodreadsRating?.toString(), fetchedMetadata?.goodreadsRating?.toString()),
        MetadataField("goodreadsReviewCount", "Goodreads #", editedMetadata.goodreadsReviewCount?.toString(), fetchedMetadata?.goodreadsReviewCount?.toString()),
        MetadataField("hardcoverBookId", "HC Book ID", editedMetadata.hardcoverBookId, fetchedMetadata?.hardcoverBookId),
        MetadataField("hardcoverId", "Hardcover ID", editedMetadata.hardcoverId, fetchedMetadata?.hardcoverId),
        MetadataField("hardcoverRating", "Hardcover \u2605", editedMetadata.hardcoverRating?.toString(), fetchedMetadata?.hardcoverRating?.toString()),
        MetadataField("hardcoverReviewCount", "Hardcover #", editedMetadata.hardcoverReviewCount?.toString(), fetchedMetadata?.hardcoverReviewCount?.toString()),
        MetadataField("categories", "Genres", editedMetadata.categories?.joinToString(", "), fetchedMetadata?.categories?.joinToString(", ")),
        MetadataField("description", "Description", editedMetadata.description, fetchedMetadata?.description, isMultiLine = true),
    ) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fields.forEach { field ->
            MetadataComparisonRow(
                field = field,
                onApplyFetched = { onApplyFetched(field.key) },
                onUpdate = { value -> onUpdateField(field.key, value) },
            )
        }
    }
}

private data class MetadataField(
    val key: String,
    val label: String,
    val currentValue: String?,
    val fetchedValue: String?,
    val isMultiLine: Boolean = false,
)

@Composable
private fun MetadataComparisonRow(
    field: MetadataField,
    onApplyFetched: () -> Unit,
    onUpdate: (String) -> Unit,
) {
    val current = field.currentValue ?: ""
    val fetched = field.fetchedValue ?: ""
    val hasFetched = fetched.isNotEmpty() && fetched != current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Label
            Text(
                field.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Current value (editable)
            var isEditing by remember { mutableStateOf(false) }
            var editText by remember(current) { mutableStateOf(current) }

            if (isEditing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .padding(vertical = 2.dp),
                    singleLine = !field.isMultiLine,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        "Save",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            onUpdate(editText)
                            isEditing = false
                        },
                    )
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            editText = current
                            isEditing = false
                        },
                    )
                }
            } else {
                Text(
                    current.ifEmpty { "(empty)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (field.isMultiLine) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isEditing = true },
                )
            }

            // Fetched value (if different)
            if (hasFetched) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Apply fetched value",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onApplyFetched),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        fetched,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = if (field.isMultiLine) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onApplyFetched),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryDropdown(
    libraries: List<GrimmoryAppLibraryWithPaths>,
    selectedLibraryId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = libraries.firstOrNull { it.id == selectedLibraryId }?.name ?: "Library"

    Box(modifier = modifier) {
        FilterChip(
            selected = selectedLibraryId != null,
            onClick = { expanded = true },
            label = {
                Text(
                    selectedName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            libraries.forEach { library ->
                DropdownMenuItem(
                    text = { Text("${library.name} (${library.bookCount})") },
                    onClick = {
                        onSelect(library.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PathDropdown(
    paths: List<com.ember.reader.core.grimmory.LibraryPathSummary>,
    selectedPathId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPath = paths.firstOrNull { it.id == selectedPathId }?.path?.substringAfterLast("/") ?: "Path"

    Box(modifier = modifier) {
        FilterChip(
            selected = selectedPathId != null,
            onClick = { expanded = true },
            label = {
                Text(
                    selectedPath,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Default") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            paths.forEach { path ->
                DropdownMenuItem(
                    text = { Text(path.path) },
                    onClick = {
                        onSelect(path.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
