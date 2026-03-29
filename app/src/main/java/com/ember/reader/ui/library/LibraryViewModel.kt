package com.ember.reader.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.ui.download.DownloadService
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
    private val grimmoryTokenManager: GrimmoryTokenManager
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

    val downloadingBooks: StateFlow<Set<String>> = DownloadService.downloadingBookIds

    // When viewing a subcategory (series, shelf, etc.), only show books from that fetch
    private val isSubcategory = catalogPath.contains("?") || catalogPath.contains("recent") || catalogPath.contains("surprise")
    private val _fetchedBookIds = MutableStateFlow<Set<String>?>(null)

    private var server: Server? = null

    private val _coverAuthHeader = MutableStateFlow<String?>(null)
    val coverAuthHeader: StateFlow<String?> = _coverAuthHeader.asStateFlow()

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
        combine(_sortOrder, _fetchedBookIds) { sort, ids -> sort to ids },
        combine(_formatFilter, _downloadedOnly) { format, downloaded -> format to downloaded }
    ) { books, downloading, query, (sort, fetchedIds), (formatFilter, downloadedOnly) ->
        timber.log.Timber.d("LibraryVM combine: totalBooks=${books.size} fetchedIds=${fetchedIds?.size} query='$query'")
        val filtered = books
            .filter { book ->
                // In subcategory view, only show books from the current fetch
                (fetchedIds == null || book.id in fetchedIds) &&
                    (
                        query.isBlank() || book.title.contains(query, ignoreCase = true) ||
                            book.author?.contains(query, ignoreCase = true) == true
                        ) &&
                    (formatFilter == null || book.format == formatFilter) &&
                    (!downloadedOnly || book.isDownloaded)
            }
            .sortedWith(sort.comparator)

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
            server?.let { s ->
                // Grimmory media covers use ?token= query param, not Authorization header
                // For OPDS covers, use Basic Auth header as before
                _coverAuthHeader.value = if (s.isGrimmory && grimmoryTokenManager.isLoggedIn(s.id)) {
                    // Special marker: "jwt:<token>" — the UI will append ?token= to the URL
                    grimmoryTokenManager.getAccessToken(s.id)?.let { "jwt:$it" }
                        ?: com.ember.reader.core.network.basicAuthHeader(s.opdsUsername, s.opdsPassword)
                } else {
                    com.ember.reader.core.network.basicAuthHeader(s.opdsUsername, s.opdsPassword)
                }
            }
            refresh()
        }
    }

    fun refresh() {
        val currentServer = server ?: return
        if (catalogPath.isEmpty()) return
        _fetchedBookIds.value = null // Reset for fresh load
        _nextPagePath.value = null
        _isRefreshing.value = true
        viewModelScope.launch {
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
                _nextPagePath.value = page.nextPagePath
            }
            _loadingMore.value = false
        }
    }

    private suspend fun refreshFromGrimmoryPage(
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
        return bookRepository.refreshFromGrimmory(
            server = server,
            page = page,
            libraryId = params["libraryId"]?.toLongOrNull(),
            shelfId = params["shelfId"]?.toLongOrNull(),
            seriesName = params["seriesName"],
            status = params["status"],
            search = params["search"]
        )
    }

    private suspend fun refreshFromGrimmory(
        server: com.ember.reader.core.model.Server
    ): Result<com.ember.reader.core.opds.OpdsBookPage> {
        val paramString = catalogPath.removePrefix("grimmory:")
        val params = paramString.split("&")
            .filter { "=" in it }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key to java.net.URLDecoder.decode(value, "UTF-8")
            }
        timber.log.Timber.d("GrimmoryRefresh: catalogPath='$catalogPath' paramString='$paramString' params=$params")
        return bookRepository.refreshFromGrimmory(
            server = server,
            libraryId = params["libraryId"]?.toLongOrNull(),
            shelfId = params["shelfId"]?.toLongOrNull(),
            seriesName = params["seriesName"],
            status = params["status"],
            search = params["search"]
        )
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
