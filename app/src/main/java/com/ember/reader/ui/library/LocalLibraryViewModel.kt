package com.ember.reader.ui.library
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryHttpException
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.LibraryDensity
import com.ember.reader.core.repository.LibraryFormat
import com.ember.reader.core.repository.LibraryGroupBy
import com.ember.reader.core.repository.LibraryPreferencesRepository
import com.ember.reader.core.repository.LibraryPrefs
import com.ember.reader.core.repository.LibrarySortKey
import com.ember.reader.core.repository.LibrarySourceFilter
import com.ember.reader.core.repository.LibraryStatus
import com.ember.reader.core.repository.LibraryViewMode
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerAppearance
import com.ember.reader.core.repository.ServerAppearanceResolver
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.ProgressSyncManager
import com.ember.reader.core.sync.PushResult
import com.ember.reader.core.sync.SkipReason
import com.ember.reader.core.sync.SourceOutcome
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ProgressInfo(val percentage: Float, val lastReadAt: Instant)

/** Either a book entry or a sticky group header. */
sealed interface LibraryListItem {
    /**
     * Group header. [colorSlot] is non-null only for server source groups and encodes
     * the palette index the UI should render beside the label; `null` means "no dot"
     * (non-source groups, or the Local source group which uses the neutral accent).
     */
    data class Header(
        val key: String,
        val label: String,
        val count: Int,
        val colorSlot: Int? = null,
        val isLocalSource: Boolean = false
    ) : LibraryListItem
    data class BookEntry(val book: Book) : LibraryListItem
}

data class LibraryViewState(
    val items: List<LibraryListItem> = emptyList(),
    val totalCount: Int = 0,
    val inProgress: List<Book> = emptyList(),
    val formatCounts: Map<LibraryFormat, Int> = emptyMap(),
    val sourceCounts: Map<LibrarySourceFilter, Int> = emptyMap(),
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
    serverAppearanceResolver: ServerAppearanceResolver
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

    val appearances: StateFlow<Map<Long, ServerAppearance>> = serverAppearanceResolver.appearances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val viewState: StateFlow<LibraryViewState> = combine(
        combine(allDownloadedBooks, progressInfo, prefs) { b, p, pr -> Triple(b, p, pr) },
        _searchQuery,
        servers,
        appearances
    ) { (books, progress, p), query, srv, appMap ->
        buildViewState(books, progress, p, query, srv, appMap)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryViewState())

    init {
        // When the selected-server filter points at a server that no longer exists, silently
        // reset to All. The filter sheet UI would otherwise show "Source: (unknown)" and yield
        // an empty library because the id doesn't match any book.
        combine(prefs, servers) { p, srv ->
            val filter = p.sourceFilter
            (filter as? LibrarySourceFilter.Server)?.takeIf { srv.none { s -> s.id == it.serverId } }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { prefsRepo.update { it.copy(sourceFilter = LibrarySourceFilter.All) } }
            .launchIn(viewModelScope)
    }

    private fun buildViewState(
        books: List<Book>,
        progress: Map<String, ProgressInfo>,
        prefs: LibraryPrefs,
        query: String,
        servers: List<Server>,
        appearances: Map<Long, ServerAppearance>
    ): LibraryViewState {
        // Counts always reflect the *unfiltered* set so the filter sheet shows true totals.
        val formatCounts = mapOf(
            LibraryFormat.ALL to books.size,
            LibraryFormat.BOOKS to books.count { it.format == BookFormat.EPUB || it.format == BookFormat.PDF },
            LibraryFormat.AUDIOBOOKS to books.count { it.format == BookFormat.AUDIOBOOK }
        )
        val countsByServerId: Map<Long?, Int> = books.groupingBy { it.serverId }.eachCount()
        val sourceCounts: Map<LibrarySourceFilter, Int> = buildMap {
            put(LibrarySourceFilter.All, books.size)
            put(LibrarySourceFilter.Local, countsByServerId[null] ?: 0)
            for (server in servers) {
                put(LibrarySourceFilter.Server(server.id), countsByServerId[server.id] ?: 0)
            }
        }
        val statusCounts = mapOf(
            LibraryStatus.ALL to books.size,
            LibraryStatus.READING to books.count { statusOf(it, progress) == LibraryStatus.READING },
            LibraryStatus.UNREAD to books.count { statusOf(it, progress) == LibraryStatus.UNREAD },
            LibraryStatus.FINISHED to books.count { statusOf(it, progress) == LibraryStatus.FINISHED }
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
            buildGrouped(sorted, progress, prefs.groupBy, servers, appearances)
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

    private fun matchesSource(book: Book, filter: LibrarySourceFilter): Boolean = when (filter) {
        LibrarySourceFilter.All -> true
        LibrarySourceFilter.Local -> book.serverId == null
        is LibrarySourceFilter.Server -> book.serverId == filter.serverId
    }

    private fun matchesFormat(book: Book, filter: LibraryFormat): Boolean = when (filter) {
        LibraryFormat.ALL -> true
        LibraryFormat.BOOKS -> book.format == BookFormat.EPUB || book.format == BookFormat.PDF
        LibraryFormat.AUDIOBOOKS -> book.format == BookFormat.AUDIOBOOK
    }

    private fun matchesStatus(
        book: Book,
        progress: Map<String, ProgressInfo>,
        filter: LibraryStatus
    ): Boolean = filter == LibraryStatus.ALL || statusOf(book, progress) == filter

    private fun matchesQuery(book: Book, query: String): Boolean {
        if (query.isBlank()) return true
        return book.title.contains(query, ignoreCase = true) ||
            book.author?.contains(query, ignoreCase = true) == true
    }

    private fun sortBooks(
        books: List<Book>,
        progress: Map<String, ProgressInfo>,
        sortKey: LibrarySortKey
    ): List<Book> = when (sortKey) {
        LibrarySortKey.RECENT -> books.sortedByDescending {
            progress[it.id]?.lastReadAt ?: it.downloadedAt ?: it.addedAt
        }
        LibrarySortKey.TITLE -> books.sortedBy { it.title.lowercase() }
        LibrarySortKey.AUTHOR -> books.sortedBy { it.author?.lowercase() ?: "" }
        LibrarySortKey.PROGRESS -> books.sortedByDescending { progress[it.id]?.percentage ?: 0f }
        LibrarySortKey.DATE_ADDED -> books.sortedByDescending { it.addedAt }
        LibrarySortKey.FILE_SIZE -> books.sortedByDescending {
            runCatching {
                it.localPath?.let { p -> File(p).length() } ?: 0L
            }.getOrDefault(0L)
        }
    }

    private fun buildGrouped(
        sorted: List<Book>,
        progress: Map<String, ProgressInfo>,
        groupBy: LibraryGroupBy,
        servers: List<Server>,
        appearances: Map<Long, ServerAppearance>
    ): List<LibraryListItem> {
        if (groupBy == LibraryGroupBy.SOURCE) {
            val byServerId = sorted.groupBy { it.serverId }
            return buildList {
                for (server in servers) {
                    val list = byServerId[server.id].orEmpty()
                    if (list.isEmpty()) continue
                    add(
                        LibraryListItem.Header(
                            key = "server:${server.id}",
                            label = server.name,
                            count = list.size,
                            colorSlot = appearances[server.id]?.colorSlot
                        )
                    )
                    for (b in list) add(LibraryListItem.BookEntry(b))
                }
                byServerId[null]?.takeIf { it.isNotEmpty() }?.let { list ->
                    add(
                        LibraryListItem.Header(
                            key = "local",
                            label = "Local",
                            count = list.size,
                            isLocalSource = true
                        )
                    )
                    for (b in list) add(LibraryListItem.BookEntry(b))
                }
            }
        }

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
                val order =
                    listOf(LibraryStatus.READING, LibraryStatus.UNREAD, LibraryStatus.FINISHED)
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
            LibraryGroupBy.SOURCE, LibraryGroupBy.NONE -> emptyMap()
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
                if (it.sortKey == key) {
                    it.copy(sortReversed = !it.sortReversed)
                } else {
                    it.copy(sortKey = key, sortReversed = false)
                }
            }
        }
    }

    fun setGroupBy(group: LibraryGroupBy) {
        viewModelScope.launch { prefsRepo.update { it.copy(groupBy = group) } }
    }

    fun setSource(source: LibrarySourceFilter) {
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
                    sourceFilter = LibrarySourceFilter.All,
                    formatFilter = LibraryFormat.ALL,
                    statusFilter = LibraryStatus.ALL,
                    sortKey = LibrarySortKey.RECENT,
                    sortReversed = false,
                    groupBy = LibraryGroupBy.NONE
                )
            }
        }
    }

    fun applyPreset(preset: LibraryPreset) {
        viewModelScope.launch {
            prefsRepo.update {
                when (preset) {
                    LibraryPreset.CURRENTLY_READING -> it.copy(
                        sourceFilter = LibrarySourceFilter.All,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.READING
                    )
                    LibraryPreset.UNREAD -> it.copy(
                        sourceFilter = LibrarySourceFilter.All,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.UNREAD
                    )
                    LibraryPreset.FINISHED -> it.copy(
                        sourceFilter = LibrarySourceFilter.All,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.FINISHED
                    )
                    LibraryPreset.DOWNLOADED -> it.copy(
                        sourceFilter = LibrarySourceFilter.Local,
                        formatFilter = LibraryFormat.ALL,
                        statusFilter = LibraryStatus.ALL
                    )
                    LibraryPreset.AUDIOBOOKS -> it.copy(
                        sourceFilter = LibrarySourceFilter.All,
                        formatFilter = LibraryFormat.AUDIOBOOKS,
                        statusFilter = LibraryStatus.ALL
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

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

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
            val result = progressSyncManager.pushProgress(server, book)
            _operationResult.value = describePushResult(result, progress.percentage)
        }
    }

    /**
     * Surface per-channel outcomes so the user can see *what* synced. Collapsing
     * this to a single "Pushed to server" message hides real failures
     * (e.g. Grimmory succeeded but kosync silently 401'd) and leaves the user
     * thinking they're in sync when they aren't.
     */
    private fun describePushResult(result: PushResult, percentage: Float): String {
        val pct = (percentage * 100).roundToInt()
        if (result.allSkipped) return "No sync channel available for this book"

        val kosyncLabel = channelLabel("kosync", result.kosync)
        val grimmoryLabel = channelLabel("Grimmory", result.grimmory)

        return when {
            result.anySucceeded && !result.anyFailed -> {
                // Clean success across any non-skipped channels.
                val synced = listOfNotNull(
                    if (result.kosync is SourceOutcome.Ok) "kosync" else null,
                    if (result.grimmory is SourceOutcome.Ok) "Grimmory" else null
                ).joinToString(" + ")
                val skippedNote = listOfNotNull(
                    (result.kosync as? SourceOutcome.Skipped)?.let { "kosync ${skipReasonText(it.reason)}" },
                    (result.grimmory as? SourceOutcome.Skipped)?.let { "Grimmory ${skipReasonText(it.reason)}" }
                ).joinToString("; ")
                if (skippedNote.isBlank()) "Pushed $pct% to $synced" else "Pushed $pct% to $synced ($skippedNote)"
            }
            result.anySucceeded && result.anyFailed -> "Pushed $pct% — $kosyncLabel, $grimmoryLabel"
            result.allFailed -> "Push failed: ${errorText(result.firstActionableError())}"
            else -> "Push failed"
        }
    }

    private fun channelLabel(name: String, outcome: SourceOutcome<*>): String = when (outcome) {
        is SourceOutcome.Ok -> "$name OK"
        is SourceOutcome.Skipped -> "$name ${skipReasonText(outcome.reason)}"
        is SourceOutcome.Failure -> "$name failed (${errorText(outcome.error)})"
    }

    private fun skipReasonText(reason: SkipReason): String = when (reason) {
        SkipReason.NoLocalProgress -> "nothing to push"
        SkipReason.NoFileHash -> "skipped (not downloaded locally)"
        SkipReason.NoKosyncCreds -> "not configured"
        SkipReason.KosyncDisabled -> "disabled"
        SkipReason.NoGrimmoryBookId -> "not linked"
        SkipReason.ServerNotGrimmory -> "not available"
        SkipReason.GrimmoryNotLoggedIn -> "sign in required"
    }

    private fun errorText(error: Throwable?): String = when (error) {
        is GrimmoryAuthExpiredException -> "session expired, sign in again"
        is GrimmoryHttpException -> "server error ${error.statusCode}"
        null -> "unknown error"
        else -> error.message?.takeIf {
            it.isNotBlank()
        } ?: error::class.simpleName.orEmpty().ifBlank { "unknown error" }
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
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Cannot open file")
                }

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

enum class LibraryPreset { CURRENTLY_READING, UNREAD, FINISHED, DOWNLOADED, AUDIOBOOKS }

private fun LibraryStatus.displayLabel(): String = when (this) {
    LibraryStatus.READING -> "Reading"
    LibraryStatus.UNREAD -> "Unread"
    LibraryStatus.FINISHED -> "Finished"
    LibraryStatus.ALL -> "All"
}
