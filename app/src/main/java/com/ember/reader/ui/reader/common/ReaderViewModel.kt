package com.ember.reader.ui.reader.common
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.coroutine.ApplicationScope
import com.ember.reader.core.epub.EpubSpine
import com.ember.reader.core.fonts.CustomFont
import com.ember.reader.core.fonts.CustomFontRepository
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryReadingSessionRequest
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.toGrimmoryPercentage
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.readium.loadEpubSpine
import com.ember.reader.core.readium.toJsonString
import com.ember.reader.core.readium.toLocator
import com.ember.reader.core.readium.toPercentage
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookReaderPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.HighlightRepository
import com.ember.reader.core.repository.ReaderPreferencesRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.repository.SyncPreferencesRepository
import com.ember.reader.core.sync.BookmarkRepairer
import com.ember.reader.core.sync.CfiLocatorConverter
import com.ember.reader.core.sync.HighlightRepairer
import com.ember.reader.core.sync.ProgressSyncManager
import com.ember.reader.core.sync.RepairSummary
import com.ember.reader.core.sync.SourceOutcome
import com.ember.reader.ui.reader.epub.AutoScrollSpeed
import com.ember.reader.ui.reader.epub.ReadAloud
import com.ember.reader.ui.reader.pdf.locatorOpeningPdfPercentage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import timber.log.Timber

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookOpener: BookOpener,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readerPreferencesRepository: ReaderPreferencesRepository,
    private val bookReaderPreferencesRepository: BookReaderPreferencesRepository,
    private val serverRepository: ServerRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val readingSessionRepository: com.ember.reader.core.repository.ReadingSessionRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val progressSyncManager: ProgressSyncManager,
    private val highlightRepairer: HighlightRepairer,
    private val bookmarkRepairer: BookmarkRepairer,
    customFontRepository: CustomFontRepository,
    val dictionaryRepository: com.ember.reader.core.dictionary.DictionaryRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    /** Fonts the user added in Settings. They are declared to the reader when a book opens. */
    val customFonts: StateFlow<List<CustomFont>> = customFontRepository.fonts

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""
    private val streaming: Boolean = savedStateHandle.get<String>("mode") == "stream"
    val isStreaming: Boolean get() = streaming

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _chromeVisible = MutableStateFlow(false)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _syncConflict = MutableStateFlow<SyncConflict?>(null)
    val syncConflict: StateFlow<SyncConflict?> = _syncConflict.asStateFlow()

    private val _autoScrolling = MutableStateFlow(false)

    /** Whether the page is moving by itself. It only does with the bars hidden. */
    val autoScrolling: StateFlow<Boolean> = _autoScrolling.asStateFlow()

    /** How fast it moves, in CSS pixels a second. Kept for every book, not per book. */
    val autoScrollSpeed: StateFlow<Float> = appPreferencesRepository.autoScrollSpeedFlow
        .map { it ?: AutoScrollSpeed.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AutoScrollSpeed.DEFAULT)

    /** Steps the speed up or down; nothing happens at either end of the range. */
    fun changeAutoScrollSpeed(faster: Boolean) {
        val speed = AutoScrollSpeed.step(autoScrollSpeed.value, faster)
        viewModelScope.launch { appPreferencesRepository.updateAutoScrollSpeed(speed) }
    }

    fun startAutoScroll() {
        readAloud.stop()
        _chromeVisible.value = false
        _autoScrolling.value = true
    }

    /** Reads the book out with the device's speech engine. Kept here so a rotation does not stop it. */
    internal val readAloud = ReadAloud(
        scope = viewModelScope,
        loadSpeed = { appPreferencesRepository.readAloudSpeedFlow.first().toDouble() },
        saveSpeed = { appPreferencesRepository.updateReadAloudSpeed(it.toFloat()) }
    )

    /** Starts reading aloud from [from], the first thing on screen; the bars go and the page stops moving. */
    fun startReadAloud(application: Application, from: Locator?) {
        val publication = publication ?: return
        stopAutoScroll()
        _chromeVisible.value = false
        readAloud.start(application, publication, from)
    }

    /** Stops the page; [showChrome] brings the bars back, where it can be started again. */
    fun stopAutoScroll(showChrome: Boolean = false) {
        if (!_autoScrolling.value) return
        _autoScrolling.value = false
        if (showChrome) _chromeVisible.value = true
    }

    // Per-book overrides take priority over global defaults. Falls back to the
    // global flow when bookId is unset (defensive — shouldn't happen in practice).
    val preferences: StateFlow<ReaderPreferences> =
        if (bookId.isNotEmpty()) {
            bookReaderPreferencesRepository.observeEffective(bookId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())
        } else {
            readerPreferencesRepository.preferencesFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())
        }

    val hasBookOverride: StateFlow<Boolean> =
        bookReaderPreferencesRepository.observeHasOverride(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
    private val _pendingNavigation = MutableStateFlow<PendingNavigation?>(null)
    val pendingNavigation: StateFlow<PendingNavigation?> = _pendingNavigation.asStateFlow()

    fun onNavigationHandled() {
        _pendingNavigation.value = null
    }

    private var publication: Publication? = null
    private var book: Book? = null
    private var progressSaveJob: Job? = null
    private var sessionPauseJob: Job? = null
    private var sessionStartTime: java.time.Instant? = null
    private var sessionStartProgress: Float = 0f
    private var sessionStartLocation: String? = null
    private var sessionPausedAt: java.time.Instant? = null

    // A pref edit triggers a Readium reflow, which emits a new locator whose
    // page-start progression differs slightly from the pre-reflow one. Skip
    // persisting any locator seen inside this window so the saved position
    // stays anchored to the user's last real navigation.
    private var suppressSaveUntil: Long = 0L

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

        val pub = if (streaming) {
            openStreamingPublication(loadedBook) ?: return
        } else {
            val localPath = loadedBook.localPath
            if (localPath == null) {
                _uiState.value = ReaderUiState.Error("Book not downloaded")
                return
            }
            bookOpener.open(File(localPath)).getOrElse { error ->
                _uiState.value = ReaderUiState.Error("Failed to open: ${error.message}")
                return
            }
        }
        publication = pub
        // Read alongside the progress and preference lookups below, and parsed off the main thread.
        val spine = viewModelScope.async(Dispatchers.Default) { pub.loadEpubSpine() }

        val localProgress = readingProgressRepository.getByBookId(bookId)
        // Try to restore position from saved Readium locator first,
        // fall back to converting percentage → locator if no locator saved
        // (happens when progress was pulled from server without locator data)
        var initialLocator = localProgress?.locatorJson?.toLocator()
        if (initialLocator == null && localProgress != null && localProgress.percentage > 0f) {
            // A PDF's percentage counts pages its own way, and its navigator opens the page
            // before the position it is given; locateProgression would land a page early.
            initialLocator = if (loadedBook.format == BookFormat.PDF) {
                pub.locatorOpeningPdfPercentage(localProgress.percentage)
            } else {
                pub.locateProgression(localProgress.percentage.toDouble())
            }
        }

        // Asked to open at a bookmark or highlight: that comes before where the book was
        // left. One synced from Grimmory and not yet placed only knows its chapter; the
        // reader is sent on to its CFI once the chapter is up.
        val targetLocatorJson = targetLocatorJson()
        targetLocatorJson?.toLocator(pub, spine.await())?.let { initialLocator = it }

        // Wait for the real (DataStore-backed) preferences before handing the
        // fragment to the screen. If we skipped this, the fragment would be
        // built with defaults, seek to initialLocator, render, and only then
        // reflow when the actual prefs arrive — and the post-reflow locator
        // emission would overwrite our saved progress a few pages back.
        val loadedPreferences = if (bookId.isNotEmpty()) {
            bookReaderPreferencesRepository.observeEffective(bookId).first()
        } else {
            readerPreferencesRepository.preferencesFlow.first()
        }

        sessionStartTime = java.time.Instant.now()
        sessionStartProgress = localProgress?.percentage ?: 0f
        sessionStartLocation = null

        _uiState.value = ReaderUiState.Ready(
            publication = pub,
            initialLocator = initialLocator,
            book = loadedBook,
            streaming = streaming,
            preferences = loadedPreferences,
            spine = spine.await(),
        )

        targetLocatorJson?.let(CfiLocatorConverter::unplacedCfi)?.let {
            _pendingNavigation.value = PendingNavigation(cfi = it)
        }
        savedStateHandle[KEY_TARGET_OPENED] = true

        pullRemoteProgressOnOpen(loadedBook, localProgress)

        // Show tap zone hint on first open of each book in Ember
        // locatorJson is null when progress came from server sync only (never opened locally)
        if (localProgress == null || localProgress.locatorJson == null) {
            _showTapZoneHint.value = true
        }
    }

    /**
     * The stored locator of the place this reader was asked to open at, if it was asked and
     * the place still exists. Asked only once: a reader rebuilt later, after the system
     * closed the app behind it, opens where reading had got to.
     */
    private suspend fun targetLocatorJson(): String? {
        if (savedStateHandle.get<Boolean>(KEY_TARGET_OPENED) == true) return null
        val target = ReaderTarget.fromArguments(
            mark = savedStateHandle.get<String>("mark"),
            cfi = savedStateHandle.get<String>("cfi")
        )
        return when (target) {
            is ReaderTarget.Bookmark ->
                bookmarkRepository.observeByBookId(bookId).first().find { it.id == target.id }?.locatorJson
            is ReaderTarget.Highlight ->
                highlightRepository.observeByBookId(bookId).first().find { it.id == target.id }?.locatorJson
            is ReaderTarget.Cfi -> CfiLocatorConverter.buildLocatorJson(target.cfi)
            null -> null
        }
    }

    private suspend fun openStreamingPublication(loadedBook: Book): Publication? {
        val serverId = loadedBook.serverId
        val grimmoryBookId = loadedBook.grimmoryBookId
        if (serverId == null || grimmoryBookId == null) {
            _uiState.value = ReaderUiState.Error("Streaming requires a Grimmory book")
            return null
        }
        val server = serverRepository.getById(serverId)
        if (server == null || !server.isGrimmory) {
            _uiState.value = ReaderUiState.Error("Streaming requires a Grimmory server")
            return null
        }
        if (!grimmoryTokenManager.isLoggedIn(serverId)) {
            _uiState.value = ReaderUiState.Error("Sign in to Grimmory to stream this book")
            return null
        }
        Timber.d("Streaming: opening %s from server %d", loadedBook.title, serverId)
        return bookOpener.openStreaming(server, grimmoryBookId).getOrElse { error ->
            _uiState.value = ReaderUiState.Error("Streaming failed: ${error.message}")
            null
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
        localProgress: ReadingProgress?
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
                remotePdfPage = remote.pdfPage
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
        // The percentage works whatever the remote position's format (Locator, XPointer,
        // CFI). A real CFI, when the remote has one, lands on the exact text.
        _pendingNavigation.value = PendingNavigation(
            percentage = conflict.remotePercentage,
            cfi = conflict.remoteProgress.cfi,
            pdfPage = conflict.remotePdfPage,
        )
    }

    fun dismissSyncConflict() {
        _syncConflict.value = null
    }

    /** The real CFI the reader computed for a position, kept until that position is saved. */
    private var positionCfi: Pair<Locator, String>? = null

    /** Reported by the EPUB reader once a page has settled; null when it has no CFI. */
    fun onPositionCfi(locator: Locator, cfi: String?) {
        positionCfi = cfi?.let { locator to it }
        noteSessionStartLocation()
    }

    fun onLocatorChanged(locator: Locator) {
        _currentLocator.value = locator
        noteSessionStartLocation()
        scheduleSaveProgress(locator)
    }

    private fun currentSessionLocation(): String? =
        sessionLocation(book?.format, _currentLocator.value, positionCfi?.second)

    /** The first location known after a session starts is where it started. */
    private fun noteSessionStartLocation() {
        if (sessionStartTime != null && sessionStartLocation == null) {
            sessionStartLocation = currentSessionLocation()
        }
    }

    fun toggleChrome() {
        _autoScrolling.value = false
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

    /** @param cfi the real CFI of the page's first text, when the reader could compute one. */
    fun addBookmark(title: String, cfi: String? = null) {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(
                bookId = bookId,
                locatorJson = locator.toJsonString(),
                title = title.ifBlank { locator.title ?: "Bookmark" },
                cfi = cfi,
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepository.deleteBookmark(id) }
    }

    /** Stores where synced highlights and bookmarks were found in the book. */
    fun placeSyncedPositions(highlights: Map<Long, String>, bookmarks: Map<Long, String>) {
        if (highlights.isEmpty() && bookmarks.isEmpty()) return
        viewModelScope.launch {
            highlightRepository.placeHighlights(highlights)
            bookmarkRepository.placeBookmarks(bookmarks)
        }
    }

    private val _highlightRepair = MutableStateFlow<CfiRepairState>(CfiRepairState.Idle)
    val highlightRepair: StateFlow<CfiRepairState> = _highlightRepair.asStateFlow()

    private val _bookmarkRepair = MutableStateFlow<CfiRepairState>(CfiRepairState.Idle)
    val bookmarkRepair: StateFlow<CfiRepairState> = _bookmarkRepair.asStateFlow()

    /**
     * Gives real CFIs to this book's highlights that lack one, replacing on the server
     * those that were pushed without one. Started by the user: it rewrites server data.
     *
     * @param computeCfis places highlights in the book by their text; see `OffscreenCfiResolver`.
     */
    fun repairHighlights(computeCfis: suspend (List<Highlight>) -> Map<Long, String>) {
        runRepair(_highlightRepair, syncEnabled = { appPreferencesRepository.getSyncHighlights() }) { server ->
            highlightRepairer.repair(server, bookId, book?.grimmoryBookId, computeCfis)
        }
    }

    /**
     * Gives real CFIs to this book's bookmarks that lack one, updating on the server those
     * that were pushed without one. Started by the user: it rewrites server data.
     *
     * @param computeCfis places bookmarks in the book by how far into their chapter they
     * are; see `OffscreenCfiResolver`.
     */
    fun repairBookmarks(computeCfis: suspend (List<Bookmark>) -> Map<Long, String>) {
        runRepair(_bookmarkRepair, syncEnabled = { appPreferencesRepository.getSyncBookmarks() }) { server ->
            bookmarkRepairer.repair(server, bookId, book?.grimmoryBookId, computeCfis)
        }
    }

    /** @param repair is given the book's server, or null when the server is to be left alone. */
    private fun runRepair(
        state: MutableStateFlow<CfiRepairState>,
        syncEnabled: suspend () -> Boolean,
        repair: suspend (server: Server?) -> Result<RepairSummary>,
    ) {
        if (state.value == CfiRepairState.Running) return
        state.value = CfiRepairState.Running
        viewModelScope.launch {
            // With that kind of sync off, the server is left alone and CFIs are only stored.
            val server = book?.serverId?.takeIf { syncEnabled() }?.let { serverRepository.getById(it) }
            state.value = repair(server).fold(
                onSuccess = { CfiRepairState.Done(it.repaired, it.unresolved) },
                onFailure = { CfiRepairState.Failed },
            )
        }
    }

    fun addHighlight(
        locatorJson: String,
        color: HighlightColor,
        annotation: String? = null,
        selectedText: String? = null,
        cfi: String? = null,
    ) {
        viewModelScope.launch {
            highlightRepository.addHighlight(bookId, locatorJson, color, annotation, selectedText, cfi)
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
        suppressSaveUntil = System.currentTimeMillis() + PREF_REFLOW_SUPPRESS_MS
        viewModelScope.launch {
            if (bookId.isNotEmpty()) {
                // Forks the book away from global defaults — first edit creates
                // an override row, subsequent edits replace it.
                bookReaderPreferencesRepository.saveOverride(bookId, preferences)
            } else {
                readerPreferencesRepository.updatePreferences(preferences)
            }
        }
    }

    /** Removes the per-book override so the book inherits global defaults again. */
    fun resetPreferencesToDefaults() {
        if (bookId.isEmpty()) return
        viewModelScope.launch { bookReaderPreferencesRepository.clearOverride(bookId) }
    }

    /** Save current progress immediately. Called on Activity pause (screen lock, app background). */
    fun saveCurrentProgress() {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch { saveProgress(locator) }
    }

    /**
     * Persist [locator] on the application scope so the write outlives the
     * ViewModel if the user is popping back. Use this when the caller can read
     * the navigator fragment directly — the fragment's currentLocator can hold
     * an emission our Compose collector hasn't processed yet.
     */
    fun saveProgressLatest(locator: Locator) {
        progressSaveJob?.cancel()
        _currentLocator.value = locator
        applicationScope.launch { saveProgress(locator) }
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
            sessionStartLocation = currentSessionLocation()
        }
    }

    private fun scheduleSaveProgress(locator: Locator) {
        if (System.currentTimeMillis() < suppressSaveUntil) return
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
                    if (totalPages <= 1) {
                        1f
                    } else {
                        ((currentPage - 1).toFloat() / (totalPages - 1)).coerceIn(0f, 1f)
                    }
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
            locatorJson = locator.toJsonString(),
            // Only when it was computed for this very position; a stale CFI would send
            // other clients somewhere else.
            cfi = positionCfi?.takeIf { it.first == locator }?.second,
        )
    }

    private suspend fun pushProgressOnClose() {
        val server = getSyncServer() ?: return
        val loadedBook = book ?: return
        val result = progressSyncManager.pushProgress(server, loadedBook)
        // Failures show up via SyncStatusBanner on next library/browse visit
        // (reportStatus downgrades the server status on any failed channel).
        // The reader screen is already being torn down here, so a Snackbar
        // emit would be dropped — log loudly instead so the on-close push
        // outcome is auditable in logcat.
        if (result.anyFailed) {
            Timber.w(
                result.firstActionableError(),
                "Sync push on close for '${loadedBook.title}': kosync=${describe(result.kosync)} grimmory=${describe(result.grimmory)}"
            )
        } else {
            Timber.i(
                "Sync push on close for '${loadedBook.title}': kosync=${describe(result.kosync)} grimmory=${describe(result.grimmory)}"
            )
        }
    }

    private fun describe(outcome: SourceOutcome<Unit>): String = when (outcome) {
        is SourceOutcome.Ok -> "OK"
        is SourceOutcome.Skipped -> "skipped(${outcome.reason})"
        is SourceOutcome.Failure -> "failed(${outcome.error::class.simpleName})"
    }

    override fun onCleared() {
        super.onCleared()
        readAloud.stop()
        // Cancel any pending grace timer — we'll record immediately below
        sessionPauseJob?.cancel()

        _currentLocator.value?.let { locator ->
            // Determine if there's an unrecorded session segment
            val startTime = sessionStartTime
            val endTime = when {
                startTime == null -> null // session already recorded (grace timer completed)
                sessionPausedAt != null -> sessionPausedAt // paused with pending grace timer, use pause time
                else -> java.time.Instant.now() // active session, use now (safety net)
            }

            // All close-time work runs on the application scope so it outlives the
            // ViewModel without blocking the main thread or leaking via GlobalScope.
            applicationScope.launch {
                saveProgress(locator)
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
        endTime: java.time.Instant
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
                    progressDelta = endPct - startPct,
                    startLocation = sessionStartLocation,
                    endLocation = currentSessionLocation()
                )
            ).getOrThrow()
        }.onFailure {
            Timber.w(it, "Failed to push reading session to Grimmory")
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 5000L
        private const val CONFLICT_THRESHOLD = 0.01f
        private const val MIN_SESSION_SECONDS = 30L
        private const val PAUSE_GRACE_MS = 30_000L
        // Covers Readium's reflow + the post-reflow currentLocator emission
        // that follows submitPreferences.
        private const val PREF_REFLOW_SUPPRESS_MS = 1500L

        /** Set once the reader has opened at the place it was asked to. */
        private const val KEY_TARGET_OPENED = "targetOpened"
    }
}

/**
 * A position in the form Grimmory's own readers record on a reading session: the page number
 * for a PDF, the real CFI for an EPUB. Null when there is neither, or when the CFI is longer
 * than the server's column, which would make it reject the whole session.
 */
internal fun sessionLocation(
    format: com.ember.reader.core.model.BookFormat?,
    locator: Locator?,
    cfi: String?
): String? = if (format == com.ember.reader.core.model.BookFormat.PDF) {
    locator?.locations?.position?.toString()
} else {
    cfi?.takeIf { it.length <= SESSION_LOCATION_MAX_LENGTH }
}

private const val SESSION_LOCATION_MAX_LENGTH = 500

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val publication: Publication,
        val initialLocator: Locator?,
        val book: Book,
        val streaming: Boolean = false,
        val preferences: ReaderPreferences = ReaderPreferences(),
        /** The EPUB's spine as its package document lists it; null for other formats. */
        val spine: EpubSpine? = null,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

/**
 * A position for the reader to open: one accepted from another device, or a synced
 * bookmark or highlight the book was opened at.
 *
 * @param percentage how far through the book it is, when that is known.
 * @param cfi its real EPUB CFI when there is one; the reader falls back to
 * [percentage] when it is null or cannot be opened.
 * @param pdfPage its page, counted from 1, when the remote stored one for a PDF.
 */
data class PendingNavigation(
    val percentage: Float? = null,
    val cfi: String? = null,
    val pdfPage: Int? = null,
)

data class SyncConflict(
    val remotePercentage: Float,
    val localPercentage: Float,
    val remoteSource: String?,
    val remoteProgress: com.ember.reader.core.model.ReadingProgress,
    val remotePdfPage: Int? = null
)
