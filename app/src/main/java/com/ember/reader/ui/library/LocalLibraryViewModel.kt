package com.ember.reader.ui.library
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.LibraryDensity
import com.ember.reader.core.repository.LibraryFormat
import com.ember.reader.core.repository.LibraryGroupBy
import com.ember.reader.core.repository.LibraryPrefs
import com.ember.reader.core.repository.LibraryPreferencesRepository
import com.ember.reader.core.repository.LibrarySortKey
import com.ember.reader.core.repository.LibrarySource
import com.ember.reader.core.repository.LibraryStatus
import com.ember.reader.core.repository.LibraryViewMode
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.ProgressSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ProgressInfo(val percentage: Float, val lastReadAt: Instant)

/** Either a book entry or a sticky group header. */
sealed interface LibraryListItem {
    data class Header(val key: String, val label: String, val count: Int) : LibraryListItem
    data class BookEntry(val book: Book) : LibraryListItem
}

data class LibraryViewState(
    val items: List<LibraryListItem> = emptyList(),
    val totalCount: Int = 0,
    val inProgress: List<Book> = emptyList(),
    val formatCounts: Map<LibraryFormat, Int> = emptyMap(),
    val sourceCounts: Map<LibrarySource, Int> = emptyMap(),
    val statusCounts: Map<LibraryStatus, Int> = emptyMap(),
)

@HiltViewModel
class LocalLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val progressSyncManager: ProgressSyncManager,
    private val prefsRepo: LibraryPreferencesRepository,
) : ViewModel() {

    val allDownloadedBooks: StateFlow<List<Book>> = bookRepository.observeDownloadedBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressInfo: StateFlow<Map<String, ProgressInfo>> =
        readingProgressRepository.observeAll()
            .map { list -> list.associate { it.bookId to ProgressInfo(it.percentage, it.lastReadAt) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Backwards-compat surface for the bottom progress bar reads. */
    val progressMap: StateFlow<Map<String, Float>> = progressInfo
        .map { m -> m.mapValues { it.value.percentage } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val prefs: StateFlow<LibraryPrefs> = prefsRepo.prefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryPrefs())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val viewState: StateFlow<LibraryViewState> = combine(
        allDownloadedBooks,
        progressInfo,
        prefs,
        _searchQuery,
    ) { books, progress, prefs, query ->
        buildViewState(books, progress, prefs, query)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryViewState())

    private fun buildViewState(
        books: List<Book>,
        progress: Map<String, ProgressInfo>,
        prefs: LibraryPrefs,
        query: String,
    ): LibraryViewState {
        // Counts always reflect the *unfiltered* set so the filter sheet shows true totals.
        val formatCounts = mapOf(
            LibraryFormat.ALL to books.size,
            LibraryFormat.BOOKS to books.count { it.format == BookFormat.EPUB || it.format == BookFormat.PDF },
            LibraryFormat.AUDIOBOOKS to books.count { it.format == BookFormat.AUDIOBOOK },
        )
        val sourceCounts = mapOf(
            LibrarySource.ALL to books.size,
            LibrarySource.SERVER to books.count { it.serverId != null },
            LibrarySource.LOCAL to books.count { it.serverId == null },
        )
        val statusCounts = mapOf(
            LibraryStatus.ALL to books.size,
            LibraryStatus.READING to books.count { statusOf(it, progress) == LibraryStatus.READING },
            LibraryStatus.UNREAD to books.count { statusOf(it, progress) == LibraryStatus.UNREAD },
            LibraryStatus.FINISHED to books.count { statusOf(it, progress) == LibraryStatus.FINISHED },
        )

        val filtered = books.asSequence()
            .filter { matchesSource(it, prefs.sourceFilter) }
            .filter { matchesFormat(it, prefs.formatFilter) }
            .filter { matchesStatus(it, progress, prefs.statusFilter) }
            .filter { matchesQuery(it, query) }
            .toList()

        val sorted = sortBooks(filtered, progress, prefs.sortKey).let {
            if (prefs.sortReversed) it.reversed() else it
        }

        // In-progress carousel — independent of current filters, drives "resume reading."
        val inProgress = books
            .filter {
                val p = progress[it.id]?.percentage ?: 0f
                p > 0f && p < 0.95f
            }
            .sortedByDescending { progress[it.id]?.lastReadAt }
            .take(10)

        val items: List<LibraryListItem> = if (prefs.groupBy == LibraryGroupBy.NONE) {
            sorted.map { LibraryListItem.BookEntry(it) }
        } else {
            buildGrouped(sorted, progress, prefs.groupBy)
        }

        return LibraryViewState(
            items = items,
            totalCount = sorted.size,
            inProgress = inProgress,
            formatCounts = formatCounts,
            sourceCounts = sourceCounts,
            statusCounts = statusCounts,
        )
    }

    private fun statusOf(book: Book, progress: Map<String, ProgressInfo>): LibraryStatus {
        val p = progress[book.id]?.percentage ?: 0f
        return when {
            p >= 0.95f -> LibraryStatus.FINISHED
            p > 0f -> LibraryStatus.READING
            else -> LibraryStatus.UNREAD
        }
    }

    private fun matchesSource(book: Book, filter: LibrarySource): Boolean = when (filter) {
        LibrarySource.ALL -> true
        LibrarySource.SERVER -> book.serverId != null
        LibrarySource.LOCAL -> book.serverId == null
    }

    private fun matchesFormat(book: Book, filter: LibraryFormat): Boolean = when (filter) {
        LibraryFormat.ALL -> true
        LibraryFormat.BOOKS -> book.format == BookFormat.EPUB || book.format == BookFormat.PDF
        LibraryFormat.AUDIOBOOKS -> book.format == BookFormat.AUDIOBOOK
    }

    private fun matchesStatus(book: Book, progress: Map<String, ProgressInfo>, filter: LibraryStatus): Boolean =
        filter == LibraryStatus.ALL || statusOf(book, progress) == filter

    private fun matchesQuery(book: Book, query: String): Boolean {
        if (query.isBlank()) return true
        return book.title.contains(query, ignoreCase = true) ||
            book.author?.contains(query, ignoreCase = true) == true
    }

    private fun sortBooks(
        books: List<Book>,
        progress: Map<String, ProgressInfo>,
        sortKey: LibrarySortKey,
    ): List<Book> = when (sortKey) {
        LibrarySortKey.RECENT -> books.sortedByDescending {
            progress[it.id]?.lastReadAt ?: it.downloadedAt ?: it.addedAt
        }
        LibrarySortKey.TITLE -> books.sortedBy { it.title.lowercase() }
        LibrarySortKey.AUTHOR -> books.sortedBy { it.author?.lowercase() ?: "" }
        LibrarySortKey.PROGRESS -> books.sortedByDescending { progress[it.id]?.percentage ?: 0f }
        LibrarySortKey.DATE_ADDED -> books.sortedByDescending { it.addedAt }
        LibrarySortKey.FILE_SIZE -> books.sortedByDescending { runCatching {
            it.localPath?.let { p -> File(p).length() } ?: 0L
        }.getOrDefault(0L) }
    }

    private fun buildGrouped(
        sorted: List<Book>,
        progress: Map<String, ProgressInfo>,
        groupBy: LibraryGroupBy,
    ): List<LibraryListItem> {
        val grouped: Map<String, Pair<String, List<Book>>> = when (groupBy) {
            LibraryGroupBy.AUTHOR -> sorted.groupBy { it.author ?: "Unknown" }
                .mapValues { it.key to it.value }
                .toSortedMap()
            LibraryGroupBy.SERIES -> sorted.groupBy { it.series ?: "No series" }
                .mapValues { it.key to it.value }
                .toSortedMap()
            LibraryGroupBy.FORMAT -> sorted.groupBy { it.format.name }
                .mapValues { it.key to it.value }
                .toSortedMap()
            LibraryGroupBy.STATUS -> {
                val order = listOf(LibraryStatus.READING, LibraryStatus.UNREAD, LibraryStatus.FINISHED)
                val byStatus = sorted.groupBy { statusOf(it, progress) }
                linkedMapOf<String, Pair<String, List<Book>>>().apply {
                    for (s in order) {
                        byStatus[s]?.let { put(s.name, s.displayLabel() to it) }
                    }
                }
            }
            LibraryGroupBy.DATE_ADDED -> {
                val fmt = DateTimeFormatter.ofPattern("MMMM yyyy")
                sorted.groupBy {
                    val instant = it.addedAt
                    instant.atZone(ZoneId.systemDefault()).format(fmt)
                }.mapValues { it.key to it.value }
            }
            LibraryGroupBy.NONE -> emptyMap()
        }

        return buildList {
            for ((key, pair) in grouped) {
                val (label, list) = pair
                add(LibraryListItem.Header(key, label, list.size))
                for (b in list) add(LibraryListItem.BookEntry(b))
            }
        }
    }

    // ----- Search -----
    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    // ----- Preferences mutations -----
    fun setSort(key: LibrarySortKey) {
        viewModelScope.launch {
            prefsRepo.update {
                if (it.sortKey == key) it.copy(sortReversed = !it.sortReversed)
                else it.copy(sortKey = key, sortReversed = false)
            }
        }
    }

    fun setGroupBy(group: LibraryGroupBy) {
        viewModelScope.launch { prefsRepo.update { it.copy(groupBy = group) } }
    }

    fun setSource(source: LibrarySource) {
        viewModelScope.launch { prefsRepo.update { it.copy(sourceFilter = source) } }
    }

    fun setFormat(format: LibraryFormat) {
        viewModelScope.launch { prefsRepo.update { it.copy(formatFilter = format) } }
    }

    fun setStatus(status: LibraryStatus) {
        viewModelScope.launch { prefsRepo.update { it.copy(statusFilter = status) } }
    }

    fun setViewMode(mode: LibraryViewMode) {
        viewModelScope.launch { prefsRepo.update { it.copy(viewMode = mode) } }
    }

    fun setDensity(density: LibraryDensity) {
        viewModelScope.launch { prefsRepo.update { it.copy(density = density) } }
    }

    fun setShowContinueReading(show: Boolean) {
        viewModelScope.launch { prefsRepo.update { it.copy(showContinueReading = show) } }
    }

    fun setCardShowProgress(v: Boolean) {
        viewModelScope.launch { prefsRepo.update { it.copy(cardShowProgress = v) } }
    }

    fun setCardShowAuthor(v: Boolean) {
        viewModelScope.launch { prefsRepo.update { it.copy(cardShowAuthor = v) } }
    }

    fun setCardShowSourceBadge(v: Boolean) {
        viewModelScope.launch { prefsRepo.update { it.copy(cardShowSourceBadge = v) } }
    }

    fun setCardShowFormatBadge(v: Boolean) {
        viewModelScope.launch { prefsRepo.update { it.copy(cardShowFormatBadge = v) } }
    }

    fun clearAllFilters() {
        viewModelScope.launch {
            _searchQuery.value = ""
            prefsRepo.update {
                it.copy(
                    sourceFilter = LibrarySource.ALL,
                    formatFilter = LibraryFormat.ALL,
                    statusFilter = LibraryStatus.ALL,
                )
            }
        }
    }

    fun applyPreset(preset: LibraryPreset) {
        viewModelScope.launch {
            prefsRepo.update {
                when (preset) {
                    LibraryPreset.CURRENTLY_READING -> it.copy(
                        sourceFilter = LibrarySource.ALL,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.READING,
                    )
                    LibraryPreset.UNREAD -> it.copy(
                        sourceFilter = LibrarySource.ALL,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.UNREAD,
                    )
                    LibraryPreset.FINISHED -> it.copy(
                        sourceFilter = LibrarySource.ALL,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.FINISHED,
                    )
                    LibraryPreset.DOWNLOADED -> it.copy(
                        sourceFilter = LibrarySource.LOCAL,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.ALL,
                    )
                    LibraryPreset.AUDIOBOOKS -> it.copy(
                        sourceFilter = LibrarySource.ALL,
                        formatFilter = LibraryFormat.AUDIOBOOKS,
                        statusFilter = LibraryStatus.ALL,
                    )
                }
            }
        }
    }

    // ----- Selection -----
    fun toggleSelection(bookId: String) {
        _selectedIds.update { ids -> if (bookId in ids) ids - bookId else ids + bookId }
    }

    fun selectAll(books: List<Book>) {
        _selectedIds.value = books.map { it.id }.toSet()
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        _selectedIds.value = emptySet()
        viewModelScope.launch {
            for (id in ids) bookRepository.deleteBook(id)
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

    fun deleteBook(bookId: String) {
        viewModelScope.launch { bookRepository.deleteBook(bookId) }
    }

    fun pullBookProgress(book: Book) {
        val serverId = book.serverId ?: return
        viewModelScope.launch {
            val server = serverRepository.getById(serverId) ?: return@launch
            val result = pullProgressForBook(book, server)
            _operationResult.value = if (result.isNotBlank()) "Pulled$result" else "No remote progress found"
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
            } else "Failed to push progress"
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
                val progressMsg = if (book != null && server != null) pullProgressForBook(book, server) else ""
                _operationResult.value = "Linked to ${match.serverName}$progressMsg"
            } else {
                _operationResult.value = "No matching book found on this server"
            }
        }
    }

    fun dismissOperationResult() { _operationResult.value = null }

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
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Cannot open file")
                }

                val metadata = bookRepository.extractMetadata(destFile)

                val fallbackTitle = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)?.substringBeforeLast('.')
                    } else null
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
                    downloadedAt = Instant.now(),
                )
                bookRepository.addLocalBook(book)
            }.onFailure {
                Timber.e(it, "Failed to import book")
            }
            _importing.value = false
        }
    }
}

enum class LibraryPreset { CURRENTLY_READING, UNREAD, FINISHED, DOWNLOADED, AUDIOBOOKS }

private fun LibraryStatus.displayLabel(): String = when (this) {
    LibraryStatus.READING -> "Reading"
    LibraryStatus.UNREAD -> "Unread"
    LibraryStatus.FINISHED -> "Finished"
    LibraryStatus.ALL -> "All"
}
