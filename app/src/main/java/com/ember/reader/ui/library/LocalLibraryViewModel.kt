package com.ember.reader.ui.library
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.ProgressSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class LibrarySortMode(val displayName: String) {
    RECENT("Recent"),
    TITLE("A-Z"),
    AUTHOR("Author"),
    PROGRESS("Progress")
}

@HiltViewModel
class LocalLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val progressSyncManager: ProgressSyncManager,
) : ViewModel() {

    val allDownloadedBooks: StateFlow<List<Book>> = bookRepository.observeDownloadedBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressMap: StateFlow<Map<String, Float>> = readingProgressRepository.observeAll()
        .map { list -> list.associate { it.bookId to it.percentage } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _coverAuthHeaders = MutableStateFlow<Map<Long, String>>(emptyMap())
    val coverAuthHeaders: StateFlow<Map<Long, String>> = _coverAuthHeaders.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _sortMode = MutableStateFlow(LibrarySortMode.RECENT)
    val sortMode: StateFlow<LibrarySortMode> = _sortMode.asStateFlow()

    private val _sortReversed = MutableStateFlow(false)
    val sortReversed: StateFlow<Boolean> = _sortReversed.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            serverRepository.observeAll().collect { servers ->
                _coverAuthHeaders.value = servers.associate { server ->
                    val auth = if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
                        grimmoryTokenManager.getAccessToken(server.id)?.let { "jwt:$it" }
                    } else {
                        null
                    }
                    server.id to (auth ?: com.ember.reader.core.network.basicAuthHeader(server.opdsUsername, server.opdsPassword))
                }
            }
        }
    }

    fun updateSortMode(mode: LibrarySortMode) {
        if (_sortMode.value == mode) {
            _sortReversed.update { !it }
        } else {
            _sortMode.value = mode
            _sortReversed.value = false
        }
    }

    fun sortBooks(books: List<Book>): List<Book> {
        val progress = progressMap.value
        return when (_sortMode.value) {
            LibrarySortMode.RECENT -> books.sortedByDescending { it.downloadedAt ?: it.addedAt }
            LibrarySortMode.TITLE -> books.sortedBy { it.title.lowercase() }
            LibrarySortMode.AUTHOR -> books.sortedBy { it.author?.lowercase() ?: "" }
            LibrarySortMode.PROGRESS -> books.sortedByDescending { progress[it.id] ?: 0f }
        }
    }

    // Selection
    fun toggleSelection(bookId: String) {
        _selectedIds.update { ids ->
            if (bookId in ids) ids - bookId else ids + bookId
        }
    }

    fun selectAll(books: List<Book>) {
        _selectedIds.value = books.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        _selectedIds.value = emptySet()
        viewModelScope.launch {
            for (id in ids) {
                bookRepository.deleteBook(id)
            }
            _operationResult.value = "Deleted ${ids.size} book(s)"
        }
    }

    fun syncSelectedProgress() {
        val ids = _selectedIds.value.toList()
        _selectedIds.value = emptySet()
        viewModelScope.launch {
            var synced = 0
            val books = ids.mapNotNull { bookRepository.getById(it) }
            val booksByServer = books.groupBy { it.serverId }
            for ((serverId, serverBooks) in booksByServer) {
                if (serverId == null) continue
                val server = serverRepository.getById(serverId) ?: continue
                for (book in serverBooks) {
                    val result = pullProgressForBook(book, server)
                    if (result.isNotBlank()) synced++
                }
            }
            _operationResult.value = if (synced > 0) "Synced $synced book(s)" else "No remote progress found"
        }
    }

    // Single book operations
    fun deleteBook(bookId: String) {
        viewModelScope.launch { bookRepository.deleteBook(bookId) }
    }

    fun pullBookProgress(book: Book) {
        val serverId = book.serverId ?: return
        viewModelScope.launch {
            val server = serverRepository.getById(serverId) ?: return@launch
            val result = pullProgressForBook(book, server)
            _operationResult.value = if (result.isNotBlank()) {
                "Pulled$result"
            } else {
                "No remote progress found"
            }
        }
    }

    fun pushBookProgress(book: Book) {
        val serverId = book.serverId ?: return
        viewModelScope.launch {
            val server = serverRepository.getById(serverId) ?: return@launch
            val progress = readingProgressRepository.getByBookId(book.id)
            if (progress == null || progress.percentage <= 0f) {
                _operationResult.value = "No local progress to push"
                return@launch
            }
            val pushed = progressSyncManager.pushProgress(server, book)
            _operationResult.value = if (pushed) {
                "Pushed ${(progress.percentage * 100).roundToInt()}% to server"
            } else {
                "Failed to push progress"
            }
        }
    }

    private suspend fun pullProgressForBook(book: Book, server: Server): String {
        val remote = progressSyncManager.pullBestProgress(server, book) ?: return ""
        readingProgressRepository.applyRemoteProgress(remote.progress)
        return " (${(remote.progress.percentage * 100).roundToInt()}% from ${remote.source})"
    }

    fun relinkBook(bookId: String, serverId: Long) {
        viewModelScope.launch {
            _operationResult.value = "Searching..."
            val match = bookRepository.findRelinkMatches(bookId, serverId)
            if (match != null) {
                bookRepository.relinkToServerBook(bookId, match.serverBookId)
                val book = bookRepository.getById(match.serverBookId)
                val server = serverRepository.getById(match.serverId)
                val progressMsg = if (book != null && server != null) {
                    pullProgressForBook(book, server)
                } else ""
                _operationResult.value = "Linked to ${match.serverName}$progressMsg"
            } else {
                _operationResult.value = "No matching book found on this server"
            }
        }
    }

    fun dismissOperationResult() {
        _operationResult.value = null
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

                // Extract metadata from the file (title, author, cover, etc.)
                val metadata = bookRepository.extractMetadata(destFile)

                val fallbackTitle = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)?.substringBeforeLast('.')
                    } else {
                        null
                    }
                } ?: "Untitled"

                val book = Book(
                    id = id,
                    title = metadata.title.takeIf { it != destFile.nameWithoutExtension } ?: fallbackTitle,
                    author = metadata.author,
                    description = metadata.description,
                    coverUrl = metadata.coverUrl,
                    format = format,
                    localPath = destFile.absolutePath,
                    publisher = metadata.publisher,
                    language = metadata.language,
                    subjects = metadata.subjects,
                    pageCount = metadata.pageCount,
                    publishedDate = metadata.publishedDate,
                    addedAt = Instant.now(),
                    downloadedAt = Instant.now()
                )
                bookRepository.addLocalBook(book)
            }.onFailure {
                Timber.e(it, "Failed to import book")
            }
            _importing.value = false
        }
    }
}
