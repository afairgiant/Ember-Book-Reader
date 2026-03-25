package com.ember.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val catalogPath: String = savedStateHandle.get<String>("path") ?: "/api/v1/opds/catalog"

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

    private var server: Server? = null

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.observeByServer(serverId),
        _downloadingBooks,
        _searchQuery,
        _sortOrder,
        combine(_formatFilter, _downloadedOnly) { format, downloaded -> format to downloaded },
    ) { books, downloading, query, sort, (formatFilter, downloadedOnly) ->
        val filtered = books
            .filter { book ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState.Loading)

    init {
        viewModelScope.launch {
            server = serverRepository.getById(serverId)
            refresh()
        }
    }

    fun refresh() {
        val currentServer = server ?: return
        _isRefreshing.value = true
        viewModelScope.launch {
            bookRepository.refreshFromServer(currentServer, path = catalogPath)
            _isRefreshing.value = false
        }
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
            bookRepository.downloadBook(book, currentServer)
            _downloadingBooks.update { it - book.id }
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
