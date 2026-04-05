package com.ember.reader.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.opds.OpdsFeed
import com.ember.reader.core.opds.OpdsFeedEntry
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.ui.common.friendlyErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val opdsClient: OpdsClient,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryTokenManager: GrimmoryTokenManager
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val path: String = savedStateHandle.get<String>("path") ?: ""

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _seriesSort = MutableStateFlow(SeriesSortOption.NAME)
    val seriesSort: StateFlow<SeriesSortOption> = _seriesSort.asStateFlow()

    val isSeriesView: Boolean get() = path == "grimmory:series"

    private var server: Server? = null

    init {
        viewModelScope.launch { loadCatalog() }
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
        val entries = mutableListOf<OpdsFeedEntry>()

        // Continue Reading
        entries.add(
            OpdsFeedEntry(
                id = "grimmory:continue-reading",
                title = "Continue Reading",
                href = "grimmory:status=READING",
                content = "Books you're currently reading"
            )
        )

        // Recently Added
        entries.add(
            OpdsFeedEntry(
                id = "grimmory:recent",
                title = "Recently Added",
                href = "grimmory:sort=addedOn&dir=desc",
                content = "Latest additions to your library"
            )
        )

        // Libraries
        grimmoryAppClient.getLibraries(server.url, server.id).onSuccess { libraries ->
            for (lib in libraries) {
                entries.add(
                    OpdsFeedEntry(
                        id = "grimmory:library:${lib.id}",
                        title = lib.name,
                        href = "grimmory:libraryId=${lib.id}",
                        content = "${lib.bookCount} books"
                    )
                )
            }
        }

        // Shelves
        grimmoryAppClient.getShelves(server.url, server.id).onSuccess { shelves ->
            if (shelves.isNotEmpty()) {
                for (shelf in shelves) {
                    entries.add(
                        OpdsFeedEntry(
                            id = "grimmory:shelf:${shelf.id}",
                            title = shelf.name,
                            href = "grimmory:shelfId=${shelf.id}",
                            content = "${shelf.bookCount} books"
                        )
                    )
                }
            }
        }

        // Series
        entries.add(
            OpdsFeedEntry(
                id = "grimmory:series",
                title = "Series",
                href = "grimmory:series",
                content = "Browse books by series"
            )
        )

        // Authors
        entries.add(
            OpdsFeedEntry(
                id = "grimmory:authors",
                title = "Authors",
                href = "grimmory:authors",
                content = "Browse books by author"
            )
        )

        // All Books
        entries.add(
            OpdsFeedEntry(
                id = "grimmory:all",
                title = "All Books",
                href = "grimmory:all",
                content = "Browse the full catalog"
            )
        )

        _uiState.value = CatalogUiState.Success(
            feed = OpdsFeed(
                title = "${server.name} Catalog",
                entries = entries
            )
        )
    }

    private suspend fun fetchGrimmorySeries(server: Server) {
        try {
            val allEntries = mutableListOf<OpdsFeedEntry>()
            var page = 0
            do {
                val sort = _seriesSort.value
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

            _uiState.value = CatalogUiState.Success(
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

            _uiState.value = CatalogUiState.Success(
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
                _uiState.value = CatalogUiState.Success(feed = feed)
            },
            onFailure = { error ->
                _uiState.value = CatalogUiState.Error(friendlyErrorMessage(error))
            }
        )
    }
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val feed: OpdsFeed) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

enum class SeriesSortOption(val key: String, val dir: String, val label: String) {
    NAME("name", "asc", "A-Z"),
    BOOK_COUNT("bookcount", "desc", "Most Books"),
    READ_PROGRESS("readprogress", "desc", "Most Read"),
    RECENTLY_ADDED("recentlyAdded", "desc", "Recent"),
}
