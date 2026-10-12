package com.ember.reader.ui.catalog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
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
import com.ember.reader.core.repository.CatalogSeriesViewMode
import com.ember.reader.ui.common.CoverImage
import com.ember.reader.ui.common.ErrorScreen
import com.ember.reader.ui.common.LoadingScreen
import com.ember.reader.ui.settings.components.SettingsLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeed: (path: String) -> Unit,
    onNavigateToBooks: (path: String, title: String?) -> Unit,
    showBack: Boolean = true,
    onOpenBookDetail: (bookId: String) -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val seriesSort by viewModel.seriesSort.collectAsStateWithLifecycle()
    val seriesViewMode by viewModel.seriesViewMode.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val shelfPreviews by viewModel.shelfPreviews.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
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
                                is CatalogUiState.SeriesSuccess -> "Series"
                                else -> stringResource(R.string.catalog_title)
                            }
                        },
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (editMode) {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Done editing")
                        }
                    } else if (showBack) {
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
                        if (viewModel.isSeriesView) {
                            IconButton(
                                onClick = {
                                    viewModel.setSeriesViewMode(
                                        when (seriesViewMode) {
                                            CatalogSeriesViewMode.GRID -> CatalogSeriesViewMode.LIST
                                            CatalogSeriesViewMode.LIST -> CatalogSeriesViewMode.GRID
                                        }
                                    )
                                },
                            ) {
                                Icon(
                                    when (seriesViewMode) {
                                        CatalogSeriesViewMode.GRID -> Icons.Default.GridView
                                        CatalogSeriesViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                    },
                                    contentDescription = "Toggle layout",
                                )
                            }
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
                    entryIcon = if (viewModel.isAuthorsView) Icons.Default.Person else null,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            onNavigateToBooks(
                                "grimmory:search=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}",
                                searchQuery
                            )
                            searchQuery = ""
                        }
                    },
                    onNavigateToFeed = onNavigateToFeed,
                    onNavigateToBooks = onNavigateToBooks,
                    onRefresh = viewModel::refresh,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            is CatalogUiState.SeriesSuccess -> {
                SeriesCatalogContent(
                    entries = state.entries,
                    viewMode = seriesViewMode,
                    seriesSort = seriesSort,
                    onSeriesSortChange = viewModel::setSeriesSort,
                    onSeriesClick = { seriesName ->
                        onNavigateToBooks(
                            "grimmory:seriesName=${java.net.URLEncoder.encode(seriesName, "UTF-8")}",
                            seriesName
                        )
                    },
                    onRefresh = viewModel::refresh,
                    isRefreshing = isRefreshing,
                    serverUrl = state.serverUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            is CatalogUiState.GrimmorySuccess -> {
                GrimmoryCatalogContent(
                    catalog = state.catalog,
                    hiddenEntryIds = state.hiddenEntryIds,
                    shelfPreviews = shelfPreviews,
                    onOpenBook = onOpenBookDetail,
                    editMode = editMode,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            onNavigateToBooks(
                                "grimmory:search=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}",
                                searchQuery
                            )
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
                                onNavigateToBooks(entry.href, entry.title)
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
    /** The icon for every row, when the list is all one kind of thing; null picks by title. */
    entryIcon: ImageVector?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onNavigateToFeed: (String) -> Unit,
    onNavigateToBooks: (path: String, title: String?) -> Unit,
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
            run {
                CatalogSearchField(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchSubmit = onSearchSubmit
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(feed.entries, key = { _, entry -> entry.id }) { index, entry ->
                    OpdsCatalogEntryCard(
                        entry = entry,
                        icon = entryIcon ?: iconForOpdsFeedEntry(entry.title),
                        first = index == 0,
                        last = index == feed.entries.lastIndex,
                        onClick = {
                            when {
                                entry.href == "grimmory:series" ||
                                    entry.href == "grimmory:authors" -> {
                                    onNavigateToFeed(entry.href)
                                }
                                entry.href.startsWith("grimmory:") -> {
                                    onNavigateToBooks(entry.href, entry.title)
                                }
                                entry.href.contains("catalog") ||
                                    entry.href.contains("?page=") ||
                                    entry.href.contains("recent") ||
                                    entry.href.contains("surprise") -> {
                                    onNavigateToBooks(entry.href, entry.title)
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
private fun OpdsCatalogEntryCard(
    entry: OpdsFeedEntry,
    icon: ImageVector,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit
) {
    CatalogRow(
        icon = icon,
        iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        iconTint = MaterialTheme.colorScheme.primary,
        title = entry.title,
        subtitle = entry.content,
        first = first,
        last = last,
        onClick = onClick
    ) {
        Icon(
            Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    shelfPreviews: Map<CatalogEntryType, List<CatalogShelfBook>>,
    editMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onEntryClick: (GrimmoryCatalogEntry) -> Unit,
    onOpenBook: (bookId: String) -> Unit,
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
            if (!editMode) {
                CatalogSearchField(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchSubmit = onSearchSubmit
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                for (section in sections) {
                    // A reading shortcut is a row of covers unless it is being edited or
                    // turned out to be empty; then it is an ordinary row like the rest.
                    val (shelves, rows) = section.entries.partition { entry ->
                        !editMode && entry.type in SHELF_TYPES && shelfPreviews[entry.type]?.isEmpty() != true
                    }
                    items(shelves, key = { "shelf:${it.id}" }) { entry ->
                        CatalogShelf(
                            title = entry.title,
                            books = shelfPreviews[entry.type],
                            onSeeAll = { onEntryClick(entry) },
                            onOpenBook = onOpenBook
                        )
                    }
                    if (rows.isEmpty()) continue
                    item(key = "header:${section.label ?: rows.first().id}") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            if (section != sections.first() || shelves.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            section.label?.let { label ->
                                SettingsLabel(label, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                            }
                        }
                    }
                    itemsIndexed(rows, key = { _, entry -> entry.id }) { index, entry ->
                        val isHidden = entry.id in hiddenEntryIds
                        GrimmoryCatalogEntryCard(
                            entry = entry,
                            first = index == 0,
                            last = index == rows.lastIndex,
                            editMode = editMode,
                            isHidden = isHidden,
                            onClick = { if (!editMode) onEntryClick(entry) },
                            onToggleHidden = { onToggleHidden(entry.id, isHidden) },
                            onMoveUp = { onMoveUp(entry.id) },
                            onMoveDown = { onMoveDown(entry.id) }
                        )
                    }
                    item(key = "gap:${rows.first().id}") { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private val SHELF_TYPES = setOf(CatalogEntryType.CONTINUE_READING, CatalogEntryType.RECENTLY_ADDED)

@Composable
private fun GrimmoryCatalogEntryCard(
    entry: GrimmoryCatalogEntry,
    first: Boolean,
    last: Boolean,
    editMode: Boolean,
    isHidden: Boolean,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val (defaultIcon, iconBackground, iconTint) = iconStyleForType(entry.type)
    val resolvedIcon = GrimmoryIconMapper.resolve(entry.serverIcon) ?: defaultIcon

    CatalogRow(
        icon = resolvedIcon,
        iconBackground = iconBackground,
        iconTint = iconTint,
        title = entry.title,
        subtitle = entry.subtitle,
        first = first,
        last = last,
        modifier = Modifier.padding(horizontal = 16.dp),
        contentAlpha = if (editMode && isHidden) 0.4f else 1f,
        onClick = if (editMode) null else onClick
    ) {
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

private val GroupCorner = 24.dp
private val IconTileShape = RoundedCornerShape(12.dp)

/**
 * One row of a catalogue list. Consecutive rows read as a single rounded group: only the
 * [first] row has rounded top corners and only the [last] has rounded bottom ones, with a
 * hairline between rows. The rows stay separate lazy items so a long feed still scrolls well.
 */
@Composable
private fun CatalogRow(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    subtitle: String?,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(
        topStart = if (first) GroupCorner else 0.dp,
        topEnd = if (first) GroupCorner else 0.dp,
        bottomStart = if (last) GroupCorner else 0.dp,
        bottomEnd = if (last) GroupCorner else 0.dp
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (!first) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 72.dp, end = 16.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 12.dp,
                    top = if (first) 14.dp else 10.dp,
                    bottom = if (last) 14.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(IconTileShape)
                    .background(iconBackground.copy(alpha = iconBackground.alpha * contentAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = contentAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing()
        }
    }
}

private data class IconStyle(
    val icon: ImageVector,
    val background: Color,
    val tint: Color
)

/**
 * The icon for each kind of entry, in a soft tile of its own colour: the accent for libraries
 * and magic shelves, the tertiary colour for the reading shortcuts, and neutral for the rest.
 */
@Composable
private fun iconStyleForType(type: CatalogEntryType): IconStyle {
    val colorScheme = MaterialTheme.colorScheme
    fun style(icon: ImageVector, color: Color) = IconStyle(icon, color.copy(alpha = 0.14f), color)
    return when (type) {
        CatalogEntryType.CONTINUE_READING -> style(Icons.Default.AutoStories, colorScheme.tertiary)
        CatalogEntryType.RECENTLY_ADDED -> style(Icons.Default.NewReleases, colorScheme.tertiary)
        CatalogEntryType.LIBRARY -> style(Icons.AutoMirrored.Filled.LibraryBooks, colorScheme.primary)
        CatalogEntryType.SHELF -> style(Icons.Default.CollectionsBookmark, colorScheme.secondary)
        CatalogEntryType.MAGIC_SHELF -> style(Icons.Default.AutoAwesome, colorScheme.primary)
        CatalogEntryType.SERIES -> style(Icons.Default.CollectionsBookmark, colorScheme.onSurfaceVariant)
        CatalogEntryType.AUTHORS -> style(Icons.Default.Person, colorScheme.onSurfaceVariant)
        CatalogEntryType.ALL_BOOKS -> style(Icons.AutoMirrored.Filled.MenuBook, colorScheme.onSurfaceVariant)
    }
}

// ── Shared ──────────────────────────────────────────────────────────────────

@Composable
private fun CatalogSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(R.string.search_catalog)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
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
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearchSubmit() }
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        )
    )
}

private val ShelfCoverShape = RoundedCornerShape(12.dp)
private val ShelfCoverWidth = 104.dp

/**
 * A reading shortcut shown as a row of covers with a way to the full list. [books] is null
 * while the row is loading, which shows blank covers so the page does not jump when it fills.
 */
@Composable
private fun CatalogShelf(
    title: String,
    books: List<CatalogShelfBook>?,
    onSeeAll: () -> Unit,
    onOpenBook: (bookId: String) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsLabel(title, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (books == null) {
                items(4) {
                    Box(
                        modifier = Modifier
                            .width(ShelfCoverWidth)
                            .aspectRatio(0.67f)
                            .clip(ShelfCoverShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            } else {
                items(books, key = { it.localBookId }) { book ->
                    Column(
                        modifier = Modifier
                            .width(ShelfCoverWidth)
                            .clip(ShelfCoverShape)
                            .clickable { onOpenBook(book.localBookId) }
                    ) {
                        CoverImage(
                            coverUrl = book.coverUrl,
                            title = book.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.67f)
                                .clip(ShelfCoverShape)
                        )
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                        )
                        book.author?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
