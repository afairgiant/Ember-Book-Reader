package com.ember.reader.ui.book

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryAppBook
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryBookDetail
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.core.hardcover.HardcoverBookDetail
import com.ember.reader.core.hardcover.HardcoverClient
import com.ember.reader.core.hardcover.HardcoverTokenManager
import com.ember.reader.core.hardcover.HardcoverUserBookEntry
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import timber.log.Timber
import com.ember.reader.ui.download.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val hardcoverClient: HardcoverClient,
    private val hardcoverTokenManager: HardcoverTokenManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _server = MutableStateFlow<Server?>(null)
    val server: StateFlow<Server?> = _server.asStateFlow()

    private val _readStatus = MutableStateFlow<ReadStatus?>(null)
    val readStatus: StateFlow<ReadStatus?> = _readStatus.asStateFlow()

    private val _grimmoryDetail = MutableStateFlow<GrimmoryBookDetail?>(null)
    val grimmoryDetail: StateFlow<GrimmoryBookDetail?> = _grimmoryDetail.asStateFlow()

    private val _hardcoverMatch = MutableStateFlow<HardcoverBookDetail?>(null)
    val hardcoverMatch: StateFlow<HardcoverBookDetail?> = _hardcoverMatch.asStateFlow()

    private val _hardcoverUserEntry = MutableStateFlow<HardcoverUserBookEntry?>(null)
    val hardcoverUserEntry: StateFlow<HardcoverUserBookEntry?> = _hardcoverUserEntry.asStateFlow()

    private val _seriesBooks = MutableStateFlow<List<SeriesBookItem>>(emptyList())
    val seriesBooks: StateFlow<List<SeriesBookItem>> = _seriesBooks.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() {
        _message.value = null
    }

    val progress: StateFlow<ReadingProgress?> = readingProgressRepository
        .observeByBookId(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            // Enrich metadata from file if downloaded but missing metadata
            val initial = bookRepository.getById(bookId)
            if (initial?.isDownloaded == true && initial.publisher == null && initial.pageCount == null) {
                bookRepository.enrichBookMetadata(bookId)
            }
        }
        viewModelScope.launch {
            bookRepository.observeById(bookId).collect { book ->
                _book.value = book
                book?.serverId?.let { loadServer(it) }
                book?.let { loadHardcoverData(it) }
            }
        }
        viewModelScope.launch {
            DownloadService.downloadingBookIds.collect { ids ->
                val bookVal = _book.value
                _downloading.value = bookVal != null && bookVal.id in ids
                // Refresh book state when download finishes
                if (bookVal != null && bookVal.id !in ids && !bookVal.isDownloaded) {
                    bookRepository.getById(bookVal.id)?.let { refreshed ->
                        if (refreshed.isDownloaded) _book.value = refreshed
                    }
                }
            }
        }
    }

    private suspend fun loadServer(serverId: Long) {
        val srv = serverRepository.getById(serverId) ?: return
        _server.value = srv

        // Fetch full detail from Grimmory if available
        val grimmoryBookId = _book.value?.grimmoryBookId
        if (srv.isGrimmory && grimmoryTokenManager.isLoggedIn(srv.id) && grimmoryBookId != null) {
            grimmoryClient.getBookDetail(srv.url, srv.id, grimmoryBookId).onSuccess { detail ->
                _readStatus.value = detail.readStatus
                _grimmoryDetail.value = detail
                // Load series books if book is in a series
                val seriesName = detail.seriesName ?: _book.value?.series
                if (seriesName != null) {
                    loadSeriesBooks(srv, seriesName)
                }
            }
        }
    }

    private suspend fun loadSeriesBooks(server: Server, seriesName: String) {
        grimmoryAppClient.getSeriesBooks(server.url, server.id, seriesName)
            .onSuccess { page ->
                val currentBookId = _book.value?.grimmoryBookId
                _seriesBooks.value = page.content
                    .filter { it.id != currentBookId } // exclude current book
                    .map { appBook ->
                        SeriesBookItem(
                            grimmoryBookId = appBook.id,
                            title = appBook.title,
                            author = appBook.authors.firstOrNull(),
                            coverUrl = grimmoryAppClient.coverUrl(server.url, appBook.id, appBook.coverUpdatedOn),
                            localBookId = null, // resolved below
                        )
                    }
                // Ensure local book entries exist (same as recently added)
                _seriesBooks.value = _seriesBooks.value.map { item ->
                    val opdsEntryId = "urn:booklore:book:${item.grimmoryBookId}"
                    val localId = bookRepository.ensureBookExists(
                        serverId = server.id,
                        opdsEntryId = opdsEntryId,
                        title = item.title,
                        author = item.author,
                        coverUrl = item.coverUrl,
                        format = com.ember.reader.core.model.BookFormat.EPUB,
                    )
                    item.copy(localBookId = localId)
                }
            }
            .onFailure { Timber.w(it, "Failed to load series books for $seriesName") }
    }

    private fun loadHardcoverData(book: Book) {
        if (!hardcoverTokenManager.isConnected()) return
        if (_hardcoverMatch.value != null) return // already loaded

        viewModelScope.launch {
            // Tier 1: Direct ID from Grimmory metadata
            val directId = _grimmoryDetail.value?.hardcoverBookId?.toInt()
            val bookId = if (directId != null) {
                directId
            } else {
                // Tier 2: Search by title + author
                val query = buildString {
                    append(book.title)
                    book.author?.let { append(" $it") }
                }
                hardcoverClient.searchBooks(query, limit = 1)
                    .getOrNull()?.firstOrNull()?.bookId
            }

            if (bookId != null) {
                hardcoverClient.fetchBookDetail(bookId)
                    .onSuccess { _hardcoverMatch.value = it }
                    .onFailure { Timber.w(it, "Hardcover: failed to fetch book detail") }

                hardcoverClient.fetchMe().onSuccess { user ->
                    hardcoverClient.fetchUserBookEntry(user.id, bookId)
                        .onSuccess { _hardcoverUserEntry.value = it }
                        .onFailure { Timber.w(it, "Hardcover: failed to fetch user entry") }
                }
            }
        }
    }

    fun downloadBook() {
        val book = _book.value ?: return
        val server = _server.value ?: return
        if (book.isDownloaded) return

        DownloadService.start(context, book.id, server.id)
    }

    /** Re-fetch book detail from Grimmory (e.g. after metadata edit). */
    fun refreshFromServer() {
        viewModelScope.launch {
            val srv = _server.value ?: return@launch
            loadServer(srv.id)
        }
    }

    fun updateReadStatus(status: ReadStatus) {
        val book = _book.value ?: return
        val server = _server.value ?: return
        val grimmoryBookId = book.grimmoryBookId ?: return
        if (!server.isGrimmory) return

        viewModelScope.launch {
            grimmoryClient.updateReadStatus(server.url, server.id, grimmoryBookId, status)
                .onSuccess {
                    _readStatus.value = status
                    _message.value = "Status updated to ${status.name.lowercase().replaceFirstChar { it.uppercase() }}"
                }
                .onFailure {
                    _message.value = "Failed to update status"
                }
        }
    }
}

data class SeriesBookItem(
    val grimmoryBookId: Long,
    val title: String,
    val author: String?,
    val coverUrl: String,
    val localBookId: String?,
)
