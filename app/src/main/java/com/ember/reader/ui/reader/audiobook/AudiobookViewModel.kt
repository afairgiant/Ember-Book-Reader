package com.ember.reader.ui.reader.audiobook

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ember.reader.core.grimmory.AudiobookChapter
import com.ember.reader.core.grimmory.AudiobookInfo
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class AudiobookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager
) : ViewModel() {

    private val bookId: String = savedStateHandle["bookId"] ?: ""

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

    private val _chapters = MutableStateFlow<List<AudiobookChapter>>(emptyList())
    val chapters: StateFlow<List<AudiobookChapter>> = _chapters.asStateFlow()

    private var controller: MediaController? = null
    private var book: Book? = null
    private var sessionStartTime: java.time.Instant? = null
    private var sessionStartProgress: Float = 0f

    init {
        viewModelScope.launch { loadBook() }
    }

    private suspend fun loadBook() {
        val loadedBook = bookRepository.getById(bookId)
        if (loadedBook == null) {
            _uiState.value = AudiobookUiState.Error("Book not found")
            return
        }
        book = loadedBook

        val serverId = loadedBook.serverId
        val server = serverId?.let { serverRepository.getById(it) }
        val grimmoryBookId = loadedBook.grimmoryBookId

        // Fetch audiobook info from Grimmory
        var info: AudiobookInfo? = null
        if (server != null && server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            info = grimmoryClient.getAudiobookInfo(server.url, server.id, grimmoryBookId).getOrNull()
            info?.chapters?.let { _chapters.value = it }
        }

        _uiState.value = AudiobookUiState.Ready(book = loadedBook, info = info)

        sessionStartTime = java.time.Instant.now()
        val localProgress = readingProgressRepository.getByBookId(bookId)
        sessionStartProgress = localProgress?.percentage ?: 0f

        // Connect to playback service and set media
        connectToService(loadedBook, server, info)
    }

    private fun connectToService(book: Book, server: com.ember.reader.core.model.Server?, info: AudiobookInfo?) {
        val sessionToken = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            val ctrl = controllerFuture.get()
            controller = ctrl

            // Set up player listener
            ctrl.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = ctrl.duration.coerceAtLeast(0)
                    }
                }
            })

            // Build media source
            val mediaUri = buildMediaUri(book, server, info)
            if (mediaUri != null) {
                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(book.title)
                            .setArtist(book.author)
                            .build()
                    )
                    .build()

                // For folder-based audiobooks with multiple tracks
                if (info?.folderBased == true && info.tracks.isNotEmpty() && server != null) {
                    val items = viewModelScope.launch {
                        val trackItems = info.tracks.map { track ->
                            val trackUrl = grimmoryClient.audiobookStreamUrl(
                                server.url, server.id, book.grimmoryBookId!!, track.index
                            )
                            MediaItem.Builder()
                                .setUri(trackUrl ?: "")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(track.title ?: track.fileName)
                                        .build()
                                )
                                .build()
                        }
                        ctrl.setMediaItems(trackItems)
                        ctrl.prepare()
                        restorePosition(ctrl)
                    }
                } else {
                    ctrl.setMediaItem(mediaItem)
                    ctrl.prepare()
                    viewModelScope.launch { restorePosition(ctrl) }
                }
            }

            // Start position update loop
            startPositionUpdates()
        }, MoreExecutors.directExecutor())
    }

    private fun buildMediaUri(
        book: Book,
        server: com.ember.reader.core.model.Server?,
        info: AudiobookInfo?
    ): Uri? {
        // Prefer local file if downloaded
        if (book.isDownloaded && book.localPath != null) {
            return Uri.fromFile(File(book.localPath))
        }
        // Stream from Grimmory
        val grimmoryBookId = book.grimmoryBookId ?: return null
        if (server == null || !server.isGrimmory) return null
        val token = grimmoryTokenManager.getAccessToken(server.id) ?: return null
        val origin = com.ember.reader.core.network.serverOrigin(server.url)
        return Uri.parse("$origin/api/v1/audiobooks/$grimmoryBookId/stream?token=$token")
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

    fun play() { controller?.play() }
    fun pause() { controller?.pause() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun skipForward() {
        val ctrl = controller ?: return
        ctrl.seekTo((ctrl.currentPosition + 30_000).coerceAtMost(ctrl.duration))
    }

    fun skipBackward() {
        val ctrl = controller ?: return
        ctrl.seekTo((ctrl.currentPosition - 30_000).coerceAtLeast(0))
    }

    fun seekToChapter(chapter: AudiobookChapter) {
        controller?.seekTo(chapter.startTimeMs)
    }

    fun nextChapter() {
        val ctrl = controller ?: return
        val pos = ctrl.currentPosition
        val next = _chapters.value.firstOrNull { it.startTimeMs > pos }
        if (next != null) ctrl.seekTo(next.startTimeMs)
        else if (ctrl.mediaItemCount > 1 && ctrl.currentMediaItemIndex < ctrl.mediaItemCount - 1) {
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
        if (prevChapter != null) ctrl.seekTo(prevChapter.startTimeMs)
        else if (ctrl.mediaItemCount > 1 && ctrl.currentMediaItemIndex > 0) {
            ctrl.seekToPreviousMediaItem()
        } else {
            ctrl.seekTo(0)
        }
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        controller?.setPlaybackSpeed(speed)
    }

    private suspend fun saveProgress() {
        val ctrl = controller ?: return
        val duration = ctrl.duration
        if (duration <= 0) return
        val position = ctrl.currentPosition
        val percentage = (position.toFloat() / duration).coerceIn(0f, 1f)

        val locatorJson = org.json.JSONObject().apply {
            put("type", "audiobook")
            put("positionMs", position)
            put("trackIndex", ctrl.currentMediaItemIndex)
        }.toString()

        readingProgressRepository.updateProgress(
            bookId = bookId,
            serverId = book?.serverId,
            percentage = percentage,
            locatorJson = locatorJson
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Save progress synchronously
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            saveProgress()
        }
        // Stop the service if not playing
        val ctrl = controller
        if (ctrl != null && !ctrl.isPlaying) {
            ctrl.stop()
            ctrl.release()
        } else {
            ctrl?.release()
        }
        controller = null
        Timber.d("AudiobookViewModel: onCleared")
    }
}
