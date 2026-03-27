package com.ember.reader.ui.reader.common
import kotlin.math.roundToInt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.SyncFrequency
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryEpubProgress
import com.ember.reader.core.grimmory.GrimmoryFileProgress
import com.ember.reader.core.grimmory.GrimmoryProgressRequest
import com.ember.reader.core.grimmory.GrimmoryReadingSessionRequest
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.readium.toJsonString
import com.ember.reader.core.readium.toLocator
import com.ember.reader.core.readium.toPercentage
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.HighlightRepository
import com.ember.reader.core.repository.ReaderPreferencesRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.repository.SyncPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookOpener: BookOpener,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readerPreferencesRepository: ReaderPreferencesRepository,
    private val serverRepository: ServerRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val readingSessionRepository: com.ember.reader.core.repository.ReadingSessionRepository,
    appPreferencesRepository: AppPreferencesRepository,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _syncConflict = MutableStateFlow<SyncConflict?>(null)
    val syncConflict: StateFlow<SyncConflict?> = _syncConflict.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> =
        readerPreferencesRepository.preferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())

    val keepScreenOn: StateFlow<Boolean> = appPreferencesRepository.keepScreenOnFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    // Navigation request that the screen should execute on the navigator
    private val _pendingNavigation = MutableStateFlow<Float?>(null)
    val pendingNavigation: StateFlow<Float?> = _pendingNavigation.asStateFlow()

    fun onNavigationHandled() {
        _pendingNavigation.value = null
    }

    private var publication: Publication? = null
    private var book: Book? = null
    private var progressSaveJob: Job? = null
    private var sessionStartTime: java.time.Instant? = null
    private var sessionStartProgress: Float = 0f

    init {
        viewModelScope.launch { loadBook() }
        viewModelScope.launch {
            bookmarkRepository.observeByBookId(bookId).collect { _bookmarks.value = it }
        }
        viewModelScope.launch {
            highlightRepository.observeByBookId(bookId).collect { _highlights.value = it }
        }
    }

    private suspend fun loadBook() {
        val loadedBook = bookRepository.getById(bookId)
        if (loadedBook == null) {
            _uiState.value = ReaderUiState.Error("Book not found")
            return
        }
        book = loadedBook

        val localPath = loadedBook.localPath
        if (localPath == null) {
            _uiState.value = ReaderUiState.Error("Book not downloaded")
            return
        }

        val pub = bookOpener.open(File(localPath)).getOrElse { error ->
            _uiState.value = ReaderUiState.Error("Failed to open: ${error.message}")
            return
        }
        publication = pub

        val localProgress = readingProgressRepository.getByBookId(bookId)
        val initialLocator = localProgress?.locatorJson?.toLocator()

        sessionStartTime = java.time.Instant.now()
        sessionStartProgress = localProgress?.percentage ?: 0f

        _uiState.value = ReaderUiState.Ready(
            publication = pub,
            initialLocator = initialLocator,
            book = loadedBook,
        )

        pullRemoteProgressOnOpen(loadedBook, localProgress)
        pullGrimmoryProgressOnOpen(loadedBook, localProgress)
    }

    private suspend fun pullGrimmoryProgressOnOpen(
        loadedBook: Book,
        localProgress: ReadingProgress?,
    ) {
        val serverId = loadedBook.serverId ?: return
        val server = serverRepository.getById(serverId) ?: return
        if (!server.isGrimmory || !grimmoryTokenManager.isLoggedIn(server.id)) return
        val grimmoryBookId = loadedBook.grimmoryBookId ?: return

        runCatching {
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
            val rawRemote = detail.readProgress ?: return
            // Grimmory returns 0-100, Ember uses 0-1
            val remotePercentage = if (rawRemote > 1f) rawRemote / 100f else rawRemote

            val localPercentage = localProgress?.percentage ?: 0f
            Timber.d("Grimmory pull: remote=$remotePercentage (raw=$rawRemote) local=$localPercentage")

            if (remotePercentage > localPercentage + CONFLICT_THRESHOLD) {
                _syncConflict.value = SyncConflict(
                    remotePercentage = remotePercentage,
                    localPercentage = localPercentage,
                    remoteLocatorJson = null,
                    remoteDevice = "Grimmory Web Reader",
                    remoteProgress = ReadingProgress(
                        bookId = bookId,
                        serverId = serverId,
                        percentage = remotePercentage,
                        lastReadAt = java.time.Instant.now(),
                        syncedAt = java.time.Instant.now(),
                        needsSync = false,
                    ),
                )
            }
        }.onFailure {
            Timber.w(it, "Failed to pull Grimmory progress on open")
        }
    }

    private data class SyncContext(
        val server: com.ember.reader.core.model.Server,
        val fileHash: String,
    )

    private suspend fun getSyncContext(): SyncContext? {
        val loadedBook = book
            ?: return null.also { Timber.d("Sync: no book loaded") }
        val serverId = loadedBook.serverId
            ?: return null.also { Timber.d("Sync: no serverId") }
        val fileHash = loadedBook.fileHash
            ?: return null.also { Timber.d("Sync: no fileHash") }
        val syncFrequency = syncPreferencesRepository.syncFrequencyFlow.first()
        if (syncFrequency == SyncFrequency.MANUAL) return null.also { Timber.d("Sync: frequency is MANUAL, skipping") }
        val server = serverRepository.getById(serverId)
            ?: return null.also { Timber.d("Sync: server $serverId not found") }
        if (server.kosyncUsername.isBlank()) return null.also { Timber.d("Sync: kosync username blank") }
        Timber.d("Sync: context ready — server='${server.name}' hash='$fileHash'")
        return SyncContext(server, fileHash)
    }

    private suspend fun pullRemoteProgressOnOpen(
        loadedBook: Book,
        localProgress: ReadingProgress?,
    ) {
        val (server, fileHash) = getSyncContext() ?: return

        Timber.d("Sync: pulling progress for hash=$fileHash")
        val result = readingProgressRepository.pullProgress(server, bookId, fileHash)
        val remoteResult = result.getOrNull()
        if (remoteResult == null) {
            Timber.d("Sync: no remote progress found (result=${result.exceptionOrNull()?.message})")
            return
        }
        val remote = remoteResult.progress

        val localPercentage = localProgress?.percentage ?: 0f
        Timber.d("Sync: remote=${remote.percentage} local=$localPercentage device=${remoteResult.deviceName}")
        if (remote.percentage > localPercentage + CONFLICT_THRESHOLD) {
            _syncConflict.value = SyncConflict(
                remotePercentage = remote.percentage,
                localPercentage = localPercentage,
                remoteLocatorJson = remote.locatorJson,
                remoteDevice = remoteResult.deviceName,
                remoteProgress = remote,
            )
        } else {
            Timber.d("Sync: remote not ahead enough to show conflict (threshold=$CONFLICT_THRESHOLD)")
        }
    }

    fun acceptRemoteProgress() {
        val conflict = _syncConflict.value ?: return
        _syncConflict.value = null
        viewModelScope.launch {
            conflict.remoteProgress?.let { readingProgressRepository.applyRemoteProgress(it) }
        }
        // Navigate by percentage — works regardless of progress format (Locator, XPointer, CFI)
        _pendingNavigation.value = conflict.remotePercentage
    }

    fun dismissSyncConflict() {
        _syncConflict.value = null
    }

    fun onLocatorChanged(locator: Locator) {
        _currentLocator.value = locator
        scheduleSaveProgress(locator)
    }

    fun toggleChrome() {
        _chromeVisible.update { !it }
    }

    fun addBookmark() {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(
                bookId = bookId,
                locatorJson = locator.toJsonString(),
                title = locator.title,
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(id) }
    }

    fun addHighlight(locatorJson: String, color: HighlightColor, annotation: String? = null) {
        viewModelScope.launch {
            highlightRepository.addHighlight(bookId, locatorJson, color, annotation)
        }
    }

    fun updateHighlightAnnotation(highlight: Highlight, annotation: String?) {
        viewModelScope.launch {
            highlightRepository.updateAnnotation(highlight, annotation)
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { highlightRepository.deleteHighlight(id) }
    }

    fun updatePreferences(preferences: ReaderPreferences) {
        viewModelScope.launch { readerPreferencesRepository.updatePreferences(preferences) }
    }

    private fun scheduleSaveProgress(locator: Locator) {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveProgress(locator)
        }
    }

    private suspend fun saveProgress(locator: Locator) {
        readingProgressRepository.updateProgress(
            bookId = bookId,
            serverId = book?.serverId,
            percentage = locator.toPercentage(),
            locatorJson = locator.toJsonString(),
        )
    }

    private suspend fun pushProgressOnClose() {
        val (server, fileHash) = getSyncContext() ?: return

        // Kosync push
        readingProgressRepository.pushProgress(server, bookId, fileHash).onFailure {
            Timber.w(it, "Failed to push kosync progress on close")
        }

        // Grimmory native push
        val loadedBook = book
        Timber.d("Grimmory push check: isGrimmory=${server.isGrimmory} loggedIn=${grimmoryTokenManager.isLoggedIn(server.id)} opdsEntryId=${loadedBook?.opdsEntryId} grimmoryBookId=${loadedBook?.grimmoryBookId}")
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
            if (loadedBook == null) { Timber.d("Grimmory push: no book loaded"); return }
            val grimmoryBookId = loadedBook.grimmoryBookId
            if (grimmoryBookId == null) { Timber.d("Grimmory push: no grimmoryBookId from opdsEntryId='${loadedBook.opdsEntryId}'"); return }
            val progress = readingProgressRepository.getByBookId(bookId) ?: run { Timber.d("Grimmory push: no local progress"); return }
            runCatching {
                // Get book detail to find the primary file ID
                val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrNull()
                val fileId = detail?.files?.firstOrNull()?.id

                // Grimmory stores percentages as 0-100, not 0-1
                val pct = kotlin.math.round(progress.percentage * 1000f) / 10f  // e.g. 0.2393 → 23.9

                val request = GrimmoryProgressRequest(
                    bookId = grimmoryBookId,
                    fileProgress = fileId?.let {
                        GrimmoryFileProgress(
                            bookFileId = it,
                            progressPercent = pct,
                        )
                    },
                    epubProgress = GrimmoryEpubProgress(
                        cfi = "epubcfi(/6/2)",
                        percentage = pct,
                    ),
                )
                Timber.d("Grimmory push: bookId=$grimmoryBookId fileId=$fileId percentage=${progress.percentage}")
                grimmoryClient.pushProgress(
                    baseUrl = server.url,
                    serverId = server.id,
                    request = request,
                ).getOrThrow()
                Timber.d("Pushed Grimmory progress: ${(progress.percentage * 100).roundToInt()}%")
            }.onFailure {
                Timber.w(it, "Failed to push Grimmory progress on close")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _currentLocator.value?.let { locator ->
            kotlinx.coroutines.runBlocking {
                saveProgress(locator)
            }
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                pushProgressOnClose()
                recordReadingSession()
            }
        }
        publication?.close()
    }

    private suspend fun recordReadingSession() {
        val startTime = sessionStartTime ?: return
        val loadedBook = book ?: return

        val endTime = java.time.Instant.now()
        val durationSeconds = java.time.Duration.between(startTime, endTime).seconds
        if (durationSeconds < 30) return // Skip accidental opens

        val endProgress = readingProgressRepository.getByBookId(bookId)?.percentage ?: 0f

        // Save locally (always)
        readingSessionRepository.saveSession(
            com.ember.reader.core.model.ReadingSession(
                bookId = bookId,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = durationSeconds,
                startProgress = sessionStartProgress,
                endProgress = endProgress,
            ),
        )
        Timber.d("Saved local session: ${durationSeconds}s, ${(sessionStartProgress * 100).roundToInt()}% → ${(endProgress * 100).roundToInt()}%")

        // Push to Grimmory (if available)
        val serverId = loadedBook.serverId ?: return
        val server = serverRepository.getById(serverId) ?: return
        if (!server.isGrimmory || !grimmoryTokenManager.isLoggedIn(server.id)) return
        val grimmoryBookId = loadedBook.grimmoryBookId ?: return

        val startPct = kotlin.math.round(sessionStartProgress * 1000f) / 10f
        val endPct = kotlin.math.round(endProgress * 1000f) / 10f
        val bookType = when (loadedBook.format) {
            com.ember.reader.core.model.BookFormat.PDF -> "PDF"
            else -> "EPUB"
        }

        runCatching {
            grimmoryClient.recordReadingSession(
                baseUrl = server.url,
                serverId = server.id,
                request = GrimmoryReadingSessionRequest(
                    bookId = grimmoryBookId,
                    bookType = bookType,
                    startTime = startTime.toString(),
                    endTime = endTime.toString(),
                    durationSeconds = durationSeconds,
                    startProgress = startPct,
                    endProgress = endPct,
                    progressDelta = endPct - startPct,
                ),
            ).getOrThrow()
        }.onFailure {
            Timber.w(it, "Failed to push reading session to Grimmory")
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 5000L
        private const val CONFLICT_THRESHOLD = 0.01f
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val publication: Publication,
        val initialLocator: Locator?,
        val book: Book,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

data class SyncConflict(
    val remotePercentage: Float,
    val localPercentage: Float,
    val remoteLocatorJson: String?,
    val remoteDevice: String?,
    val remoteProgress: com.ember.reader.core.model.ReadingProgress? = null,
)
