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
import androidx.media3.common.PlaybackException
import com.ember.reader.core.grimmory.AudiobookChapter
import com.ember.reader.core.grimmory.AudiobookInfo
import com.ember.reader.core.grimmory.AudiobookTrack
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
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
            Timber.w("Audiobook: skipping info fetch server=%s isGrimmory=%s loggedIn=%s grimmoryBookId=%s", server?.id, server?.isGrimmory, server?.let { grimmoryTokenManager.isLoggedIn(it.id) }, grimmoryBookId)
        }

        audiobookInfo = info
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
                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Audiobook: ExoPlayer error code=%d mediaItem=%d/%d", error.errorCode, ctrl.currentMediaItemIndex, ctrl.mediaItemCount)
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentTrackIndex.value = ctrl.currentMediaItemIndex
                    Timber.d("Audiobook: track transition to %d/%d reason=%d title=%s", ctrl.currentMediaItemIndex, ctrl.mediaItemCount, reason, mediaItem?.mediaMetadata?.title)
                }
            })

            // Build media items
            viewModelScope.launch {
                val mediaItems = buildMediaItems(book, server, info)
                if (mediaItems.isNotEmpty()) {
                    Timber.d("Audiobook: setting %d media items", mediaItems.size)
                    ctrl.setMediaItems(mediaItems)
                    ctrl.prepare()
                    restorePosition(ctrl)
                } else {
                    Timber.e("Audiobook: no media items could be built")
                }

                // Start position update loop
                startPositionUpdates()
            }
        }, MoreExecutors.directExecutor())
    }

    private suspend fun buildMediaItems(
        book: Book,
        server: com.ember.reader.core.model.Server?,
        info: AudiobookInfo?,
    ): List<MediaItem> {
        val artworkUri = book.coverUrl?.let { Uri.parse(it) }
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
                    server.url, server.id, book.grimmoryBookId!!, track.index,
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
            locatorJson = locatorJson,
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Save progress on main thread (MediaController requires it)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main.immediate) {
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
