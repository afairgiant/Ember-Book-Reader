package com.ember.reader.ui.book

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.grimmory.ReadStatus
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.ui.common.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _server = MutableStateFlow<Server?>(null)
    val server: StateFlow<Server?> = _server.asStateFlow()

    private val _readStatus = MutableStateFlow<ReadStatus?>(null)
    val readStatus: StateFlow<ReadStatus?> = _readStatus.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _coverAuthHeader = MutableStateFlow<String?>(null)
    val coverAuthHeader: StateFlow<String?> = _coverAuthHeader.asStateFlow()

    val progress: StateFlow<ReadingProgress?> = readingProgressRepository
        .observeByBookId(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            bookRepository.observeById(bookId).collect { book ->
                _book.value = book
                book?.serverId?.let { loadServer(it) }
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
                android.util.Base64.NO_WRAP,
            )
            _coverAuthHeader.value = "Basic $credentials"
        }

        // Fetch read status from Grimmory if available
        val grimmoryBookId = _book.value?.grimmoryBookId
        if (srv.isGrimmory && grimmoryTokenManager.isLoggedIn(srv.id) && grimmoryBookId != null) {
            grimmoryClient.getBookDetail(srv.url, srv.id, grimmoryBookId).onSuccess { detail ->
                _readStatus.value = detail.readStatus
            }
        }
    }

    fun downloadBook() {
        val book = _book.value ?: return
        val server = _server.value ?: return
        if (book.isDownloaded || _downloading.value) return

        _downloading.value = true
        viewModelScope.launch {
            bookRepository.downloadBook(book, server).onSuccess { downloadedBook ->
                _book.value = downloadedBook
                pullProgressAfterDownload(downloadedBook, server)
                NotificationHelper.showDownloadComplete(context, book.title, book.id)
            }
            _downloading.value = false
        }
    }

    private suspend fun pullProgressAfterDownload(book: Book, server: Server) {
        val grimmoryBookId = book.grimmoryBookId
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            runCatching {
                val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
                val rawPct = detail.readProgress
                if (rawPct != null && rawPct > 0f) {
                    val pct = if (rawPct > 1f) rawPct / 100f else rawPct
                    readingProgressRepository.applyRemoteProgress(
                        ReadingProgress(
                            bookId = book.id,
                            serverId = server.id,
                            percentage = pct,
                            lastReadAt = java.time.Instant.now(),
                            syncedAt = java.time.Instant.now(),
                            needsSync = false,
                        ),
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
                }
        }
    }
}
