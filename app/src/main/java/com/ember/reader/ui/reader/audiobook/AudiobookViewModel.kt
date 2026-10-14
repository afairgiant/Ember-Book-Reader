package com.ember.reader.ui.reader.audiobook

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ember.reader.core.coroutine.ApplicationScope
import com.ember.reader.core.grimmory.AudiobookChapter
import com.ember.reader.core.grimmory.AudiobookInfo
import com.ember.reader.core.grimmory.AudiobookTrack
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.AudioPosition
import com.ember.reader.core.model.Book
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.DEFAULT_AUDIOBOOK_SKIP_SECONDS
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.BookmarkSyncManager
import com.ember.reader.ui.reader.common.ReaderTarget
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * The player screen. It shows the playback service's player and sends it the user's taps;
 * saving progress and moving on to the next book in Up Next are the service's
 * ([AudiobookPlayback]), so they go on with the screen closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudiobookViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val bookmarkSyncManager: BookmarkSyncManager,
    private val audiobookPlayback: AudiobookPlayback,
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

    /** The playback speed, kept for every audiobook. */
    val playbackSpeed: StateFlow<Float> = appPreferencesRepository.audiobookSpeedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

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

    private val _chapters = MutableStateFlow<List<AudiobookChapter>>(emptyList())
    val chapters: StateFlow<List<AudiobookChapter>> = _chapters.asStateFlow()

    private val _tracks = MutableStateFlow<List<AudiobookTrack>>(emptyList())
    val tracks: StateFlow<List<AudiobookTrack>> = _tracks.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private var controller: MediaController? = null
    private var book: Book? = null

    init {
        viewModelScope.launch { openBook() }
        // Keep "what plays next" current as the queue or the book changes.
        viewModelScope.launch {
            combine(upNext, bookIdFlow, _uiState) { _, _, _ -> }.collect {
                _nextAudiobook.value = book?.let { audiobookPlayback.chooseNext(it) }
            }
        }
    }

    fun removeFromUpNext(book: Book) {
        viewModelScope.launch { appPreferencesRepository.updateAudiobookUpNext { it - book.id } }
    }

    fun moveUpInUpNext(book: Book) {
        viewModelScope.launch { appPreferencesRepository.updateAudiobookUpNext { movedUp(it, book.id) } }
    }

    /** Leaves the current book where it is and starts [book] from Up Next now. */
    fun playNow(book: Book) {
        val ctrl = controller ?: return
        // The screen follows the player to the new book (see followPlayer).
        viewModelScope.launch { audiobookPlayback.switchTo(ctrl, book) }
    }

    /** Opens the book the screen was opened for, and puts it in the player unless it is there already. */
    private suspend fun openBook() {
        val loaded = show(bookId) ?: return
        val ctrl = controller
        if (ctrl == null) {
            connectToService(loaded)
        } else {
            putInPlayer(ctrl, loaded)
        }
    }

    /**
     * Shows [id]: its title, tracks and chapters. Leaves the player alone.
     *
     * @return the book, or null when it is not in the library.
     */
    private suspend fun show(id: String): LoadedAudiobook? {
        savedStateHandle["bookId"] = id
        bookIdFlow.value = id
        _chapters.value = emptyList()
        _tracks.value = emptyList()
        _currentTrackIndex.value = controller?.currentMediaItemIndex ?: 0
        val loaded = audiobookPlayback.load(id)
        if (loaded == null) {
            _uiState.value = AudiobookUiState.Error("Book not found")
            return null
        }
        book = loaded.book
        loaded.info?.chapters?.let { _chapters.value = it }
        loaded.info?.tracks?.let { _tracks.value = it }
        _uiState.value = AudiobookUiState.Ready(book = loaded.book, info = loaded.info)
        syncBookmarks()
        return loaded
    }

    /**
     * Puts [loaded] into the player, unless the player has it already: reopening the screen
     * while the book plays leaves it playing where it is. Another book in the player is
     * saved first, as replacing it would lose where it was.
     */
    private suspend fun putInPlayer(ctrl: MediaController, loaded: LoadedAudiobook) {
        when (audiobookPlayback.playingBookId(ctrl)) {
            loaded.book.id -> if (ctrl.playbackState == Player.STATE_IDLE) ctrl.prepare()
            null -> audiobookPlayback.start(ctrl, loaded, autoPlay = false)
            else -> {
                audiobookPlayback.saveProgress(ctrl)
                audiobookPlayback.start(ctrl, loaded, autoPlay = false)
            }
        }
        seekToRequestedBookmark(ctrl)
    }

    /** When the service moves the player on to another book, as Up Next does, the screen follows. */
    private fun followPlayer(ctrl: MediaController) {
        val playing = audiobookPlayback.playingBookId(ctrl) ?: return
        if (playing != bookId) viewModelScope.launch { show(playing) }
    }

    private fun connectToService(loaded: LoadedAudiobook) {
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
            val ctrl = runCatching { controllerFuture.get() }.getOrElse {
                Timber.e(it, "Audiobook: could not connect to the playback service")
                _uiState.value = AudiobookUiState.Error("Could not start playback")
                return@addListener
            }
            controller = ctrl
            // The service may already be running a timer set before this screen was opened.
            onSleepTimerChanged(SleepTimer.fromBundle(ctrl.sessionExtras))

            ctrl.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = ctrl.duration.coerceAtLeast(0)
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Audiobook: ExoPlayer error code=%d mediaItem=%d/%d", error.errorCode, ctrl.currentMediaItemIndex, ctrl.mediaItemCount)
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentTrackIndex.value = ctrl.currentMediaItemIndex
                    Timber.d("Audiobook: track transition to %d/%d reason=%d title=%s", ctrl.currentMediaItemIndex, ctrl.mediaItemCount, reason, mediaItem?.mediaMetadata?.title)
                    followPlayer(ctrl)
                }
            })
            _isPlaying.value = ctrl.isPlaying

            viewModelScope.launch {
                putInPlayer(ctrl, loaded)
                startPositionUpdates()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (isActive) {
                val ctrl = controller
                if (ctrl != null && ctrl.isConnected) {
                    _currentPositionMs.value = ctrl.currentPosition.coerceAtLeast(0)
                    _durationMs.value = ctrl.duration.coerceAtLeast(0)
                }
                updateSleepTimeLeft()
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
        controller?.setPlaybackSpeed(speed)
        viewModelScope.launch { appPreferencesRepository.updateAudiobookSpeed(speed) }
    }

    private companion object {
        const val KEY_TARGET_OPENED = "target_opened"
    }

    override fun onCleared() {
        super.onCleared()
        // The service saves the position itself, on pause and as it plays.
        controller?.let { ctrl ->
            if (!ctrl.isPlaying) ctrl.stop()
            ctrl.release()
        }
        controller = null
        Timber.d("AudiobookViewModel: onCleared")
    }
}
