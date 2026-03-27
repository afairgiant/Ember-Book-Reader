package com.ember.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import android.content.Context
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.ui.common.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val catalogPath: String = savedStateHandle.get<String>("path") ?: ""

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _formatFilter = MutableStateFlow<BookFormat?>(null)
    val formatFilter: StateFlow<BookFormat?> = _formatFilter.asStateFlow()

    private val _downloadedOnly = MutableStateFlow(false)
    val downloadedOnly: StateFlow<Boolean> = _downloadedOnly.asStateFlow()

    private val _downloadingBooks = MutableStateFlow<Set<String>>(emptySet())

    // When viewing a subcategory (series, shelf, etc.), only show books from that fetch
    private val isSubcategory = catalogPath.contains("?") || catalogPath.contains("recent") || catalogPath.contains("surprise")
    private val _fetchedBookIds = MutableStateFlow<Set<String>?>(null)

    private var server: Server? = null

    private val _coverAuthHeader = MutableStateFlow<String?>(null)
    val coverAuthHeader: StateFlow<String?> = _coverAuthHeader.asStateFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.observeByServer(serverId),
        _downloadingBooks,
        _searchQuery,
        combine(_sortOrder, _fetchedBookIds) { sort, ids -> sort to ids },
        combine(_formatFilter, _downloadedOnly) { format, downloaded -> format to downloaded },
    ) { books, downloading, query, (sort, fetchedIds), (formatFilter, downloadedOnly) ->
        val filtered = books
            .filter { book ->
                // In subcategory view, only show books from the current fetch
                (fetchedIds == null || book.id in fetchedIds) &&
                    (query.isBlank() || book.title.contains(query, ignoreCase = true) ||
                        book.author?.contains(query, ignoreCase = true) == true) &&
                    (formatFilter == null || book.format == formatFilter) &&
                    (!downloadedOnly || book.isDownloaded)
            }
            .sortedWith(sort.comparator)

        LibraryUiState.Success(
            books = filtered,
            downloadingBookIds = downloading,
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
        _isRefreshing.value = true
        viewModelScope.launch {
            val result = if (catalogPath.startsWith("grimmory:")) {
                refreshFromGrimmory(currentServer)
            } else {
                bookRepository.refreshFromServer(currentServer, path = catalogPath)
            }
            if (isSubcategory || catalogPath.startsWith("grimmory:")) {
                result.onSuccess { page ->
                    _fetchedBookIds.value = page.resolvedBookIds.toSet()
                }
            }
            _isRefreshing.value = false
        }
    }

    private suspend fun refreshFromGrimmory(server: com.ember.reader.core.model.Server): Result<com.ember.reader.core.opds.OpdsBookPage> {
        val paramString = catalogPath.removePrefix("grimmory:")
        val params = paramString.split("&")
            .filter { "=" in it }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key to value
            }
        return bookRepository.refreshFromGrimmory(
            server = server,
            libraryId = params["libraryId"]?.toLongOrNull(),
            shelfId = params["shelfId"]?.toLongOrNull(),
            seriesName = params["seriesName"],
            status = params["status"],
            search = params["search"],
        )
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun toggleViewMode() { _viewMode.update { if (it == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID } }
    fun updateSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun updateFormatFilter(format: BookFormat?) { _formatFilter.value = format }
    fun toggleDownloadedOnly() { _downloadedOnly.update { !it } }

    fun downloadBook(book: Book) {
        val currentServer = server ?: return
        _downloadingBooks.update { it + book.id }
        viewModelScope.launch {
            bookRepository.downloadBook(book, currentServer).onSuccess { downloadedBook ->
                pullProgressAfterDownload(downloadedBook, currentServer)
                NotificationHelper.showDownloadComplete(context, book.title, book.id)
            }
            _downloadingBooks.update { it - book.id }
        }
    }

    private suspend fun pullProgressAfterDownload(book: Book, server: Server) {
        // Try Grimmory API
        val grimmoryBookId = book.grimmoryBookId
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            runCatching {
                val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
                val rawPct = detail.readProgress
                if (rawPct != null && rawPct > 0f) {
                    val pct = if (rawPct > 1f) rawPct / 100f else rawPct
                    readingProgressRepository.applyRemoteProgress(
                        com.ember.reader.core.model.ReadingProgress(
                            bookId = book.id,
                            serverId = server.id,
                            percentage = pct,
                            lastReadAt = java.time.Instant.now(),
                            syncedAt = java.time.Instant.now(),
                            needsSync = false,
                        ),
                    )
                    return
                }
            }
        }

        // Fall back to kosync
        val fileHash = book.fileHash
        if (fileHash != null && server.kosyncUsername.isNotBlank()) {
            val remote = readingProgressRepository.pullProgress(server, book.id, fileHash).getOrNull()
            if (remote != null) {
                readingProgressRepository.applyRemoteProgress(remote.progress)
            }
        }
    }
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val books: List<Book>,
        val downloadingBookIds: Set<String> = emptySet(),
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

enum class ViewMode { GRID, LIST }

enum class SortOrder(val displayName: String, val comparator: Comparator<Book>) {
    TITLE("Title", compareBy { it.title.lowercase() }),
    AUTHOR("Author", compareBy { it.author?.lowercase() ?: "" }),
    RECENT("Recently Added", compareByDescending { it.addedAt }),
    SERIES("Series", compareBy<Book> { it.series?.lowercase() ?: "" }.thenBy { it.seriesIndex ?: 0f }),
}
