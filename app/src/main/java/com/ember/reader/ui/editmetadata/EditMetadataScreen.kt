package com.ember.reader.ui.editmetadata

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.core.grimmory.GrimmoryBookMetadata
import com.ember.reader.core.grimmory.MetadataProvider
import com.ember.reader.core.grimmory.searchableProviders
import com.ember.reader.ui.common.BookCoverImage

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

            // Fullscreen cover overlay
            enlargedCoverUrl?.let { url ->
                val successState = (state as? EditMetadataUiState.Success)?.state
                val applyingCoverUrl = successState?.applyingCoverUrl
                // When zooming the book's current cover, hide the "Use this cover" button —
                // it would just re-upload the cover to itself.
                val isCurrentCover = url == successState?.book?.coverUrl
                CoverOverlay(
                    coverUrl = url,
                    applyingCoverUrl = applyingCoverUrl,
                    onApplyCover = if (isCurrentCover) null else { { viewModel.applyCover(url) } },
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
    FieldSpec(MetadataFieldKey.Categories, "Categories (comma-separated)"),
    FieldSpec(MetadataFieldKey.Moods, "Moods (comma-separated)"),
    FieldSpec(MetadataFieldKey.Tags, "Tags (comma-separated)"),
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

        // Search section — only for Grimmory books
        if (!state.isLocal) SearchSection(
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

        // Candidate merge banner + Apply All (Grimmory only)
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

        // Editor fields — local books only get the fields stored in Room
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

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SearchSection(
    form: SearchForm,
    phase: SearchPhase,
    results: List<GrimmoryBookMetadata>,
    error: String?,
    selectedCandidateId: Long?,
    applyingCoverUrl: String?,
    onFormChange: ((SearchForm) -> SearchForm) -> Unit,
    onToggleProvider: (MetadataProvider) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (GrimmoryBookMetadata) -> Unit,
    onCoverClick: (String) -> Unit,
    onApplyCover: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Search metadata providers",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { v -> onFormChange { it.copy(title = v) } },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = form.author,
                    onValueChange = { v -> onFormChange { it.copy(author = v) } },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = form.isbn,
                    onValueChange = { v -> onFormChange { it.copy(isbn = v) } },
                    label = { Text("ISBN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = form.asin,
                    onValueChange = { v -> onFormChange { it.copy(asin = v) } },
                    label = { Text("ASIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    searchableProviders.forEach { p ->
                        FilterChip(
                            selected = p in form.providers,
                            onClick = { onToggleProvider(p) },
                            label = { Text(p.name) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(
                        onClick = { if (phase == SearchPhase.Running) onCancel() else onStart() },
                    ) {
                        Text(if (phase == SearchPhase.Running) "Cancel" else "Search")
                    }
                    if (phase == SearchPhase.Running) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("${results.size} result(s)", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    // Group by provider
                    val grouped = results.groupBy { it.provider }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grouped.forEach { (provider, candidates) ->
                            ProviderResultGroup(
                                providerName = provider?.name ?: "Unknown",
                                candidates = candidates,
                                selectedCandidateId = selectedCandidateId,
                                applyingCoverUrl = applyingCoverUrl,
                                onSelect = onSelect,
                                onCoverClick = onCoverClick,
                                onApplyCover = onApplyCover,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderResultGroup(
    providerName: String,
    candidates: List<GrimmoryBookMetadata>,
    selectedCandidateId: Long?,
    applyingCoverUrl: String?,
    onSelect: (GrimmoryBookMetadata) -> Unit,
    onCoverClick: (String) -> Unit,
    onApplyCover: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    providerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${candidates.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                ) {
                    candidates.forEach { candidate ->
                        CandidateRow(
                            metadata = candidate,
                            selected = candidate.bookId != null && candidate.bookId == selectedCandidateId,
                            applyingCoverUrl = applyingCoverUrl,
                            onClick = { onSelect(candidate) },
                            onCoverClick = onCoverClick,
                            onApplyCover = onApplyCover,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    metadata: GrimmoryBookMetadata,
    selected: Boolean,
    applyingCoverUrl: String?,
    onClick: () -> Unit,
    onCoverClick: (String) -> Unit = {},
    onApplyCover: (String) -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Small cover thumbnail
            val coverUrl = metadata.thumbnailUrl
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 40.dp, height = 60.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCoverClick(coverUrl) },
                )
                Spacer(Modifier.width(6.dp))
                // "Use this cover" button — stops propagation so it doesn't select the candidate.
                val uploadingThis = applyingCoverUrl == coverUrl
                val anyUploading = applyingCoverUrl != null
                IconButton(
                    onClick = { onApplyCover(coverUrl) },
                    enabled = !anyUploading,
                    modifier = Modifier.size(32.dp),
                ) {
                    if (uploadingThis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = "Use this cover",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    metadata.title.orEmpty().ifEmpty { "(no title)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata.authors?.joinToString(", ")?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val details = buildList {
                    metadata.publishedDate?.let { add(it) }
                    metadata.isbn13?.let { add("ISBN: $it") }
                    metadata.pageCount?.let { add("$it pages") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
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
private fun CoverOverlay(
    coverUrl: String,
    applyingCoverUrl: String?,
    onApplyCover: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var resolution by remember(coverUrl) { mutableStateOf<IntSize?>(null) }
    val uploadingThis = applyingCoverUrl == coverUrl
    val anyUploading = applyingCoverUrl != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Inner column holds the image + resolution caption + apply button.
        // clickable(enabled = false) on the column would block the parent's dismiss tap;
        // instead we simply attach no click handlers so taps on the image area pass
        // through to the parent scrim for dismiss, while the Button intercepts its own tap.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Box {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover preview",
                    contentScale = ContentScale.Fit,
                    onSuccess = { success ->
                        val d = success.result.drawable
                        resolution = IntSize(d.intrinsicWidth, d.intrinsicHeight)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                )
                resolution?.let { size ->
                    Text(
                        "${size.width} × ${size.height}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                Color.Black.copy(alpha = 0.55f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (onApplyCover != null) {
                Button(
                    onClick = onApplyCover,
                    enabled = !anyUploading,
                ) {
                    if (uploadingThis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Applying…")
                    } else {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Use this cover")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableMetadataRow(
    label: String,
    value: String,
    locked: Boolean,
    multiLine: Boolean,
    fetchedValue: String?,
    onValueChange: (String) -> Unit,
    onApplyFetched: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (locked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Locked",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !locked,
                singleLine = !multiLine,
                minLines = if (multiLine) 3 else 1,
                maxLines = if (multiLine) 6 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
            if (fetchedValue != null && !locked) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onApplyFetched),
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Apply fetched value",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        fetchedValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = if (multiLine) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
