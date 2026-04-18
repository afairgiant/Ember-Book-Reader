package com.ember.reader.ui.editmetadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.core.grimmory.GrimmoryBookMetadata
import com.ember.reader.core.grimmory.MetadataProvider
import com.ember.reader.ui.common.BookCoverImage
import com.ember.reader.ui.editmetadata.components.CoverOverlay
import com.ember.reader.ui.editmetadata.components.EditableMetadataRow
import com.ember.reader.ui.editmetadata.components.EditableTagRow
import com.ember.reader.ui.editmetadata.components.SearchMetadataSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataScreen(
    onNavigateBack: (saved: Boolean) -> Unit,
    viewModel: EditMetadataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val coverAppliedTicker by viewModel.coverAppliedTicker.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit metadata") },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack(saved) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val s = (state as? EditMetadataUiState.Success)?.state
                    if (s != null) {
                        val enabled = s.isDirty && !s.saving && !s.readOnly
                        TextButton(onClick = { viewModel.save() }, enabled = enabled) {
                            Text(if (s.saving) "Saving…" else "Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        var enlargedCoverUrl by remember { mutableStateOf<String?>(null) }

        // Close the cover preview overlay when a cover apply succeeds.
        LaunchedEffect(coverAppliedTicker) {
            if (coverAppliedTicker > 0) enlargedCoverUrl = null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                EditMetadataUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is EditMetadataUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                }
                is EditMetadataUiState.Success -> EditMetadataContent(
                    state = current.state,
                    onEdit = viewModel::editField,
                    onSearchFormChange = viewModel::updateSearchForm,
                    onToggleProvider = viewModel::toggleProvider,
                    onStartSearch = viewModel::startSearch,
                    onCancelSearch = viewModel::cancelSearch,
                    onSelectCandidate = viewModel::selectCandidate,
                    onCloseCandidate = viewModel::closeCandidate,
                    onApplyField = viewModel::applyFetchedField,
                    onApplyAll = viewModel::applyAllFetched,
                    onCoverClick = { enlargedCoverUrl = it },
                    onApplyCover = viewModel::applyCover,
                )
            }

            enlargedCoverUrl?.let { url ->
                val successState = (state as? EditMetadataUiState.Success)?.state
                val applyingCoverUrl = successState?.applyingCoverUrl
                // When zooming the book's current cover, hide the "Use this cover" button —
                // it would just re-upload the cover to itself.
                val isCurrentCover = url == successState?.book?.coverUrl
                CoverOverlay(
                    coverUrl = url,
                    applyingCoverUrl = applyingCoverUrl,
                    onApplyCover = if (isCurrentCover) {
                        null
                    } else {
                        { viewModel.applyCover(url) }
                    },
                    onDismiss = { enlargedCoverUrl = null },
                )
            }
        }
    }
}

private data class FieldSpec(
    val key: MetadataFieldKey,
    val label: String,
    val multiLine: Boolean = false,
    val isTagField: Boolean = false,
)

private data class FieldSection(
    val title: String,
    val fields: List<FieldSpec>,
)

private val basicFields = listOf(
    FieldSpec(MetadataFieldKey.Title, "Title"),
    FieldSpec(MetadataFieldKey.Subtitle, "Subtitle"),
    FieldSpec(MetadataFieldKey.Authors, "Authors (comma-separated)"),
    FieldSpec(MetadataFieldKey.Publisher, "Publisher"),
    FieldSpec(MetadataFieldKey.PublishedDate, "Published date"),
)

private val seriesFields = listOf(
    FieldSpec(MetadataFieldKey.SeriesName, "Series"),
    FieldSpec(MetadataFieldKey.SeriesNumber, "Series #"),
    FieldSpec(MetadataFieldKey.SeriesTotal, "Series total"),
)

private val detailFields = listOf(
    FieldSpec(MetadataFieldKey.Language, "Language"),
    FieldSpec(MetadataFieldKey.Isbn13, "ISBN-13"),
    FieldSpec(MetadataFieldKey.Isbn10, "ISBN-10"),
    FieldSpec(MetadataFieldKey.PageCount, "Pages"),
    FieldSpec(MetadataFieldKey.AgeRating, "Age rating"),
    FieldSpec(MetadataFieldKey.ContentRating, "Content rating"),
    FieldSpec(MetadataFieldKey.ExternalUrl, "External URL"),
)

private val localDetailFields = listOf(
    FieldSpec(MetadataFieldKey.Language, "Language"),
    FieldSpec(MetadataFieldKey.PageCount, "Pages"),
)

private val arrayFields = listOf(
    FieldSpec(MetadataFieldKey.Categories, "Categories", isTagField = true),
    FieldSpec(MetadataFieldKey.Moods, "Moods", isTagField = true),
    FieldSpec(MetadataFieldKey.Tags, "Tags", isTagField = true),
)

private val descriptionFields = listOf(
    FieldSpec(MetadataFieldKey.Description, "Description", multiLine = true),
)

private val audiobookFields = listOf(
    FieldSpec(MetadataFieldKey.Narrator, "Narrator"),
    FieldSpec(MetadataFieldKey.Abridged, "Abridged (true/false)"),
)

private val providerCommonFields = listOf(
    FieldSpec(MetadataFieldKey.Asin, "ASIN"),
    FieldSpec(MetadataFieldKey.AmazonRating, "Amazon rating"),
    FieldSpec(MetadataFieldKey.AmazonReviewCount, "Amazon review count"),
    FieldSpec(MetadataFieldKey.GoodreadsId, "Goodreads ID"),
    FieldSpec(MetadataFieldKey.GoodreadsRating, "Goodreads rating"),
    FieldSpec(MetadataFieldKey.GoodreadsReviewCount, "Goodreads review count"),
    FieldSpec(MetadataFieldKey.GoogleId, "Google ID"),
    FieldSpec(MetadataFieldKey.HardcoverId, "Hardcover ID"),
    FieldSpec(MetadataFieldKey.HardcoverBookId, "Hardcover book ID"),
    FieldSpec(MetadataFieldKey.HardcoverRating, "Hardcover rating"),
    FieldSpec(MetadataFieldKey.HardcoverReviewCount, "Hardcover review count"),
)

private val providerNicheFields = listOf(
    FieldSpec(MetadataFieldKey.ComicvineId, "Comicvine ID"),
    FieldSpec(MetadataFieldKey.DoubanId, "Douban ID"),
    FieldSpec(MetadataFieldKey.DoubanRating, "Douban rating"),
    FieldSpec(MetadataFieldKey.DoubanReviewCount, "Douban review count"),
    FieldSpec(MetadataFieldKey.LubimyczytacId, "Lubimyczytac ID"),
    FieldSpec(MetadataFieldKey.LubimyczytacRating, "Lubimyczytac rating"),
    FieldSpec(MetadataFieldKey.RanobedbId, "Ranobedb ID"),
    FieldSpec(MetadataFieldKey.RanobedbRating, "Ranobedb rating"),
    FieldSpec(MetadataFieldKey.AudibleId, "Audible ID"),
    FieldSpec(MetadataFieldKey.AudibleRating, "Audible rating"),
    FieldSpec(MetadataFieldKey.AudibleReviewCount, "Audible review count"),
)

@Composable
private fun EditMetadataContent(
    state: EditMetadataSuccess,
    onEdit: (MetadataFieldKey, String) -> Unit,
    onSearchFormChange: ((SearchForm) -> SearchForm) -> Unit,
    onToggleProvider: (MetadataProvider) -> Unit,
    onStartSearch: () -> Unit,
    onCancelSearch: () -> Unit,
    onSelectCandidate: (GrimmoryBookMetadata) -> Unit,
    onCloseCandidate: () -> Unit,
    onApplyField: (MetadataFieldKey) -> Unit,
    onApplyAll: () -> Unit,
    onCoverClick: (String) -> Unit,
    onApplyCover: (String) -> Unit,
) {
    val candidate = state.selectedCandidate
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Current book cover as a header. Tap to zoom.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BookCoverImage(
                book = state.book,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(0.67f)
                    .clip(RoundedCornerShape(8.dp)),
                onClick = state.book.coverUrl?.let { url -> { onCoverClick(url) } },
            )
        }

        if (state.isLocal) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    "Changes are saved to Ember's local database only and won't be written into the book file. Other apps won't see these edits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (state.readOnly) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("All metadata is locked for this book.")
                }
            }
        }

        if (!state.isLocal) {
            SearchMetadataSection(
                form = state.searchForm,
                phase = state.searchPhase,
                results = state.searchResults,
                error = state.searchError,
                selectedCandidateId = candidate?.bookId,
                applyingCoverUrl = state.applyingCoverUrl,
                onFormChange = onSearchFormChange,
                onToggleProvider = onToggleProvider,
                onStart = onStartSearch,
                onCancel = onCancelSearch,
                onSelect = onSelectCandidate,
                onCoverClick = onCoverClick,
                onApplyCover = onApplyCover,
            )
        }

        if (candidate != null && !state.isLocal) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Merging from: ${candidate.provider?.name ?: "candidate"}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCloseCandidate) {
                            Icon(Icons.Default.Close, contentDescription = "Close candidate")
                        }
                    }
                    Text(
                        candidate.title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    candidate.authors?.firstOrNull()?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onApplyAll, enabled = !state.readOnly) {
                        Text("Apply all unlocked fields")
                    }
                }
            }
        }

        val sections = buildList {
            add(FieldSection("Basic", basicFields))
            add(FieldSection("Series", if (state.isLocal) seriesFields.take(2) else seriesFields))
            add(FieldSection("Details", if (state.isLocal) localDetailFields else detailFields))
            add(FieldSection("Tags & categories", if (state.isLocal) listOf(arrayFields.first()) else arrayFields))
            add(FieldSection("Description", descriptionFields))
            if (!state.isLocal) {
                add(FieldSection("Audiobook", audiobookFields))
                add(FieldSection("Providers", providerCommonFields))
                add(FieldSection("Other providers", providerNicheFields))
            }
        }

        val candidateEditable = candidate?.let { EditableMetadata.from(it) }
        sections.forEach { section ->
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            section.fields.forEach { spec ->
                val locked = isLocked(state.original, spec.key)
                val fetched = candidateEditable?.get(spec.key).orEmpty()
                if (spec.isTagField) {
                    EditableTagRow(
                        label = spec.label,
                        value = state.edited.get(spec.key),
                        locked = locked || state.readOnly,
                        fetchedValue = fetched.takeIf { it.isNotBlank() && it != state.edited.get(spec.key) },
                        onValueChange = { onEdit(spec.key, it) },
                        onApplyFetched = { onApplyField(spec.key) },
                    )
                } else {
                    EditableMetadataRow(
                        label = spec.label,
                        value = state.edited.get(spec.key),
                        locked = locked || state.readOnly,
                        multiLine = spec.multiLine,
                        fetchedValue = fetched.takeIf { it.isNotBlank() && it != state.edited.get(spec.key) },
                        onValueChange = { onEdit(spec.key, it) },
                        onApplyFetched = { onApplyField(spec.key) },
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
