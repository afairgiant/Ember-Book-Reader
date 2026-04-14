package com.ember.reader.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.SyncStatus
import com.ember.reader.core.sync.SyncStatusRepository
import com.ember.reader.ui.download.DownloadService
import com.ember.reader.ui.organize.OrganizeFilesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val syncStatusRepository: SyncStatusRepository,
    val organizeFilesViewModelFactory: OrganizeFilesViewModel.Factory
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val catalogPath: String = savedStateHandle.get<String>("path") ?: ""

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        if (catalogPath.contains("seriesName")) SortOrder.SERIES else SortOrder.TITLE
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _formatFilter = MutableStateFlow<BookFormat?>(null)
    val formatFilter: StateFlow<BookFormat?> = _formatFilter.asStateFlow()

    private val _downloadedOnly = MutableStateFlow(false)
    val downloadedOnly: StateFlow<Boolean> = _downloadedOnly.asStateFlow()

    // Server-side sort/filter for Grimmory library views. Resets per navigation because the VM
    // is scoped to the nav backstack entry. Only consulted for grimmory: catalog paths.
    private val _grimmoryFilter = MutableStateFlow(GrimmoryFilter())
    val grimmoryFilter: StateFlow<GrimmoryFilter> = _grimmoryFilter.asStateFlow()

    // Available filter options (authors, languages, statuses, file types) for the current
    // library/shelf, fetched once on init from Grimmory's /app/filter-options endpoint. Null
    // until loaded; falls back to free-text input in the sheet if fetch fails.
    private val _grimmoryFilterOptions =
        MutableStateFlow<com.ember.reader.core.grimmory.GrimmoryAppFilterOptions?>(null)
    val grimmoryFilterOptions: StateFlow<com.ember.reader.core.grimmory.GrimmoryAppFilterOptions?> =
        _grimmoryFilterOptions.asStateFlow()

    val downloadingBooks: StateFlow<Set<String>> = DownloadService.downloadingBookIds

    // When viewing a subcategory (series, shelf, etc.), only show books from that fetch
    private val isSubcategory = catalogPath.contains("?") || catalogPath.contains("recent") || catalogPath.contains("surprise")
    private val _fetchedBookIds = MutableStateFlow<Set<String>?>(null)

    // Ordered list of book IDs in the order the server returned them. Used for Grimmory
    // paths where we want to preserve server sort (e.g. seriesName + seriesNumber) instead of
    // re-sorting client-side with a local comparator. Kept as a Map<String, Int> for O(1) lookup.
    private val _fetchedBookOrder = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val isGrimmoryPath = catalogPath.startsWith("grimmory:")

    private var server: Server? = null

    private val _currentServer = MutableStateFlow<Server?>(null)

    /** Reactive snapshot of the current [Server], exposing [Server.canMoveOrganizeFiles]. */
    val currentServer: StateFlow<Server?> = _currentServer.asStateFlow()

    /** Live sync health for the current server — drives the [SyncStatusBanner]. */
    val syncStatus: StateFlow<SyncStatus> = syncStatusRepository
        .observe(serverId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Unknown)

    // Multi-select state — used by the library screen's long-press selection mode
    // to drive bulk actions like Organize Files.
    private val _selectedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBookIds: StateFlow<Set<String>> = _selectedBookIds.asStateFlow()

    /** True when the user is in multi-select mode (at least one book is selected). */
    val isSelecting: StateFlow<Boolean> = _selectedBookIds
        .let { flow ->
            MutableStateFlow(false).also { out ->
                viewModelScope.launch {
                    flow.collect { out.value = it.isNotEmpty() }
                }
            }
        }

    private val _nextPagePath = MutableStateFlow<String?>(null)
    val hasMore: StateFlow<Boolean> = _nextPagePath.asStateFlow()
        .let { flow ->
            kotlinx.coroutines.flow.MutableStateFlow(false).also { hasMoreFlow ->
                viewModelScope.launch {
                    _nextPagePath.collect { hasMoreFlow.value = it != null }
                }
            }
        }

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.observeByServer(serverId),
        downloadingBooks,
        _searchQuery,
        combine(_sortOrder, _fetchedBookIds, _fetchedBookOrder) { sort, ids, order -> Triple(sort, ids, order) },
        combine(_formatFilter, _downloadedOnly) { format, downloaded -> format to downloaded }
    ) { books, downloading, query, (sort, fetchedIds, order), (formatFilter, downloadedOnly) ->
        timber.log.Timber.d("LibraryVM combine: totalBooks=${books.size} fetchedIds=${fetchedIds?.size} query='$query'")
        val filteredBooks = books.filter { book ->
            // In subcategory view, only show books from the current fetch
            (fetchedIds == null || book.id in fetchedIds) &&
                (
                    query.isBlank() || book.title.contains(query, ignoreCase = true) ||
                        book.author?.contains(query, ignoreCase = true) == true
                    ) &&
                (formatFilter == null || book.format == formatFilter) &&
                (!downloadedOnly || book.isDownloaded)
        }
        // For Grimmory paths, preserve the server's returned order (handles series sort
        // correctly: Grimmory orders by seriesName, and within a series we want the server's
        // intended order, not a client-side re-sort by title). Books not in the order map fall
        // through to the end and get the local comparator as a tiebreaker.
        val filtered = if (isGrimmoryPath && order.isNotEmpty()) {
            // When sorting by series, push books without a series to the end. Grimmory's SQL
            // returns NULL seriesName first by default, which the user finds confusing.
            val pushNullSeriesLast = _grimmoryFilter.value.sort == GrimmorySortKey.SERIES
            filteredBooks.sortedWith(
                compareBy<Book>(
                    { if (pushNullSeriesLast && it.series.isNullOrBlank()) 1 else 0 },
                    { order[it.id] ?: Int.MAX_VALUE },
                    { it.title.lowercase() }
                )
            )
        } else {
            filteredBooks.sortedWith(sort.comparator)
        }

        LibraryUiState.Success(
            books = filtered,
            downloadingBookIds = downloading
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState.Loading)

    init {
        viewModelScope.launch {
            server = serverRepository.getById(serverId)
            _currentServer.value = server
            refresh()
            loadGrimmoryFilterOptions()
        }
    }

    fun toggleSelection(bookId: String) {
        _selectedBookIds.update { current ->
            if (current.contains(bookId)) current - bookId else current + bookId
        }
    }

    fun enterSelectionWith(bookId: String) {
        _selectedBookIds.value = setOf(bookId)
    }

    fun clearSelection() {
        _selectedBookIds.value = emptySet()
    }

    /**
     * Fetches the set of available filter values (authors, languages, statuses, file types)
     * scoped to the current library/shelf. Called once on init — filter options rarely change
     * during a session and are only used to populate dropdowns in the filter sheet.
     */
    private suspend fun loadGrimmoryFilterOptions() {
        val currentServer = server ?: return
        if (!isGrimmoryPath) return
        val paramString = catalogPath.removePrefix("grimmory:")
        val params = paramString.split("&")
            .filter { "=" in it }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key to java.net.URLDecoder.decode(value, "UTF-8")
            }
        grimmoryAppClient.getFilterOptions(
            baseUrl = currentServer.url,
            serverId = currentServer.id,
            libraryId = params["libraryId"]?.toLongOrNull(),
            shelfId = params["shelfId"]?.toLongOrNull(),
            magicShelfId = params["magicShelfId"]?.toLongOrNull()
        ).onSuccess { options ->
            _grimmoryFilterOptions.value = options
            timber.log.Timber.d(
                "FilterOptions loaded: ${options.authors.size} authors, " +
                    "${options.languages.size} languages"
            )
        }.onFailure {
            timber.log.Timber.w(it, "LibraryVM: failed to load Grimmory filter options")
        }
    }

    /**
     * True when the current view is a full unfiltered catalog root (not a shelf, library, series,
     * search, or subcategory). Reconcile/prune only runs for these.
     */
    private fun isUnfilteredRootView(): Boolean {
        if (catalogPath.startsWith("grimmory:")) {
            val params = catalogPath.removePrefix("grimmory:")
            if (params.isEmpty() || params == "all") return true
            // grimmory:sort=... with no libraryId/shelfId/seriesName/status/search is still root
            val keys = params.split("&").mapNotNull {
                it.substringBefore("=", "").takeIf { k -> k.isNotEmpty() }
            }.toSet()
            val filterKeys =
                setOf("libraryId", "shelfId", "magicShelfId", "seriesName", "status", "search")
            return keys.none { it in filterKeys }
        }
        // OPDS: root path has no query string
        return "?" !in catalogPath && catalogPath.isNotEmpty()
    }

    fun refresh() {
        val currentServer = server ?: return
        if (catalogPath.isEmpty()) return
        _fetchedBookIds.value = null // Reset for fresh load
        _fetchedBookOrder.value = emptyMap()
        _nextPagePath.value = null
        _isRefreshing.value = true
        viewModelScope.launch {
            // Full catalog reconcile (prunes deleted books) only on unfiltered root views with
            // no active filters. Filtered/paginated/subcategory refreshes stay upsert-only so
            // browsing a shelf / series / filtered list doesn't wipe books outside its scope.
            val grimmoryFilterActive = _grimmoryFilter.value.hasRestrictiveFilters
            if (!isSubcategory && isUnfilteredRootView() && !grimmoryFilterActive) {
                if (catalogPath == "grimmory:all" || catalogPath == "grimmory:") {
                    bookRepository.reconcileGrimmoryLibrary(currentServer)
                        .onFailure { timber.log.Timber.w(it, "LibraryVM: grimmory reconcile failed, skipping prune") }
                } else if (!catalogPath.startsWith("grimmory:")) {
                    bookRepository.reconcileOpdsLibrary(currentServer, rootPath = catalogPath)
                        .onFailure { timber.log.Timber.w(it, "LibraryVM: opds reconcile failed, skipping prune") }
                }
            }

            val result = if (catalogPath.startsWith("grimmory:")) {
                refreshFromGrimmory(currentServer)
            } else {
                bookRepository.refreshFromServer(currentServer, path = catalogPath)
            }
            if (isSubcategory || catalogPath.startsWith("grimmory:")) {
                result.onSuccess { page ->
                    val newIds = page.resolvedBookIds.toSet()
                    val existing = _fetchedBookIds.value ?: emptySet()
                    val combined = existing + newIds
                    timber.log.Timber.d("LibraryVM: fetchedBookIds count=${combined.size} (was ${existing.size}, new ${newIds.size})")
                    _fetchedBookIds.value = combined
                    // Record server-returned order for Grimmory paths so uiState can preserve it.
                    _fetchedBookOrder.value = page.resolvedBookIds
                        .withIndex()
                        .associate { (idx, id) -> id to idx }
                    _nextPagePath.value = page.nextPagePath
                }
            } else {
                result.onSuccess { page ->
                    _nextPagePath.value = page.nextPagePath
                }
            }
            result.onFailure {
                timber.log.Timber.w(it, "LibraryVM: refresh failed")
            }
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        val nextPath = _nextPagePath.value ?: return
        val currentServer = server ?: return
        if (_loadingMore.value) return

        _loadingMore.value = true
        viewModelScope.launch {
            val result = if (nextPath.startsWith("grimmory:")) {
                val pageNum = nextPath.removePrefix("grimmory:page=").toIntOrNull() ?: 1
                refreshFromGrimmoryPage(currentServer, pageNum)
            } else {
                bookRepository.refreshFromServer(currentServer, path = nextPath)
            }
            result.onSuccess { page ->
                val newIds = page.resolvedBookIds.toSet()
                val existing = _fetchedBookIds.value ?: emptySet()
                _fetchedBookIds.value = existing + newIds
                // Append new IDs to the order map, keeping existing offsets stable.
                val existingOrder = _fetchedBookOrder.value
                val nextIndex = existingOrder.size
                val appended = existingOrder.toMutableMap().apply {
                    page.resolvedBookIds.forEachIndexed { idx, id ->
                        putIfAbsent(id, nextIndex + idx)
                    }
                }
                _fetchedBookOrder.value = appended
                _nextPagePath.value = page.nextPagePath
            }
            _loadingMore.value = false
        }
    }

    /**
     * Parses the path's query params and returns a lambda that kicks a Grimmory refresh. Both the
     * initial refresh and `loadMore` paginate through this so filter/sort settings apply
     * consistently across pages. Path-embedded filters (libraryId/shelfId/seriesName/status/
     * search) and the user's toolbar filters are merged — path-level `status` wins if both are
     * set.
     */
    private suspend fun refreshFromGrimmoryWithFilter(
        server: com.ember.reader.core.model.Server,
        page: Int
    ): Result<com.ember.reader.core.opds.OpdsBookPage> {
        val paramString = catalogPath.removePrefix("grimmory:")
        val params = paramString.split("&")
            .filter { "=" in it }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key to java.net.URLDecoder.decode(value, "UTF-8")
            }
        val filter = _grimmoryFilter.value
        timber.log.Timber.d(
            "GrimmoryRefresh: catalogPath='$catalogPath' params=$params filter=$filter page=$page"
        )
        return bookRepository.refreshFromGrimmory(
            server = server,
            page = page,
            libraryId = params["libraryId"]?.toLongOrNull(),
            shelfId = params["shelfId"]?.toLongOrNull(),
            magicShelfId = params["magicShelfId"]?.toLongOrNull(),
            seriesName = params["seriesName"],
            status = params["status"] ?: filter.status?.name,
            search = params["search"],
            sort = filter.sort.apiValue,
            dir = filter.direction.apiValue,
            minRating = filter.minRating,
            maxRating = filter.maxRating,
            authors = filter.authors,
            language = filter.language
        )
    }

    private suspend fun refreshFromGrimmoryPage(
        server: com.ember.reader.core.model.Server,
        page: Int
    ): Result<com.ember.reader.core.opds.OpdsBookPage> = refreshFromGrimmoryWithFilter(server, page)

    private suspend fun refreshFromGrimmory(
        server: com.ember.reader.core.model.Server
    ): Result<com.ember.reader.core.opds.OpdsBookPage> =
        refreshFromGrimmoryWithFilter(server, page = 0)

    /**
     * Updates the Grimmory sort/filter state and reloads. Called from the filter sheet.
     */
    fun updateGrimmoryFilter(filter: GrimmoryFilter) {
        if (_grimmoryFilter.value == filter) return
        _grimmoryFilter.value = filter
        refresh()
    }

    fun resetGrimmoryFilter() {
        if (_grimmoryFilter.value == GrimmoryFilter()) return
        _grimmoryFilter.value = GrimmoryFilter()
        refresh()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        // For Grimmory servers, trigger server-side search
        if (catalogPath.startsWith("grimmory:")) {
            searchServerDebounceJob?.cancel()
            if (query.isBlank()) {
                // Reload original catalog page
                refresh()
                return
            }
        }
        if (catalogPath.startsWith("grimmory:") && query.length >= 3) {
            searchServerDebounceJob?.cancel()
            searchServerDebounceJob = viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                val currentServer = server ?: return@launch
                _isRefreshing.value = true
                val result = bookRepository.refreshFromGrimmory(
                    server = currentServer,
                    search = query
                )
                result.onSuccess { page ->
                    _fetchedBookIds.value = page.resolvedBookIds.toSet()
                    _fetchedBookOrder.value = page.resolvedBookIds
                        .withIndex()
                        .associate { (idx, id) -> id to idx }
                }
                _isRefreshing.value = false
            }
        }
    }
    private var searchServerDebounceJob: kotlinx.coroutines.Job? = null
    fun toggleViewMode() {
        _viewMode.update { if (it == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }
    }
    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }
    fun updateFormatFilter(format: BookFormat?) {
        _formatFilter.value = format
    }
    fun toggleDownloadedOnly() {
        _downloadedOnly.update { !it }
    }

    fun downloadBook(book: Book) {
        val currentServer = server ?: return
        DownloadService.start(context, book.id, currentServer.id)
    }
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val books: List<Book>,
        val downloadingBookIds: Set<String> = emptySet()
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

enum class ViewMode { GRID, LIST }

enum class SortOrder(val displayName: String, val comparator: Comparator<Book>) {
    TITLE("Title", compareBy { it.title.lowercase() }),
    AUTHOR("Author", compareBy { it.author?.lowercase() ?: "" }),
    RECENT("Recently Added", compareByDescending { it.addedAt }),
    SERIES("Series", compareBy<Book> { it.series?.lowercase() ?: "" }.thenBy { it.seriesIndex ?: 0f })
}
