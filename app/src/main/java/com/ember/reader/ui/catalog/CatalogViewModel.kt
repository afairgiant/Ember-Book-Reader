package com.ember.reader.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.CatalogEntryType
import com.ember.reader.core.model.GrimmoryCatalog
import com.ember.reader.core.model.GrimmoryCatalogEntry
import com.ember.reader.core.model.GrimmoryCatalogLibrary
import com.ember.reader.core.model.GrimmoryCatalogMagicShelf
import com.ember.reader.core.model.GrimmoryCatalogMeta
import com.ember.reader.core.model.GrimmoryCatalogShelf
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.opds.OpdsFeed
import com.ember.reader.core.opds.OpdsFeedEntry
import com.ember.reader.core.repository.CatalogPreferencesRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.ui.common.friendlyErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val opdsClient: OpdsClient,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val catalogPreferencesRepository: CatalogPreferencesRepository
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val path: String = savedStateHandle.get<String>("path") ?: ""

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _seriesSort = MutableStateFlow(SeriesSortOption.NAME)
    val seriesSort: StateFlow<SeriesSortOption> = _seriesSort.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    val isSeriesView: Boolean get() = path == "grimmory:series"
    val isGrimmoryCatalogRoot: Boolean get() = path.isEmpty() && server?.isGrimmory == true

    private var server: Server? = null

    /** Raw entries from the API before preferences are applied. */
    private val _rawGrimmoryEntries = MutableStateFlow<List<GrimmoryCatalogEntry>>(emptyList())

    init {
        viewModelScope.launch { loadCatalog() }

        // Combine raw entries with preferences for the Grimmory catalog root
        combine(
            _rawGrimmoryEntries,
            catalogPreferencesRepository.observePreferences(serverId),
            _editMode
        ) { entries, prefs, editing ->
            if (entries.isEmpty()) return@combine null
            val prefsMap = prefs.associateBy { it.entryId }
            val filtered = if (editing) {
                // In edit mode, show all entries (including hidden) so user can unhide
                entries.sortedBy { prefsMap[it.id]?.sortOrder ?: defaultOrderFor(it) }
            } else {
                entries
                    .filter { !(prefsMap[it.id]?.hidden ?: false) }
                    .sortedBy { prefsMap[it.id]?.sortOrder ?: defaultOrderFor(it) }
            }
            val serverName = server?.name ?: "Catalog"
            Triple(filtered, serverName, prefsMap.mapValues { it.value.hidden })
        }.onEach { result ->
            if (result != null) {
                val (filtered, serverName, hiddenMap) = result
                _uiState.value = CatalogUiState.GrimmorySuccess(
                    catalog = GrimmoryCatalog(serverName = serverName, entries = filtered),
                    hiddenEntryIds = hiddenMap.filter { it.value }.keys
                )
            }
        }.launchIn(viewModelScope)
    }

    fun toggleEditMode() {
        _editMode.value = !_editMode.value
    }

    fun hideEntry(entryId: String) {
        viewModelScope.launch {
            catalogPreferencesRepository.hideEntry(serverId, entryId)
        }
    }

    fun unhideEntry(entryId: String) {
        viewModelScope.launch {
            catalogPreferencesRepository.unhideEntry(serverId, entryId)
        }
    }

    fun moveEntryUp(entryId: String) {
        val current = (_uiState.value as? CatalogUiState.GrimmorySuccess)?.catalog?.entries ?: return
        val sectionEntries = entriesInSameSection(current, entryId)
        val index = sectionEntries.indexOfFirst { it.id == entryId }
        if (index <= 0) return
        val reordered = sectionEntries.toMutableList().apply {
            val item = removeAt(index)
            add(index - 1, item)
        }
        viewModelScope.launch {
            catalogPreferencesRepository.reorder(serverId, reordered.map { it.id })
        }
    }

    fun moveEntryDown(entryId: String) {
        val current = (_uiState.value as? CatalogUiState.GrimmorySuccess)?.catalog?.entries ?: return
        val sectionEntries = entriesInSameSection(current, entryId)
        val index = sectionEntries.indexOfFirst { it.id == entryId }
        if (index < 0 || index >= sectionEntries.lastIndex) return
        val reordered = sectionEntries.toMutableList().apply {
            val item = removeAt(index)
            add(index + 1, item)
        }
        viewModelScope.launch {
            catalogPreferencesRepository.reorder(serverId, reordered.map { it.id })
        }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            catalogPreferencesRepository.resetForServer(serverId)
        }
    }

    private fun entriesInSameSection(
        entries: List<GrimmoryCatalogEntry>,
        entryId: String
    ): List<GrimmoryCatalogEntry> {
        val target = entries.find { it.id == entryId } ?: return emptyList()
        val sectionType = sectionGroupFor(target.type)
        return entries.filter { sectionGroupFor(it.type) == sectionType }
    }

    private fun sectionGroupFor(type: CatalogEntryType): String = when (type) {
        CatalogEntryType.CONTINUE_READING, CatalogEntryType.RECENTLY_ADDED -> "quick"
        CatalogEntryType.LIBRARY -> "libraries"
        CatalogEntryType.SHELF -> "shelves"
        CatalogEntryType.MAGIC_SHELF -> "magic"
        CatalogEntryType.SERIES, CatalogEntryType.AUTHORS, CatalogEntryType.ALL_BOOKS -> "browse"
    }

    private fun defaultOrderFor(entry: GrimmoryCatalogEntry): Int = when (entry.type) {
        CatalogEntryType.CONTINUE_READING -> 0
        CatalogEntryType.RECENTLY_ADDED -> 1
        CatalogEntryType.LIBRARY -> 100
        CatalogEntryType.SHELF -> 200
        CatalogEntryType.MAGIC_SHELF -> 300
        CatalogEntryType.SERIES -> 400
        CatalogEntryType.AUTHORS -> 401
        CatalogEntryType.ALL_BOOKS -> 402
    }

    private suspend fun loadCatalog() {
        val loadedServer = serverRepository.getById(serverId)
        if (loadedServer == null) {
            _uiState.value = CatalogUiState.Error("Server not found")
            return
        }
        server = loadedServer
        fetchFeed()
    }

    fun refresh() {
        _uiState.value = CatalogUiState.Loading
        viewModelScope.launch { fetchFeed() }
    }

    fun setSeriesSort(sort: SeriesSortOption) {
        if (_seriesSort.value == sort) return
        _seriesSort.value = sort
        val currentServer = server ?: return
        _uiState.value = CatalogUiState.Loading
        viewModelScope.launch { fetchGrimmorySeries(currentServer) }
    }

    private suspend fun fetchFeed() {
        val currentServer = server ?: return

        if (path.isNotEmpty()) {
            // Grimmory sub-navigation for series/authors lists
            if (path == "grimmory:series") {
                fetchGrimmorySeries(currentServer)
                return
            }
            if (path == "grimmory:authors") {
                fetchGrimmoryAuthors(currentServer)
                return
            }
            // Regular OPDS sub-navigation
            fetchOpdsFeed(currentServer)
            return
        }

        if (currentServer.isGrimmory) {
            // Re-login if token is missing (e.g., after keystore invalidation or app update)
            if (!grimmoryTokenManager.isLoggedIn(currentServer.id)) {
                serverRepository.tryGrimmoryRelogin(currentServer)
            }

            if (grimmoryTokenManager.isLoggedIn(currentServer.id)) {
                fetchGrimmoryCatalog(currentServer)
            } else if (currentServer.opdsUsername.isNotBlank()) {
                // Grimmory detected but not logged in — fall back to OPDS
                fetchOpdsFeed(currentServer)
            } else {
                _uiState.value = CatalogUiState.Error("Grimmory login required — edit server to add credentials")
            }
        } else {
            fetchOpdsFeed(currentServer)
        }
    }

    private suspend fun fetchGrimmoryCatalog(server: Server) {
        try {
            fetchGrimmoryCatalogInner(server)
        } catch (e: Exception) {
            _uiState.value = CatalogUiState.Error(friendlyErrorMessage(e))
        }
    }

    private suspend fun fetchGrimmoryCatalogInner(server: Server) {
        val entries = mutableListOf<GrimmoryCatalogEntry>()

        // Quick Access
        entries.add(
            GrimmoryCatalogMeta(
                id = "grimmory:continue-reading",
                title = "Continue Reading",
                subtitle = "Books you're currently reading",
                href = "grimmory:status=READING",
                type = CatalogEntryType.CONTINUE_READING
            )
        )
        entries.add(
            GrimmoryCatalogMeta(
                id = "grimmory:recent",
                title = "Recently Added",
                subtitle = "Latest additions to your library",
                // Must hit the dedicated /recently-added endpoint — it applies a 30-day window
                // that /books?sort=addedOn doesn't, so sort params alone can't reproduce it.
                href = "grimmory:recentlyAdded",
                type = CatalogEntryType.RECENTLY_ADDED
            )
        )

        // Libraries
        grimmoryAppClient.getLibraries(server.url, server.id).onSuccess { libraries ->
            for (lib in libraries) {
                entries.add(
                    GrimmoryCatalogLibrary(
                        libraryId = lib.id,
                        title = lib.name,
                        bookCount = lib.bookCount,
                        serverIcon = lib.icon
                    )
                )
            }
        }

        // Shelves
        grimmoryAppClient.getShelves(server.url, server.id).onSuccess { shelves ->
            for (shelf in shelves) {
                entries.add(
                    GrimmoryCatalogShelf(
                        shelfId = shelf.id,
                        title = shelf.name,
                        bookCount = shelf.bookCount,
                        publicShelf = shelf.publicShelf,
                        serverIcon = shelf.icon
                    )
                )
            }
        }

        // Magic Shelves
        grimmoryAppClient.getMagicShelves(server.url, server.id).onSuccess { magicShelves ->
            for (magicShelf in magicShelves) {
                entries.add(
                    GrimmoryCatalogMagicShelf(
                        magicShelfId = magicShelf.id,
                        title = magicShelf.name,
                        publicShelf = magicShelf.publicShelf,
                        serverIcon = magicShelf.icon,
                        iconType = magicShelf.iconType
                    )
                )
            }
        }

        // Browse
        entries.add(
            GrimmoryCatalogMeta(
                id = "grimmory:series",
                title = "Series",
                subtitle = "Browse books by series",
                href = "grimmory:series",
                type = CatalogEntryType.SERIES
            )
        )
        entries.add(
            GrimmoryCatalogMeta(
                id = "grimmory:authors",
                title = "Authors",
                subtitle = "Browse books by author",
                href = "grimmory:authors",
                type = CatalogEntryType.AUTHORS
            )
        )
        entries.add(
            GrimmoryCatalogMeta(
                id = "grimmory:all",
                title = "All Books",
                subtitle = "Browse the full catalog",
                href = "grimmory:all",
                type = CatalogEntryType.ALL_BOOKS
            )
        )

        // Emit raw entries — the combine flow applies preferences and updates uiState
        _rawGrimmoryEntries.value = entries
    }

    private suspend fun fetchGrimmorySeries(server: Server) {
        try {
            val sort = _seriesSort.value
            val allEntries = mutableListOf<OpdsFeedEntry>()
            var page = 0
            do {
                val result = grimmoryAppClient.getSeries(server.url, server.id, page = page, size = 100, sort = sort.key, dir = sort.dir).getOrThrow()
                result.content.forEach { series ->
                    val subtitle = buildString {
                        append("${series.bookCount} books")
                        if (series.booksRead > 0) append(" · ${series.booksRead} read")
                        if (series.authors.isNotEmpty()) append(" · ${series.authors.first()}")
                    }
                    allEntries.add(
                        OpdsFeedEntry(
                            id = "grimmory:series:${series.seriesName}",
                            title = series.seriesName,
                            href = "grimmory:seriesName=${java.net.URLEncoder.encode(series.seriesName, "UTF-8")}",
                            content = subtitle
                        )
                    )
                }
                page++
            } while (result.hasNext)

            _uiState.value = CatalogUiState.OpdsSuccess(
                feed = OpdsFeed(title = "Series", entries = allEntries)
            )
        } catch (e: Exception) {
            _uiState.value = CatalogUiState.Error(friendlyErrorMessage(e))
        }
    }

    private suspend fun fetchGrimmoryAuthors(server: Server) {
        try {
            val allEntries = mutableListOf<OpdsFeedEntry>()
            var page = 0
            do {
                val result = grimmoryAppClient.getAuthors(server.url, server.id, page = page, size = 100).getOrThrow()
                result.content.forEach { author ->
                    allEntries.add(
                        OpdsFeedEntry(
                            id = "grimmory:author:${author.id}",
                            title = author.name,
                            href = "grimmory:search=${java.net.URLEncoder.encode(author.name, "UTF-8")}",
                            content = "${author.bookCount} books"
                        )
                    )
                }
                page++
            } while (result.hasNext)

            _uiState.value = CatalogUiState.OpdsSuccess(
                feed = OpdsFeed(title = "Authors", entries = allEntries)
            )
        } catch (e: Exception) {
            _uiState.value = CatalogUiState.Error(friendlyErrorMessage(e))
        }
    }

    private suspend fun fetchOpdsFeed(server: Server) {
        val result = opdsClient.fetchCatalog(
            baseUrl = server.opdsUrl,
            username = server.opdsUsername,
            password = server.opdsPassword,
            path = path.ifEmpty { null }
        )
        result.fold(
            onSuccess = { feed ->
                _uiState.value = CatalogUiState.OpdsSuccess(feed = feed)
            },
            onFailure = { error ->
                _uiState.value = CatalogUiState.Error(friendlyErrorMessage(error))
            }
        )
    }
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class OpdsSuccess(val feed: OpdsFeed) : CatalogUiState
    data class GrimmorySuccess(
        val catalog: GrimmoryCatalog,
        val hiddenEntryIds: Set<String> = emptySet()
    ) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

enum class SeriesSortOption(val key: String, val dir: String, val label: String) {
    NAME("name", "asc", "A-Z"),
    BOOK_COUNT("bookcount", "desc", "Most Books"),
    READ_PROGRESS("readprogress", "desc", "Most Read"),
    RECENTLY_ADDED("recentlyAdded", "desc", "Recent")
}
