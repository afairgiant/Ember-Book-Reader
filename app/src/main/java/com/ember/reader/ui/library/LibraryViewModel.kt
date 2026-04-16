package com.ember.reader.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ember.reader.core.database.query.LibrarySortOrder
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryFilter
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.SyncStatus
import com.ember.reader.core.sync.SyncStatusProber
import com.ember.reader.core.sync.SyncStatusRepository
import com.ember.reader.ui.download.DownloadService
import com.ember.reader.ui.organize.OrganizeFilesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val syncStatusProber: SyncStatusProber,
    val organizeFilesViewModelFactory: OrganizeFilesViewModel.Factory
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val catalogPath: String = savedStateHandle.get<String>("path") ?: ""

    /**
     * Parsed Grimmory catalog params, or `null` when [catalogPath] isn't a Grimmory path. Empty
     * map for unscoped roots (`grimmory:` or `grimmory:all`). This is the single source of truth
     * for "is this a scoped Grimmory view" — both [isSubcategory] and [isUnfilteredRootView] read
     * it, so they can't drift apart when new filter keys are added to [GRIMMORY_FILTER_KEYS].
     */
    private val grimmoryParams: Map<String, String>? = parseGrimmoryParamsOrNull(catalogPath)

    private val isGrimmoryScopedPath: Boolean =
        grimmoryParams?.keys?.any { it in GRIMMORY_FILTER_KEYS } == true

    private val isSubcategory: Boolean = catalogPath.contains("?") ||
        catalogPath.contains("recent") ||
        catalogPath.contains("surprise") ||
        isGrimmoryScopedPath

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(defaultSortOrderFor(catalogPath, grimmoryParams))
    val sortOrder: StateFlow<LibrarySortOrder> = _sortOrder.asStateFlow()

    private val _formatFilter = MutableStateFlow<BookFormat?>(null)
    val formatFilter: StateFlow<BookFormat?> = _formatFilter.asStateFlow()

    private val _downloadedOnly = MutableStateFlow(false)
    val downloadedOnly: StateFlow<Boolean> = _downloadedOnly.asStateFlow()

    private val _grimmoryFilter = MutableStateFlow(GrimmoryFilter())
    val grimmoryFilter: StateFlow<GrimmoryFilter> = _grimmoryFilter.asStateFlow()

    private val _grimmoryFilterOptions =
        MutableStateFlow<com.ember.reader.core.grimmory.GrimmoryAppFilterOptions?>(null)
    val grimmoryFilterOptions: StateFlow<com.ember.reader.core.grimmory.GrimmoryAppFilterOptions?> =
        _grimmoryFilterOptions.asStateFlow()

    val downloadingBookIds: StateFlow<Set<String>> = DownloadService.downloadingBookIds

    private var server: Server? = null

    private val _currentServer = MutableStateFlow<Server?>(null)

    /** Reactive snapshot of the current [Server], exposing [Server.canMoveOrganizeFiles]. */
    val currentServer: StateFlow<Server?> = _currentServer.asStateFlow()

    /** Live sync health for the current server — drives the [SyncStatusBanner]. */
    val syncStatus: StateFlow<SyncStatus> = syncStatusRepository
        .observe(serverId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Unknown)

    private val _selectedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBookIds: StateFlow<Set<String>> = _selectedBookIds.asStateFlow()

    /** True when the user is in multi-select mode (at least one book is selected). */
    val isSelecting: StateFlow<Boolean> = _selectedBookIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Allowlist of book IDs fetched by the mediator for the current view. Non-null for
     * subcategory views (series / shelf / etc.), null for unscoped catalog roots — the DAO's
     * paged query reads this flow and gates rows to the allowlist when non-null.
     */
    private val _sessionIds = MutableStateFlow<Set<String>?>(
        if (isSubcategory) emptySet() else null
    )

    private val _refreshTicker = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val bookPagingData: Flow<PagingData<Book>> = combine(
        _sortOrder,
        _formatFilter,
        _downloadedOnly,
        _searchQuery.debounce(300),
        _grimmoryFilter,
        _refreshTicker
    ) { values ->
        LibraryInputs(
            sort = values[0] as LibrarySortOrder,
            formatFilter = values[1] as BookFormat?,
            downloadedOnly = values[2] as Boolean,
            query = values[3] as String,
            grimmoryFilter = values[4] as GrimmoryFilter
        )
    }
        .flatMapLatest { inputs ->
            val currentServer = server
                ?: return@flatMapLatest flowOf(PagingData.empty())
            _sessionIds.value = if (isSubcategory) emptySet() else null
            bookRepository.pageByServer(
                server = currentServer,
                catalogPath = catalogPath,
                sort = inputs.sort,
                formatFilter = inputs.formatFilter,
                downloadedOnly = inputs.downloadedOnly,
                query = inputs.query,
                grimmoryFilter = inputs.grimmoryFilter,
                sessionIds = _sessionIds
            )
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            server = serverRepository.getById(serverId)
            _currentServer.value = server
            // Init-time reconcile runs in the background without flashing the pull-to-refresh
            // indicator — the user didn't ask for a refresh.
            syncCatalog(userInitiated = false)
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
     * Resolves the current selection into Grimmory backend IDs. Books with no
     * grimmoryBookId (local-only, OPDS, etc.) are filtered out — they can't be moved.
     * Called from [LibraryScreen] when the Organize Files sheet opens; hits the DAO directly
     * because paging only keeps a window of loaded books in memory.
     */
    suspend fun resolveSelectedGrimmoryIds(): List<Long> {
        val ids = _selectedBookIds.value
        if (ids.isEmpty()) return emptyList()
        return bookRepository.getByIds(ids).mapNotNull { it.grimmoryBookId }
    }

    private suspend fun loadGrimmoryFilterOptions() {
        val currentServer = server ?: return
        val params = grimmoryParams ?: return
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
    private fun isUnfilteredRootView(): Boolean = when {
        grimmoryParams != null -> !isGrimmoryScopedPath
        else -> "?" !in catalogPath && catalogPath.isNotEmpty()
    }

    /**
     * Invalidates the Paging flow and, for unfiltered root views with no active filters, runs the
     * catalog reconcile so deleted server books are pruned from the local DB. Drives the
     * PullToRefreshBox spinner — only call this from user-initiated refresh gestures.
     */
    fun refresh() {
        viewModelScope.launch { syncCatalog(userInitiated = true) }
    }

    private suspend fun syncCatalog(userInitiated: Boolean) {
        val currentServer = server ?: return
        if (catalogPath.isEmpty()) return
        if (userInitiated) _isRefreshing.value = true
        try {
            val grimmoryFilterActive = _grimmoryFilter.value.hasRestrictiveFilters
            if (!isSubcategory && isUnfilteredRootView() && !grimmoryFilterActive) {
                if (catalogPath == "grimmory:all" || catalogPath == "grimmory:") {
                    bookRepository.reconcileGrimmoryLibrary(currentServer)
                        .onFailure {
                            timber.log.Timber.w(it, "LibraryVM: grimmory reconcile failed")
                        }
                } else if (!catalogPath.startsWith("grimmory:")) {
                    bookRepository.reconcileOpdsLibrary(currentServer, rootPath = catalogPath)
                        .onFailure {
                            timber.log.Timber.w(it, "LibraryVM: opds reconcile failed")
                        }
                }
            }
            _refreshTicker.update { it + 1 }
        } finally {
            if (userInitiated) _isRefreshing.value = false
        }
    }

    fun updateGrimmoryFilter(filter: GrimmoryFilter) {
        if (_grimmoryFilter.value == filter) return
        _grimmoryFilter.value = filter
    }

    fun resetGrimmoryFilter() {
        if (_grimmoryFilter.value == GrimmoryFilter()) return
        _grimmoryFilter.value = GrimmoryFilter()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        _viewMode.update { if (it == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }
    }

    fun updateSortOrder(order: LibrarySortOrder) {
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

    /**
     * Re-probes the current server so a transient [SyncStatus.NetworkError] can
     * clear itself. The probe writes through [SyncStatusRepository], which the
     * [syncStatus] StateFlow already observes — no explicit state reset needed.
     */
    fun retrySync() {
        val target = server ?: return
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            syncStatusProber.probe(target)
        }
    }

    private var retryJob: kotlinx.coroutines.Job? = null

    private data class LibraryInputs(
        val sort: LibrarySortOrder,
        val formatFilter: BookFormat?,
        val downloadedOnly: Boolean,
        val query: String,
        val grimmoryFilter: GrimmoryFilter
    )

    companion object {
        /**
         * Grimmory catalog-path keys that turn a catalog view into a scoped subcategory (shelf,
         * library, series, etc.). Kept in one place so [isSubcategory] and [isUnfilteredRootView]
         * can never disagree about what "scoped" means — adding a new scoped view means adding a
         * key here and nowhere else.
         *
         * `recentlyAdded` is flag-shaped (`grimmory:recentlyAdded` with no `=value`) but lives
         * here anyway so the Recently Added view gets sessionIds gating like shelves do.
         */
        private val GRIMMORY_FILTER_KEYS = setOf(
            "libraryId", "shelfId", "magicShelfId", "seriesName", "status", "search", "recentlyAdded"
        )

        private fun parseGrimmoryParamsOrNull(catalogPath: String): Map<String, String>? {
            if (!catalogPath.startsWith("grimmory:")) return null
            val paramString = catalogPath.removePrefix("grimmory:")
            if (paramString.isEmpty() || paramString == "all") return emptyMap()
            return paramString.split("&")
                .filter { it.isNotEmpty() }
                .associate { segment ->
                    if ("=" in segment) {
                        val (key, value) = segment.split("=", limit = 2)
                        key to java.net.URLDecoder.decode(value, "UTF-8")
                    } else {
                        // Flag-style params (e.g. `grimmory:recentlyAdded`) map to an empty value
                        // so callers can still check for key presence.
                        segment to ""
                    }
                }
        }

        /**
         * Picks the initial local sort order for the library view. Most paths default to TITLE,
         * but series views sort by series/index and the Recently Added view sorts by addedAt DESC
         * so the local ordering mirrors Grimmory's `/recently-added` response instead of getting
         * re-alphabetized.
         */
        private fun defaultSortOrderFor(
            catalogPath: String,
            grimmoryParams: Map<String, String>?
        ): LibrarySortOrder = when {
            catalogPath.contains("seriesName") -> LibrarySortOrder.SERIES
            grimmoryParams?.containsKey("recentlyAdded") == true -> LibrarySortOrder.RECENT
            else -> LibrarySortOrder.TITLE
        }
    }
}

enum class ViewMode { GRID, LIST }
