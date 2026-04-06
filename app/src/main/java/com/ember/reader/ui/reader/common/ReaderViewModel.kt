package com.ember.reader.ui.reader.common
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryReadingSessionRequest
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.toGrimmoryPercentage
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.readium.toJsonString
import com.ember.reader.core.readium.toLocator
import com.ember.reader.core.readium.toPercentage
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.HighlightRepository
import com.ember.reader.core.repository.ReaderPreferencesRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.repository.SyncPreferencesRepository
import com.ember.reader.core.sync.ProgressSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt
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
import org.readium.r2.shared.publication.services.locateProgression
import timber.log.Timber

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
    private val appPreferencesRepository: AppPreferencesRepository,
    private val progressSyncManager: ProgressSyncManager,
    val dictionaryRepository: com.ember.reader.core.dictionary.DictionaryRepository,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _chromeVisible = MutableStateFlow(false)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _syncConflict = MutableStateFlow<SyncConflict?>(null)
    val syncConflict: StateFlow<SyncConflict?> = _syncConflict.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> =
        readerPreferencesRepository.preferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())

    val keepScreenOn: StateFlow<Boolean> = appPreferencesRepository.keepScreenOnFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _showTapZoneHint = MutableStateFlow(false)
    val showTapZoneHint: StateFlow<Boolean> = _showTapZoneHint.asStateFlow()

    fun dismissTapZoneHint() {
        _showTapZoneHint.value = false
    }

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
    private var sessionPauseJob: Job? = null
    private var sessionStartTime: java.time.Instant? = null
    private var sessionStartProgress: Float = 0f
    private var sessionPausedAt: java.time.Instant? = null

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
        // Try to restore position from saved Readium locator first,
        // fall back to converting percentage → locator if no locator saved
        // (happens when progress was pulled from server without locator data)
        var initialLocator = localProgress?.locatorJson?.toLocator()
        if (initialLocator == null && localProgress != null && localProgress.percentage > 0f) {
            initialLocator = pub.locateProgression(localProgress.percentage.toDouble())
        }

        sessionStartTime = java.time.Instant.now()
        sessionStartProgress = localProgress?.percentage ?: 0f

        _uiState.value = ReaderUiState.Ready(
            publication = pub,
            initialLocator = initialLocator,
            book = loadedBook
        )

        pullRemoteProgressOnOpen(loadedBook, localProgress)

        // Show tap zone hint on first open of each book in Ember
        // locatorJson is null when progress came from server sync only (never opened locally)
        if (localProgress == null || localProgress.locatorJson == null) {
            _showTapZoneHint.value = true
        }
    }

    private suspend fun getSyncServer(): com.ember.reader.core.model.Server? {
        val loadedBook = book ?: return null
        val serverId = loadedBook.serverId ?: return null
        val syncFrequency = syncPreferencesRepository.syncFrequencyFlow.first()
        if (!syncFrequency.syncOnOpenClose) {
            Timber.d("Sync: open/close sync disabled (${syncFrequency.name})")
            return null
        }
        return serverRepository.getById(serverId)
    }

    private suspend fun pullRemoteProgressOnOpen(
        loadedBook: Book,
        localProgress: ReadingProgress?,
    ) {
        val server = getSyncServer() ?: return
        val localPercentage = localProgress?.percentage ?: 0f

        val remote = progressSyncManager.pullBestProgress(server, loadedBook) ?: return
        if (remote.progress.percentage > localPercentage + CONFLICT_THRESHOLD) {
            _syncConflict.value = SyncConflict(
                remotePercentage = remote.progress.percentage,
                localPercentage = localPercentage,
                remoteSource = remote.source,
                remoteProgress = remote.progress,
            )
        } else {
            Timber.d("Sync: remote not ahead enough to show conflict")
        }
    }

    fun acceptRemoteProgress() {
        val conflict = _syncConflict.value ?: return
        _syncConflict.value = null
        viewModelScope.launch {
            readingProgressRepository.applyRemoteProgress(conflict.remoteProgress)
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

    /** Returns true if bookmark was removed, false if a new one should be created (show dialog). */
    fun toggleBookmark(): Boolean {
        val locator = _currentLocator.value ?: return false
        val href = locator.href.toString()
        val hrefEscaped = href.replace("/", "\\/")
        val currentProg = locator.locations.progression ?: -1.0
        val existing = _bookmarks.value.find { bm ->
            val inSameChapter = bm.locatorJson.contains(href) || bm.locatorJson.contains(hrefEscaped)
            if (!inSameChapter) return@find false
            val bmLocator = bm.locatorJson.toLocator() ?: return@find false
            val bmProg = bmLocator.locations.progression ?: return@find false
            kotlin.math.abs(currentProg - bmProg) < 0.02
        }
        if (existing != null) {
            viewModelScope.launch { bookmarkRepository.deleteBookmark(existing.id) }
            return true
        }
        return false
    }

    fun addBookmark(title: String) {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(
                bookId = bookId,
                locatorJson = locator.toJsonString(),
                title = title.ifBlank { locator.title ?: "Bookmark" },
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(id) }
    }

    fun addHighlight(
        locatorJson: String,
        color: HighlightColor,
        annotation: String? = null,
        selectedText: String? = null
    ) {
        viewModelScope.launch {
            highlightRepository.addHighlight(bookId, locatorJson, color, annotation, selectedText)
        }
    }

    fun updateHighlight(highlight: Highlight, annotation: String?, color: HighlightColor) {
        viewModelScope.launch {
            highlightRepository.updateHighlight(highlight, annotation, color)
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { highlightRepository.deleteHighlight(id) }
    }

    fun updatePreferences(preferences: ReaderPreferences) {
        viewModelScope.launch { readerPreferencesRepository.updatePreferences(preferences) }
    }

    /** Save current progress immediately. Called on Activity pause (screen lock, app background). */
    fun saveCurrentProgress() {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch { saveProgress(locator) }
    }

    /**
     * Start a grace period before ending the reading session. Called on ON_PAUSE.
     * If the user returns within [PAUSE_GRACE_MS], the session continues seamlessly.
     */
    fun onSessionPause() {
        if (sessionPausedAt != null) return // already paused
        val startTime = sessionStartTime ?: return // no active session

        val pauseTime = java.time.Instant.now()
        sessionPausedAt = pauseTime

        // Delay session recording — gives the user a window to return without splitting the session
        sessionPauseJob?.cancel()
        sessionPauseJob = viewModelScope.launch {
            delay(PAUSE_GRACE_MS)
            // Grace period expired — end the session at the pause time
            val durationSeconds = java.time.Duration.between(startTime, pauseTime).seconds
            if (durationSeconds >= MIN_SESSION_SECONDS) {
                recordReadingSessionSegment(startTime, pauseTime)
            }
            sessionStartTime = null
        }
    }

    /** Resume the reading session or start a fresh one. Called on ON_RESUME. */
    fun onSessionResume() {
        if (sessionPausedAt == null) return // not paused (handles first ON_RESUME before any ON_PAUSE)

        val graceStillActive = sessionPauseJob?.isActive == true
        sessionPauseJob?.cancel()
        sessionPauseJob = null
        sessionPausedAt = null

        if (graceStillActive) {
            // Returned within grace period — session continues as if nothing happened
            return
        }

        if (sessionStartTime == null) {
            // Grace expired and session was recorded — start a fresh session
            sessionStartTime = java.time.Instant.now()
            sessionStartProgress = _currentLocator.value
                ?.locations?.totalProgression?.toFloat() ?: 0f
        }
    }

    private fun scheduleSaveProgress(locator: Locator) {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveProgress(locator)
        }
    }

    private suspend fun saveProgress(locator: Locator) {
        // For PDFs, use page-based progress: (currentPage + 1) / totalPages
        // totalProgression reports start-of-page which means last page never reaches 100%
        val percentage = if (book?.format == com.ember.reader.core.model.BookFormat.PDF) {
            val totalPages = publication?.metadata?.numberOfPages
            val totalProg = locator.locations.totalProgression
            when {
                totalProg != null && totalPages != null && totalPages > 0 -> {
                    // Derive page from totalProgression and convert to page-based %
                    val lastPageStart = (totalPages - 1).toFloat() / totalPages
                    val currentPage = if (totalProg.toFloat() >= lastPageStart - 0.01f) {
                        totalPages
                    } else {
                        kotlin.math.ceil(totalProg.toFloat() * totalPages)
                            .toInt().coerceIn(1, totalPages)
                    }
                    if (totalPages <= 1) 1f
                    else ((currentPage - 1).toFloat() / (totalPages - 1)).coerceIn(0f, 1f)
                }
                // Last resort
                else -> locator.toPercentage()
            }
        } else {
            locator.toPercentage()
        }

        readingProgressRepository.updateProgress(
            bookId = bookId,
            serverId = book?.serverId,
            percentage = percentage,
            locatorJson = locator.toJsonString()
        )
    }

    private suspend fun pushProgressOnClose() {
        val server = getSyncServer() ?: return
        val loadedBook = book ?: return
        progressSyncManager.pushProgress(server, loadedBook)
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel any pending grace timer — we'll record immediately below
        sessionPauseJob?.cancel()

        _currentLocator.value?.let { locator ->
            // Save progress to local DB — runs on IO, fast Room write
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                saveProgress(locator)
            }

            // Determine if there's an unrecorded session segment
            val startTime = sessionStartTime
            val endTime = when {
                startTime == null -> null // session already recorded (grace timer completed)
                sessionPausedAt != null -> sessionPausedAt // paused with pending grace timer, use pause time
                else -> java.time.Instant.now() // active session, use now (safety net)
            }

            // Fire-and-forget: push to server + record any remaining session
            // Uses GlobalScope intentionally — this work must outlive the ViewModel
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                pushProgressOnClose()
                if (startTime != null && endTime != null) {
                    recordReadingSessionSegment(startTime, endTime)
                }
            }
        }
        publication?.close()
    }

    private suspend fun recordReadingSessionSegment(
        startTime: java.time.Instant,
        endTime: java.time.Instant,
    ) {
        val loadedBook = book ?: return

        val durationSeconds = java.time.Duration.between(startTime, endTime).seconds
        if (durationSeconds < MIN_SESSION_SECONDS) return // Skip accidental opens / sub-threshold segments

        val endProgress = readingProgressRepository.getByBookId(bookId)?.percentage ?: 0f

        // Save locally (always)
        readingSessionRepository.saveSession(
            com.ember.reader.core.model.ReadingSession(
                bookId = bookId,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = durationSeconds,
                startProgress = sessionStartProgress,
                endProgress = endProgress
            )
        )
        Timber.d("Saved local session: ${durationSeconds}s, ${(sessionStartProgress * 100).roundToInt()}% → ${(endProgress * 100).roundToInt()}%")

        // Push to Grimmory (if available)
        val serverId = loadedBook.serverId ?: return
        val server = serverRepository.getById(serverId) ?: return
        if (!server.isGrimmory || !grimmoryTokenManager.isLoggedIn(server.id)) return
        val grimmoryBookId = loadedBook.grimmoryBookId ?: return

        val startPct = sessionStartProgress.toGrimmoryPercentage()
        val endPct = endProgress.toGrimmoryPercentage()
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
                    progressDelta = endPct - startPct
                )
            ).getOrThrow()
        }.onFailure {
            Timber.w(it, "Failed to push reading session to Grimmory")
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 5000L
        private const val CONFLICT_THRESHOLD = 0.01f
        private const val MIN_SESSION_SECONDS = 120L
        private const val PAUSE_GRACE_MS = 30_000L
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val publication: Publication,
        val initialLocator: Locator?,
        val book: Book
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

data class SyncConflict(
    val remotePercentage: Float,
    val localPercentage: Float,
    val remoteSource: String?,
    val remoteProgress: com.ember.reader.core.model.ReadingProgress,
)
