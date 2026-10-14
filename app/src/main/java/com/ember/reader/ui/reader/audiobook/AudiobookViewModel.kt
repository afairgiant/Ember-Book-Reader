package com.ember.reader.ui.reader.audiobook

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ember.reader.core.coroutine.ApplicationScope
import com.ember.reader.core.grimmory.AudiobookChapter
import com.ember.reader.core.grimmory.AudiobookInfo
import com.ember.reader.core.grimmory.AudiobookTrack
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.AudioPosition
import com.ember.reader.core.model.Book
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.DEFAULT_AUDIOBOOK_SKIP_SECONDS
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.BookmarkSyncManager
import com.ember.reader.ui.reader.common.ReaderTarget
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface AudiobookUiState {
    data object Loading : AudiobookUiState
    data class Ready(
        val book: Book,
        val info: AudiobookInfo?
    ) : AudiobookUiState
    data class Error(val message: String) : AudiobookUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudiobookViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val bookmarkSyncManager: BookmarkSyncManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    // The book can change under the player: when one ends, the next in Up Next takes over.
    private val bookIdFlow = MutableStateFlow(savedStateHandle.get<String>("bookId") ?: "")
    private val bookId: String get() = bookIdFlow.value

    private val _uiState = MutableStateFlow<AudiobookUiState>(AudiobookUiState.Loading)
    val uiState: StateFlow<AudiobookUiState> = _uiState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    /** How far the skip buttons jump, in seconds. Kept for every book. */
    val skipBackSeconds: StateFlow<Int> = appPreferencesRepository.audiobookSkipBackSecondsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_AUDIOBOOK_SKIP_SECONDS)
    val skipForwardSeconds: StateFlow<Int> = appPreferencesRepository.audiobookSkipForwardSecondsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_AUDIOBOOK_SKIP_SECONDS)

    private val _sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)

    /** The sleep timer as the playback service holds it. */
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()

    private val _sleepTimeLeftMs = MutableStateFlow<Long?>(null)

    /** How long a timed sleep timer has left; null when there is none. */
    val sleepTimeLeftMs: StateFlow<Long?> = _sleepTimeLeftMs.asStateFlow()

    /** The book's bookmarks, in the order they come in the book. */
    val bookmarks: StateFlow<List<AudioBookmark>> = bookIdFlow
        .flatMapLatest { bookmarkRepository.observeByBookId(it) }
        .map(::audioBookmarksOf)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The audiobooks queued in Up Next, in order. One that is no longer in the library is left out. */
    val upNext: StateFlow<List<Book>> = appPreferencesRepository.audiobookUpNextFlow
        .map { ids ->
            val books = bookRepository.getByIds(ids.toSet()).associateBy { it.id }
            ids.mapNotNull(books::get)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _nextAudiobook = MutableStateFlow<NextAudiobook?>(null)

    /** What will play when this book ends; null when playback will simply stop. */
    val nextAudiobook: StateFlow<NextAudiobook?> = _nextAudiobook.asStateFlow()

    /** Set while one book is being swapped for the next, so the swap is not started twice. */
    private var advancing = false

    private val _chapters = MutableStateFlow<List<AudiobookChapter>>(emptyList())
    val chapters: StateFlow<List<AudiobookChapter>> = _chapters.asStateFlow()

    private val _tracks = MutableStateFlow<List<AudiobookTrack>>(emptyList())
    val tracks: StateFlow<List<AudiobookTrack>> = _tracks.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private var controller: MediaController? = null
    private var book: Book? = null
    private var audiobookInfo: AudiobookInfo? = null
    private var sessionStartTime: java.time.Instant? = null
    private var sessionStartProgress: Float = 0f

    init {
        viewModelScope.launch { loadBook() }
        // Keep "what plays next" current as the queue or the book changes.
        viewModelScope.launch {
            combine(upNext, bookIdFlow, _uiState) { queue, _, _ -> queue }.collect { queue ->
                _nextAudiobook.value = book?.let { chooseNext(it, queue) }
            }
        }
    }

    private suspend fun chooseNext(current: Book, queue: List<Book>): NextAudiobook? {
        val seriesMates = current.serverId
            ?.takeIf { current.series != null }
            ?.let { bookRepository.observeByServer(it).first() }
            .orEmpty()
        val progress = seriesMates.associate { it.id to readingProgressRepository.getByBookId(it.id)?.percentage }
        return nextAudiobook(current, queue, seriesMates, progress::get)
    }

    fun removeFromUpNext(book: Book) {
        viewModelScope.launch { appPreferencesRepository.updateAudiobookUpNext { it - book.id } }
    }

    fun moveUpInUpNext(book: Book) {
        viewModelScope.launch { appPreferencesRepository.updateAudiobookUpNext { movedUp(it, book.id) } }
    }

    /** Leaves the current book where it is and starts [book] from Up Next now. */
    fun playNow(book: Book) {
        viewModelScope.launch {
            saveProgress()
            switchTo(book)
        }
    }

    /** The book has played to its end: mark it finished and start what is next, if anything is. */
    private fun onBookEnded() {
        if (advancing) return
        advancing = true
        viewModelScope.launch {
            try {
                readingProgressRepository.updateProgress(
                    bookId = bookId,
                    serverId = book?.serverId,
                    percentage = 1f,
                    locatorJson = null
                )
                val next = book?.let { chooseNext(it, upNext.value) } ?: return@launch
                Timber.d("Audiobook: '%s' ended, starting '%s'", book?.title, next.book.title)
                switchTo(next.book)
            } finally {
                advancing = false
            }
        }
    }

    private suspend fun switchTo(next: Book) {
        appPreferencesRepository.updateAudiobookUpNext { it - next.id }
        savedStateHandle["bookId"] = next.id
        bookIdFlow.value = next.id
        _chapters.value = emptyList()
        _tracks.value = emptyList()
        _currentTrackIndex.value = 0
        loadBook(autoPlay = true)
    }

    private suspend fun loadBook(autoPlay: Boolean = false) {
        val loadedBook = bookRepository.getById(bookId)
        if (loadedBook == null) {
            _uiState.value = AudiobookUiState.Error("Book not found")
            return
        }
        book = loadedBook
        syncBookmarks()

        val serverId = loadedBook.serverId
        val server = serverId?.let { serverRepository.getById(it) }
        val grimmoryBookId = loadedBook.grimmoryBookId
        Timber.d("Audiobook: loading book=%s grimmoryId=%s serverId=%s isDownloaded=%s", loadedBook.id, grimmoryBookId, serverId, loadedBook.isDownloaded)

        // Fetch audiobook info from Grimmory
        var info: AudiobookInfo? = null
        if (server != null && server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            val result = grimmoryClient.getAudiobookInfo(server.url, server.id, grimmoryBookId)
            result.onSuccess { fetchedInfo ->
                info = fetchedInfo
                Timber.d("Audiobook: info fetched folderBased=%s tracks=%d chapters=%d", fetchedInfo.folderBased, fetchedInfo.tracks?.size ?: 0, fetchedInfo.chapters?.size ?: 0)
                fetchedInfo.chapters?.let { _chapters.value = it }
                fetchedInfo.tracks?.let { _tracks.value = it }
            }.onFailure { error ->
                Timber.e(error, "Audiobook: failed to fetch audiobook info")
            }
        } else {
            Timber.w(
                "Audiobook: skipping info fetch server=%s isGrimmory=%s loggedIn=%s grimmoryBookId=%s", server?.id, server?.isGrimmory,
                server?.let {
                    grimmoryTokenManager.isLoggedIn(it.id)
                },
                grimmoryBookId
            )
        }

        audiobookInfo = info
        _uiState.value = AudiobookUiState.Ready(book = loadedBook, info = info)

        sessionStartTime = java.time.Instant.now()
        val localProgress = readingProgressRepository.getByBookId(bookId)
        sessionStartProgress = localProgress?.percentage ?: 0f

        // Connect to the playback service the first time; after that it only needs the new book.
        val ctrl = controller
        if (ctrl == null) {
            connectToService(loadedBook, server, info)
        } else {
            startBook(ctrl, loadedBook, server, info, autoPlay)
        }
    }

    private fun connectToService(
        book: Book,
        server: com.ember.reader.core.model.Server?,
        info: AudiobookInfo?
    ) {
        val sessionToken =
            SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    onSleepTimerChanged(SleepTimer.fromBundle(extras))
                }
            })
            .buildAsync()

        controllerFuture.addListener({
            val ctrl = controllerFuture.get()
            controller = ctrl
            // The service may already be running a timer set before this screen was opened.
            onSleepTimerChanged(SleepTimer.fromBundle(ctrl.sessionExtras))

            // Set up player listener
            ctrl.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = ctrl.duration.coerceAtLeast(0)
                    }
                    if (playbackState == Player.STATE_ENDED) onBookEnded()
                }
                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Audiobook: ExoPlayer error code=%d mediaItem=%d/%d", error.errorCode, ctrl.currentMediaItemIndex, ctrl.mediaItemCount)
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentTrackIndex.value = ctrl.currentMediaItemIndex
                    Timber.d("Audiobook: track transition to %d/%d reason=%d title=%s", ctrl.currentMediaItemIndex, ctrl.mediaItemCount, reason, mediaItem?.mediaMetadata?.title)
                }
            })

            viewModelScope.launch {
                startBook(ctrl, book, server, info, autoPlay = false)
                // Start position update loop
                startPositionUpdates()
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Hands [book] to the player. With [autoPlay] it starts by itself, as it does when it
     * follows a book that has just ended; a book already heard to its end then starts over.
     */
    private suspend fun startBook(
        ctrl: MediaController,
        book: Book,
        server: com.ember.reader.core.model.Server?,
        info: AudiobookInfo?,
        autoPlay: Boolean
    ) {
        val mediaItems = buildMediaItems(book, server, info)
        if (mediaItems.isEmpty()) {
            Timber.e("Audiobook: no media items could be built")
            // Leave nothing of the book before in the player: its position would otherwise
            // be saved as this book's.
            ctrl.clearMediaItems()
            return
        }
        Timber.d("Audiobook: setting %d media items", mediaItems.size)
        ctrl.setMediaItems(mediaItems)
        ctrl.prepare()
        val heard = readingProgressRepository.getByBookId(book.id)?.percentage ?: 0f
        if (!(autoPlay && heard >= FINISHED_PROGRESS)) restorePosition(ctrl)
        seekToRequestedBookmark(ctrl)
        val speed = appPreferencesRepository.audiobookSpeedFlow.first()
        _playbackSpeed.value = speed
        ctrl.setPlaybackSpeed(speed)
        if (autoPlay) ctrl.play()
    }

    private suspend fun buildMediaItems(
        book: Book,
        server: com.ember.reader.core.model.Server?,
        info: AudiobookInfo?
    ): List<MediaItem> {
        // Build authenticated artwork URI for media notification
        val artworkUri = book.coverUrl?.let { rawUrl ->
            if (server != null && server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
                val token = grimmoryTokenManager.getAccessToken(server.id)
                if (token != null) Uri.parse("$rawUrl?token=$token") else Uri.parse(rawUrl)
            } else {
                Uri.parse(rawUrl)
            }
        }
        // Downloaded folder-based audiobook: play from local track files
        val localPath = book.localPath
        if (book.isDownloaded && localPath != null) {
            val localDir = File(localPath)
            if (localDir.isDirectory) {
                val trackFiles = localDir.listFiles()
                    ?.filter { it.isFile && it.length() > 0 }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                if (trackFiles.isNotEmpty()) {
                    Timber.d("Audiobook: playing %d local track files from %s", trackFiles.size, localDir.name)
                    return trackFiles.map { file ->
                        MediaItem.Builder()
                            .setUri(Uri.fromFile(file))
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(file.nameWithoutExtension)
                                    .setArtist(book.author)
                                    .setAlbumTitle(book.title)
                                    .setArtworkUri(artworkUri)
                                    .build()
                            )
                            .build()
                    }
                }
            }
            // Single downloaded file
            Timber.d("Audiobook: playing single local file %s", localPath)
            return listOf(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(localDir))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(book.title)
                            .setArtist(book.author)
                            .setArtworkUri(artworkUri)
                            .build()
                    )
                    .build()
            )
        }

        // Folder-based streaming: build per-track URLs
        val tracks = info?.tracks
        if (info?.folderBased == true && !tracks.isNullOrEmpty() && server != null) {
            Timber.d("Audiobook: building %d track stream URLs", tracks.size)
            return tracks.map { track ->
                val trackUrl = grimmoryClient.audiobookStreamUrl(
                    server.url, server.id, book.grimmoryBookId!!, track.index
                )
                Timber.d("Audiobook: track %d url=%s", track.index, trackUrl?.take(80))
                MediaItem.Builder()
                    .setUri(trackUrl ?: "")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title ?: track.fileName)
                            .setArtist(book.author)
                            .setArtworkUri(artworkUri)
                            .build()
                    )
                    .build()
            }
        }

        // Single-file streaming
        val grimmoryBookId = book.grimmoryBookId ?: return emptyList()
        if (server == null || !server.isGrimmory) return emptyList()
        val token = grimmoryTokenManager.getAccessToken(server.id) ?: return emptyList()
        val origin = com.ember.reader.core.network.serverOrigin(server.url)
        val streamUrl = "$origin/api/v1/audiobooks/$grimmoryBookId/stream?token=$token"
        Timber.d("Audiobook: single-stream URL (not folder-based)")
        return listOf(
            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(book.title)
                        .setArtist(book.author)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        )
    }

    private suspend fun restorePosition(ctrl: MediaController) {
        val progress = readingProgressRepository.getByBookId(bookId) ?: return
        // Try to restore from locatorJson which stores audio position
        val locator = progress.locatorJson
        if (locator != null && locator.contains("positionMs")) {
            runCatching {
                val json = org.json.JSONObject(locator)
                val positionMs = json.optLong("positionMs", 0)
                val trackIndex = json.optInt("trackIndex", 0)
                if (ctrl.mediaItemCount > 1) {
                    ctrl.seekTo(trackIndex, positionMs)
                } else {
                    ctrl.seekTo(positionMs)
                }
            }
        } else if (progress.percentage > 0f) {
            // Fall back to percentage
            val duration = ctrl.duration
            if (duration > 0) {
                ctrl.seekTo((duration * progress.percentage).toLong())
            }
        }
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            var saveCounter = 0
            while (isActive) {
                val ctrl = controller
                if (ctrl != null && ctrl.isConnected) {
                    _currentPositionMs.value = ctrl.currentPosition.coerceAtLeast(0)
                    _durationMs.value = ctrl.duration.coerceAtLeast(0)
                }
                updateSleepTimeLeft()
                // Save progress every 30 seconds
                saveCounter++
                if (saveCounter >= 30) {
                    saveCounter = 0
                    saveProgress()
                }
                delay(1000)
            }
        }
    }

    fun play() {
        controller?.play()
    }
    fun pause() {
        controller?.pause()
    }
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun skipForward() {
        val ctrl = controller ?: return
        ctrl.seekTo((ctrl.currentPosition + skipForwardSeconds.value * 1000L).coerceAtMost(ctrl.duration))
    }

    fun skipBackward() {
        val ctrl = controller ?: return
        ctrl.seekTo((ctrl.currentPosition - skipBackSeconds.value * 1000L).coerceAtLeast(0))
    }

    fun setSkipBackSeconds(seconds: Int) {
        viewModelScope.launch { appPreferencesRepository.updateAudiobookSkipBackSeconds(seconds) }
    }

    fun setSkipForwardSeconds(seconds: Int) {
        viewModelScope.launch { appPreferencesRepository.updateAudiobookSkipForwardSeconds(seconds) }
    }

    /** Stops playback after [minutes], fading out at the end. */
    fun setSleepTimerMinutes(minutes: Int) {
        sendSleepTimer(SleepTimer.Timed(SystemClock.elapsedRealtime() + minutes * 60_000L))
    }

    /**
     * Stops playback at the end of the chapter now playing. Without chapters, or in the
     * last one, that is the end of the track now playing.
     */
    fun setSleepTimerEndOfChapter() {
        val ctrl = controller ?: return
        val nextChapterStart = _chapters.value.firstOrNull { it.startTimeMs > ctrl.currentPosition }?.startTimeMs
        sendSleepTimer(SleepTimer.EndOfChapter(ctrl.currentMediaItemIndex, nextChapterStart))
    }

    fun cancelSleepTimer() = sendSleepTimer(SleepTimer.Off)

    private fun sendSleepTimer(timer: SleepTimer) {
        controller?.sendCustomCommand(SleepTimer.SET_COMMAND, timer.toBundle())
    }

    private fun onSleepTimerChanged(timer: SleepTimer) {
        _sleepTimer.value = timer
        updateSleepTimeLeft()
    }

    private fun updateSleepTimeLeft() {
        val timed = _sleepTimer.value as? SleepTimer.Timed
        _sleepTimeLeftMs.value = timed?.let { (it.endsAtElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
    }

    fun seekToChapter(chapter: AudiobookChapter) {
        controller?.seekTo(chapter.startTimeMs)
    }

    /** Bookmarks where playback is now, named after the track or chapter and the time. */
    fun addBookmark() {
        val ctrl = controller ?: return
        val position = AudioPosition(
            positionMs = ctrl.currentPosition.coerceAtLeast(0),
            // A book that is one file has no track to name.
            trackIndex = ctrl.currentMediaItemIndex.takeIf { ctrl.mediaItemCount > 1 }
        )
        val trackIndex = position.trackIndex
        val place = if (trackIndex != null) {
            _tracks.value.getOrNull(trackIndex)?.title
        } else {
            _chapters.value.lastOrNull { it.startTimeMs <= position.positionMs }?.title
        }
        val title = audioBookmarkTitle(place, position.positionMs)
        viewModelScope.launch {
            // Grimmory keeps one bookmark every few seconds; nor is a second one here any use.
            if (bookmarks.value.none { it.position.isNear(position) }) {
                bookmarkRepository.addBookmark(bookId, position.toLocatorJson(), title)
                syncBookmarks()
            }
        }
    }

    fun seekToBookmark(bookmark: AudioBookmark) {
        val ctrl = controller ?: return
        seekTo(ctrl, bookmark.position)
    }

    fun deleteBookmark(bookmark: AudioBookmark) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark.id)
            syncBookmarks()
        }
    }

    private fun seekTo(ctrl: MediaController, position: AudioPosition) {
        val trackIndex = position.trackIndex
        if (trackIndex != null && trackIndex in 0 until ctrl.mediaItemCount) {
            ctrl.seekTo(trackIndex, position.positionMs)
        } else {
            ctrl.seekTo(position.positionMs)
        }
    }

    /** Opens at the bookmark the player was asked for, such as one tapped in Notes; once only. */
    private suspend fun seekToRequestedBookmark(ctrl: MediaController) {
        if (savedStateHandle.get<Boolean>(KEY_TARGET_OPENED) == true) return
        savedStateHandle[KEY_TARGET_OPENED] = true
        val target = ReaderTarget.fromArguments(mark = savedStateHandle.get<String>("mark"), cfi = null)
            as? ReaderTarget.Bookmark ?: return
        val bookmark = bookmarkRepository.observeByBookId(bookId).first().find { it.id == target.id } ?: return
        AudioPosition.fromLocatorJson(bookmark.locatorJson)?.let { seekTo(ctrl, it) }
    }

    /**
     * Sends and fetches this book's bookmarks when the user syncs bookmarks. The background
     * sync only covers downloaded books, and an audiobook is often streamed.
     */
    private fun syncBookmarks() {
        val loadedBook = book ?: return
        val serverId = loadedBook.serverId ?: return
        val grimmoryBookId = loadedBook.grimmoryBookId ?: return
        applicationScope.launch {
            runCatching {
                if (!appPreferencesRepository.getSyncBookmarks()) return@launch
                val server = serverRepository.getById(serverId)?.takeIf { it.isGrimmory } ?: return@launch
                if (!grimmoryTokenManager.isLoggedIn(server.id)) return@launch
                bookmarkSyncManager.syncBookmarksForBook(server, loadedBook.id, grimmoryBookId)
            }.onFailure { Timber.w(it, "Audiobook: bookmark sync failed") }
        }
    }

    fun seekToTrack(trackIndex: Int) {
        val ctrl = controller ?: return
        if (trackIndex in 0 until ctrl.mediaItemCount) {
            ctrl.seekTo(trackIndex, 0)
        }
    }

    fun nextChapter() {
        val ctrl = controller ?: return
        val pos = ctrl.currentPosition
        val next = _chapters.value.firstOrNull { it.startTimeMs > pos }
        if (next != null) {
            ctrl.seekTo(next.startTimeMs)
        } else if (ctrl.mediaItemCount > 1 && ctrl.currentMediaItemIndex < ctrl.mediaItemCount - 1) {
            ctrl.seekToNextMediaItem()
        }
    }

    fun previousChapter() {
        val ctrl = controller ?: return
        val pos = ctrl.currentPosition
        // Go to start of current chapter, or previous chapter if within 3s of start
        val currentChapter = _chapters.value.lastOrNull { it.startTimeMs <= pos }
        val prevChapter = if (currentChapter != null && pos - currentChapter.startTimeMs > 3000) {
            currentChapter
        } else {
            val idx = _chapters.value.indexOf(currentChapter)
            if (idx > 0) _chapters.value[idx - 1] else null
        }
        if (prevChapter != null) {
            ctrl.seekTo(prevChapter.startTimeMs)
        } else if (ctrl.mediaItemCount > 1 && ctrl.currentMediaItemIndex > 0) {
            ctrl.seekToPreviousMediaItem()
        } else {
            ctrl.seekTo(0)
        }
    }

    /** Sets the speed, and keeps it for the next audiobook too. */
    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        controller?.setPlaybackSpeed(speed)
        viewModelScope.launch { appPreferencesRepository.updateAudiobookSpeed(speed) }
    }

    private suspend fun saveProgress() {
        val ctrl = controller ?: return
        val duration = ctrl.duration
        if (duration <= 0) return
        val position = ctrl.currentPosition
        val trackIndex = ctrl.currentMediaItemIndex
        val trackCount = ctrl.mediaItemCount

        // Calculate overall percentage across all tracks
        val percentage = if (trackCount > 1) {
            val info = audiobookInfo
            val infoTracks = info?.tracks
            if (info != null && !infoTracks.isNullOrEmpty()) {
                val totalDurationMs = infoTracks.sumOf { it.durationMs }
                if (totalDurationMs > 0) {
                    val cumulativeMs = infoTracks.take(trackIndex).sumOf { it.durationMs } + position
                    (cumulativeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                } else {
                    // Fall back to track-count-based estimate
                    ((trackIndex.toFloat() + position.toFloat() / duration) / trackCount).coerceIn(0f, 1f)
                }
            } else {
                ((trackIndex.toFloat() + position.toFloat() / duration) / trackCount).coerceIn(0f, 1f)
            }
        } else {
            (position.toFloat() / duration).coerceIn(0f, 1f)
        }

        val locatorJson = org.json.JSONObject().apply {
            put("type", "audiobook")
            put("positionMs", position)
            put("trackIndex", trackIndex)
        }.toString()

        readingProgressRepository.updateProgress(
            bookId = bookId,
            serverId = book?.serverId,
            percentage = percentage,
            locatorJson = locatorJson
        )
    }

    private companion object {
        const val KEY_TARGET_OPENED = "target_opened"
    }

    override fun onCleared() {
        super.onCleared()
        // Read position from MediaController on main thread (required by API),
        // then save to DB asynchronously — never block the main thread
        val ctrl = controller
        if (ctrl != null) {
            val position = ctrl.currentPosition
            val trackIndex = ctrl.currentMediaItemIndex
            val duration = ctrl.duration
            val trackCount = ctrl.mediaItemCount
            val playing = ctrl.isPlaying

            // Fire-and-forget on the application scope so the save outlives the
            // ViewModel without leaking via GlobalScope (mirrors ReaderViewModel
            // after commit 5a22820).
            applicationScope.launch {
                saveProgressSnapshot(position, trackIndex, duration, trackCount)
            }

            if (!playing) {
                ctrl.stop()
            }
            ctrl.release()
        }
        controller = null
        Timber.d("AudiobookViewModel: onCleared")
    }

    private suspend fun saveProgressSnapshot(
        position: Long,
        trackIndex: Int,
        duration: Long,
        trackCount: Int
    ) {
        if (duration <= 0) return

        val percentage = if (trackCount > 1) {
            val infoTracks = audiobookInfo?.tracks
            if (!infoTracks.isNullOrEmpty()) {
                val totalDurationMs = infoTracks.sumOf { it.durationMs }
                if (totalDurationMs > 0) {
                    val cumulativeMs = infoTracks.take(trackIndex).sumOf { it.durationMs } + position
                    (cumulativeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                } else {
                    ((trackIndex.toFloat() + position.toFloat() / duration) / trackCount).coerceIn(0f, 1f)
                }
            } else {
                ((trackIndex.toFloat() + position.toFloat() / duration) / trackCount).coerceIn(0f, 1f)
            }
        } else {
            (position.toFloat() / duration).coerceIn(0f, 1f)
        }

        val locatorJson = org.json.JSONObject().apply {
            put("type", "audiobook")
            put("positionMs", position)
            put("trackIndex", trackIndex)
        }.toString()

        readingProgressRepository.updateProgress(
            bookId = bookId,
            serverId = book?.serverId,
            percentage = percentage,
            locatorJson = locatorJson
        )
    }
}
