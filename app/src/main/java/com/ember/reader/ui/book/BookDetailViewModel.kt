package com.ember.reader.ui.book

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryBookDetail
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.normalizeGrimmoryPercentage
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
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
    private val grimmoryTokenManager: GrimmoryTokenManager,
    @ApplicationContext private val context: Context
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

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() {
        _message.value = null
    }

    private val _coverAuthHeader = MutableStateFlow<String?>(null)
    val coverAuthHeader: StateFlow<String?> = _coverAuthHeader.asStateFlow()

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

        // Build cover auth header
        if (srv.isGrimmory && grimmoryTokenManager.isLoggedIn(srv.id)) {
            val token = grimmoryTokenManager.getAccessToken(srv.id)
            _coverAuthHeader.value = token?.let { "jwt:$it" }
        } else if (srv.opdsUsername.isNotBlank()) {
            val credentials = android.util.Base64.encodeToString(
                "${srv.opdsUsername}:${srv.opdsPassword}".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            _coverAuthHeader.value = "Basic $credentials"
        }

        // Fetch full detail from Grimmory if available
        val grimmoryBookId = _book.value?.grimmoryBookId
        if (srv.isGrimmory && grimmoryTokenManager.isLoggedIn(srv.id) && grimmoryBookId != null) {
            grimmoryClient.getBookDetail(srv.url, srv.id, grimmoryBookId).onSuccess { detail ->
                _readStatus.value = detail.readStatus
                _grimmoryDetail.value = detail
            }
        }
    }

    fun downloadBook() {
        val book = _book.value ?: return
        val server = _server.value ?: return
        if (book.isDownloaded) return

        DownloadService.start(context, book.id, server.id)
    }

    private suspend fun pullProgressAfterDownload(book: Book, server: Server) {
        val grimmoryBookId = book.grimmoryBookId
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            runCatching {
                val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
                val rawPct = detail.readProgress
                if (rawPct != null && rawPct > 0f) {
                    readingProgressRepository.applyRemoteProgress(
                        ReadingProgress.fromRemote(
                            bookId = book.id,
                            serverId = server.id,
                            percentage = rawPct.normalizeGrimmoryPercentage(),
                        )
                    )
                }
            }
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
