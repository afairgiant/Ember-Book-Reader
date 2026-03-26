package com.ember.reader.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LocalLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
) : ViewModel() {

    val allDownloadedBooks: StateFlow<List<Book>> = bookRepository.observeDownloadedBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of bookId → reading percentage
    val progressMap: StateFlow<Map<String, Float>> = readingProgressRepository.observeAll()
        .map { list -> list.associate { it.bookId to it.percentage } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Map of serverId → Basic auth header for cover loading
    private val _coverAuthHeaders = MutableStateFlow<Map<Long, String>>(emptyMap())
    val coverAuthHeaders: StateFlow<Map<Long, String>> = _coverAuthHeaders.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    init {
        viewModelScope.launch {
            serverRepository.observeAll().collect { servers ->
                _coverAuthHeaders.value = servers.associate { server ->
                    server.id to com.ember.reader.core.network.basicAuthHeader(server.opdsUsername, server.opdsPassword)
                }
            }
        }
    }

    fun importBook(uri: Uri) {
        _importing.value = true
        viewModelScope.launch {
            runCatching {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri)
                val format = when {
                    mimeType?.contains("epub") == true -> BookFormat.EPUB
                    mimeType?.contains("pdf") == true -> BookFormat.PDF
                    uri.path?.endsWith(".epub", ignoreCase = true) == true -> BookFormat.EPUB
                    uri.path?.endsWith(".pdf", ignoreCase = true) == true -> BookFormat.PDF
                    else -> BookFormat.EPUB
                }

                val id = UUID.randomUUID().toString()
                val extension = if (format == BookFormat.PDF) "pdf" else "epub"
                val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
                val destFile = File(booksDir, "$id.$extension")

                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("Cannot open file")
                }

                val title = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.')
                    ?: "Untitled"

                val book = Book(
                    id = id,
                    title = title,
                    format = format,
                    localPath = destFile.absolutePath,
                    addedAt = Instant.now(),
                    downloadedAt = Instant.now(),
                )
                bookRepository.addLocalBook(book)
            }.onFailure {
                Timber.e(it, "Failed to import book")
            }
            _importing.value = false
        }
    }

    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    fun deleteBook(bookId: String) {
        viewModelScope.launch { bookRepository.deleteBook(bookId) }
    }

    fun relinkBook(bookId: String, serverId: Long) {
        viewModelScope.launch {
            _operationResult.value = "Searching..."
            val match = bookRepository.findRelinkMatches(bookId, serverId)
            if (match != null) {
                val result = bookRepository.relinkToServerBook(bookId, match.serverBookId)
                _operationResult.value = "Linked to ${match.serverName}"
            } else {
                _operationResult.value = "No matching book found on this server"
            }
        }
    }

    fun dismissOperationResult() {
        _operationResult.value = null
    }

    fun syncBookProgress(book: Book) {
        val serverId = book.serverId ?: return
        val fileHash = book.fileHash ?: return
        viewModelScope.launch {
            val server = serverRepository.getById(serverId) ?: run {
                _operationResult.value = "Server not found"
                return@launch
            }
            if (server.kosyncUsername.isBlank()) {
                _operationResult.value = "Kosync credentials not configured"
                return@launch
            }
            val result = readingProgressRepository.pullProgress(server, book.id, fileHash)
            val remote = result.getOrNull()
            if (remote != null) {
                readingProgressRepository.applyRemoteProgress(remote.progress)
                _operationResult.value = "Synced: ${(remote.progress.percentage * 100).toInt()}% from ${remote.deviceName ?: "server"}"
            } else {
                _operationResult.value = "No remote progress found"
            }
        }
    }
}
