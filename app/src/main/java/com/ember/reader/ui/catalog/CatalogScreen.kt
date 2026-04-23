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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.reader.R
import com.ember.reader.core.model.CatalogEntryType
import com.ember.reader.core.model.GrimmoryCatalog
import com.ember.reader.core.model.GrimmoryCatalogEntry
import com.ember.reader.core.opds.OpdsFeed
import com.ember.reader.core.opds.OpdsFeedEntry
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeed: (path: String) -> Unit,
    onNavigateToBooks: (path: String) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val seriesSort by viewModel.seriesSort.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editMode) {
                            "Edit Catalog"
                        } else {
                            when (val state = uiState) {
                                is CatalogUiState.OpdsSuccess -> state.feed.title
                                is CatalogUiState.GrimmorySuccess -> state.catalog.serverName
                                else -> stringResource(R.string.catalog_title)
                            }
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (editMode) {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Done editing")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (editMode) {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Reset to Default") },
                                    onClick = {
                                        viewModel.resetPreferences()
                                        showOverflowMenu = false
                                    }
                                )
                            }
                        }
                    } else {
                        if (viewModel.isGrimmoryCatalogRoot) {
                            IconButton(onClick = { viewModel.toggleEditMode() }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit catalog")
                            }
                        }
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {
            CatalogUiState.Loading -> LoadingScreen(Modifier.padding(padding))
            is CatalogUiState.Error -> ErrorScreen(
                message = state.message,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::refresh
            )
            is CatalogUiState.OpdsSuccess -> {
                OpdsCatalogContent(
                    feed = state.feed,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            onNavigateToBooks("grimmory:search=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")
                            searchActive = false
                            searchQuery = ""
                        }
                    },
                    isSeriesView = viewModel.isSeriesView,
                    seriesSort = seriesSort,
                    onSeriesSortChange = viewModel::setSeriesSort,
                    onNavigateToFeed = onNavigateToFeed,
                    onNavigateToBooks = onNavigateToBooks,
                    onRefresh = viewModel::refresh,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            is CatalogUiState.GrimmorySuccess -> {
                GrimmoryCatalogContent(
                    catalog = state.catalog,
                    hiddenEntryIds = state.hiddenEntryIds,
                    editMode = editMode,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            onNavigateToBooks("grimmory:search=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")
                            searchActive = false
                            searchQuery = ""
                        }
                    },
                    onEntryClick = { entry ->
                        when {
                            entry.href == "grimmory:series" ||
                                entry.href == "grimmory:authors" -> {
                                onNavigateToFeed(entry.href)
                            }
                            else -> {
                                onNavigateToBooks(entry.href)
                            }
                        }
                    },
                    onToggleHidden = { entryId, isHidden ->
                        if (isHidden) {
                            viewModel.unhideEntry(entryId)
                        } else {
                            viewModel.hideEntry(entryId)
                        }
                    },
                    onMoveUp = viewModel::moveEntryUp,
                    onMoveDown = viewModel::moveEntryDown,
                    onRefresh = viewModel::refresh,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

// ── OPDS Catalog (generic OPDS servers + Grimmory fallback) ─────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpdsCatalogContent(
    feed: OpdsFeed,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    isSeriesView: Boolean,
    seriesSort: SeriesSortOption,
    onSeriesSortChange: (SeriesSortOption) -> Unit,
    onNavigateToFeed: (String) -> Unit,
    onNavigateToBooks: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (searchActive) {
                CatalogSearchField(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchSubmit = onSearchSubmit
                )
            }
            if (isSeriesView) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SeriesSortOption.entries, key = { it.name }) { option ->
                        FilterChip(
                            selected = seriesSort == option,
                            onClick = { onSeriesSortChange(option) },
                            label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(feed.entries, key = { it.id }) { entry ->
                    OpdsCatalogEntryCard(
                        entry = entry,
                        onClick = {
                            when {
                                entry.href == "grimmory:series" ||
                                    entry.href == "grimmory:authors" -> {
                                    onNavigateToFeed(entry.href)
                                }
                                entry.href.startsWith("grimmory:") -> {
                                    onNavigateToBooks(entry.href)
                                }
                                entry.href.contains("catalog") ||
                                    entry.href.contains("?page=") ||
                                    entry.href.contains("recent") ||
                                    entry.href.contains("surprise") -> {
                                    onNavigateToBooks(entry.href)
                                }
                                else -> {
                                    onNavigateToFeed(entry.href)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun iconForOpdsFeedEntry(title: String): ImageVector = when {
    title.contains("librar", ignoreCase = true) -> Icons.AutoMirrored.Filled.LibraryBooks
    title.contains("shelv", ignoreCase = true) -> Icons.Default.CollectionsBookmark
    title.contains("magic", ignoreCase = true) -> Icons.Default.AutoStories
    title.contains("author", ignoreCase = true) -> Icons.Default.Person
    title.contains("series", ignoreCase = true) -> Icons.Default.CollectionsBookmark
    title.contains("recent", ignoreCase = true) -> Icons.Default.History
    title.contains("surprise", ignoreCase = true) -> Icons.Default.Shuffle
    title.contains("all", ignoreCase = true) -> Icons.AutoMirrored.Filled.LibraryBooks
    else -> Icons.Default.Explore
}

@Composable
private fun OpdsCatalogEntryCard(entry: OpdsFeedEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconForOpdsFeedEntry(entry.title),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entry.content?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Grimmory Catalog (typed, grouped, visually distinct) ────────────────────

private data class CatalogSection(
    val label: String?,
    val entries: List<GrimmoryCatalogEntry>
)

private fun groupIntoSections(entries: List<GrimmoryCatalogEntry>): List<CatalogSection> {
    val quickAccess = entries.filter {
        it.type == CatalogEntryType.CONTINUE_READING || it.type == CatalogEntryType.RECENTLY_ADDED
    }
    val libraries = entries.filter { it.type == CatalogEntryType.LIBRARY }
    val shelves = entries.filter { it.type == CatalogEntryType.SHELF }
    val magicShelves = entries.filter { it.type == CatalogEntryType.MAGIC_SHELF }
    val browse = entries.filter {
        it.type == CatalogEntryType.SERIES ||
            it.type == CatalogEntryType.AUTHORS ||
            it.type == CatalogEntryType.ALL_BOOKS
    }

    return buildList {
        if (quickAccess.isNotEmpty()) add(CatalogSection(null, quickAccess))
        if (libraries.isNotEmpty()) add(CatalogSection("Libraries", libraries))
        if (shelves.isNotEmpty()) add(CatalogSection("Shelves", shelves))
        if (magicShelves.isNotEmpty()) add(CatalogSection("Magic Shelves", magicShelves))
        if (browse.isNotEmpty()) add(CatalogSection("Browse", browse))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrimmoryCatalogContent(
    catalog: GrimmoryCatalog,
    hiddenEntryIds: Set<String>,
    editMode: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onEntryClick: (GrimmoryCatalogEntry) -> Unit,
    onToggleHidden: (entryId: String, isCurrentlyHidden: Boolean) -> Unit,
    onMoveUp: (entryId: String) -> Unit,
    onMoveDown: (entryId: String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val sections = groupIntoSections(catalog.entries)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (searchActive && !editMode) {
                CatalogSearchField(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchSubmit = onSearchSubmit
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                for (section in sections) {
                    section.label?.let { label ->
                        item(key = "header:$label") {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = if (section == sections.first()) 0.dp else 8.dp,
                                    bottom = 4.dp
                                )
                            )
                        }
                    }
                    items(section.entries, key = { it.id }) { entry ->
                        val isHidden = entry.id in hiddenEntryIds
                        GrimmoryCatalogEntryCard(
                            entry = entry,
                            editMode = editMode,
                            isHidden = isHidden,
                            onClick = { if (!editMode) onEntryClick(entry) },
                            onToggleHidden = { onToggleHidden(entry.id, isHidden) },
                            onMoveUp = { onMoveUp(entry.id) },
                            onMoveDown = { onMoveDown(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrimmoryCatalogEntryCard(
    entry: GrimmoryCatalogEntry,
    editMode: Boolean,
    isHidden: Boolean,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val (defaultIcon, iconBackground, iconTint) = iconStyleForType(entry.type)
    val resolvedIcon = GrimmoryIconMapper.resolve(entry.serverIcon) ?: defaultIcon
    val contentAlpha = if (editMode && isHidden) 0.4f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!editMode) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (editMode) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBackground.copy(alpha = contentAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    resolvedIcon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = contentAlpha),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                entry.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (editMode) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleHidden, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isHidden) "Show" else "Hide",
                        tint = if (isHidden) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class IconStyle(
    val icon: ImageVector,
    val background: Color,
    val tint: Color
)

@Composable
private fun iconStyleForType(type: CatalogEntryType): IconStyle {
    val colorScheme = MaterialTheme.colorScheme
    return when (type) {
        CatalogEntryType.CONTINUE_READING -> IconStyle(
            icon = Icons.Default.AutoStories,
            background = colorScheme.tertiaryContainer,
            tint = colorScheme.onTertiaryContainer
        )
        CatalogEntryType.RECENTLY_ADDED -> IconStyle(
            icon = Icons.Default.NewReleases,
            background = colorScheme.tertiaryContainer,
            tint = colorScheme.onTertiaryContainer
        )
        CatalogEntryType.LIBRARY -> IconStyle(
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            background = colorScheme.primaryContainer,
            tint = colorScheme.onPrimaryContainer
        )
        CatalogEntryType.SHELF -> IconStyle(
            icon = Icons.Default.CollectionsBookmark,
            background = colorScheme.secondaryContainer,
            tint = colorScheme.onSecondaryContainer
        )
        CatalogEntryType.MAGIC_SHELF -> IconStyle(
            icon = Icons.Default.AutoAwesome,
            background = colorScheme.inversePrimary,
            tint = colorScheme.onPrimaryContainer
        )
        CatalogEntryType.SERIES -> IconStyle(
            icon = Icons.Default.CollectionsBookmark,
            background = colorScheme.surfaceVariant,
            tint = colorScheme.onSurfaceVariant
        )
        CatalogEntryType.AUTHORS -> IconStyle(
            icon = Icons.Default.Person,
            background = colorScheme.surfaceVariant,
            tint = colorScheme.onSurfaceVariant
        )
        CatalogEntryType.ALL_BOOKS -> IconStyle(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            background = colorScheme.surfaceVariant,
            tint = colorScheme.onSurfaceVariant
        )
    }
}

// ── Shared ──────────────────────────────────────────────────────────────────

@Composable
private fun CatalogSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit
) {
    androidx.compose.material3.OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(R.string.search_catalog)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearchSubmit() }
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        )
    )
}
