package com.ember.reader.ui.reader.audiobook

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.ember.reader.core.grimmory.AudiobookInfo
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.isPartFile
import com.ember.reader.core.network.serverOrigin
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import timber.log.Timber

/** An audiobook ready to hand to the player: the book, its server, and what Grimmory says of it. */
data class LoadedAudiobook(
    val book: Book,
    val server: Server?,
    val info: AudiobookInfo?,
)

/**
 * Puts audiobooks into a player and keeps their progress. The player screen and the
 * playback service both use it, so progress is saved and the next book in Up Next starts
 * whether or not the screen is open.
 *
 * Each track handed to the player carries its book's id and where it sits in the book
 * ([trackExtras]), so progress is always saved under the book actually playing.
 *
 * Every function that takes a [Player] must be called on the main thread.
 */
@Singleton
class AudiobookPlayback @Inject constructor(
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val appPreferencesRepository: AppPreferencesRepository,
) {

    /** [bookId] with its server and, from a Grimmory server, its tracks and chapters; null if there is no such book. */
    suspend fun load(bookId: String): LoadedAudiobook? {
        val book = bookRepository.getById(bookId) ?: return null
        val server = book.serverId?.let { serverRepository.getById(it) }
        val grimmoryBookId = book.grimmoryBookId
        var info: AudiobookInfo? = null
        if (server != null && server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            grimmoryClient.getAudiobookInfo(server.url, server.id, grimmoryBookId)
                .onSuccess {
                    info = it
                    Timber.d("Audiobook: info fetched folderBased=%s tracks=%d chapters=%d", it.folderBased, it.tracks?.size ?: 0, it.chapters?.size ?: 0)
                }
                .onFailure { Timber.e(it, "Audiobook: failed to fetch audiobook info") }
        }
        return LoadedAudiobook(book, server, info)
    }

    /**
     * Hands [loaded] to [player], at the place it was left. With [autoPlay] it starts by
     * itself, as it does when it follows a book that has just ended; a book already heard
     * to its end then starts over.
     *
     * @return false when nothing playable could be built for it; the player is then emptied,
     * so nothing of the book before is left in it.
     */
    suspend fun start(player: Player, loaded: LoadedAudiobook, autoPlay: Boolean): Boolean {
        val mediaItems = mediaItems(loaded)
        if (mediaItems.isEmpty()) {
            Timber.e("Audiobook: no media items could be built")
            player.clearMediaItems()
            return false
        }
        // Read before the player changes, while nothing can have saved over it.
        val progress = readingProgressRepository.getByBookId(loaded.book.id)
        val speed = appPreferencesRepository.audiobookSpeedFlow.first()
        Timber.d("Audiobook: setting %d media items", mediaItems.size)
        player.setMediaItems(mediaItems)
        player.prepare()
        if (progress != null && !(autoPlay && progress.percentage >= FINISHED_PROGRESS)) {
            restorePosition(player, progress.locatorJson, progress.percentage)
        }
        player.setPlaybackSpeed(speed)
        if (autoPlay) player.play()
        return true
    }

    /** The book whose track [player] has now, or null when it has none of Ember's. */
    fun playingBookId(player: Player): String? = player.currentMediaItem?.bookId

    /** Saves where [player] is in its book. Does nothing when it holds no audiobook. */
    suspend fun saveProgress(player: Player) {
        snapshot(player)?.let { save(it) }
    }

    /** Where [player] is now, read on the main thread to be saved later; null when it holds no audiobook. */
    fun snapshot(player: Player): PlaybackSnapshot? {
        val item = player.currentMediaItem ?: return null
        val bookId = item.bookId ?: return null
        return PlaybackSnapshot(
            bookId = bookId,
            trackIndex = player.currentMediaItemIndex,
            trackCount = player.mediaItemCount,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration,
            trackStartMs = item.mediaMetadata.extras?.getLong(EXTRA_TRACK_START_MS, -1L) ?: -1L,
            bookLengthMs = item.mediaMetadata.extras?.getLong(EXTRA_BOOK_LENGTH_MS, 0L) ?: 0L,
        )
    }

    suspend fun save(snapshot: PlaybackSnapshot) {
        val percentage = snapshot.percentage() ?: return
        val serverId = bookRepository.getById(snapshot.bookId)?.serverId
        readingProgressRepository.updateProgress(
            bookId = snapshot.bookId,
            serverId = serverId,
            percentage = percentage,
            locatorJson = JSONObject().apply {
                put("type", "audiobook")
                put("positionMs", snapshot.positionMs)
                put("trackIndex", snapshot.trackIndex)
            }.toString(),
        )
    }

    /** What plays after [current]: the head of Up Next, or the next unfinished book of its series. */
    suspend fun chooseNext(current: Book): NextAudiobook? {
        val ids = appPreferencesRepository.audiobookUpNextFlow.first()
        val books = bookRepository.getByIds(ids.toSet()).associateBy { it.id }
        val queue = ids.mapNotNull(books::get)
        val seriesMates = current.serverId
            ?.takeIf { current.series != null }
            ?.let { bookRepository.observeByServer(it).first() }
            .orEmpty()
        val progress = seriesMates.associate { it.id to readingProgressRepository.getByBookId(it.id)?.percentage }
        return nextAudiobook(current, queue, seriesMates, progress::get)
    }

    /**
     * The book in [player] has played to its end: marks it finished and starts what comes
     * next, if anything does.
     *
     * @return the book now playing, or null when playback simply stopped.
     */
    suspend fun onEnded(player: Player): LoadedAudiobook? {
        val bookId = playingBookId(player) ?: return null
        val book = bookRepository.getById(bookId) ?: return null
        readingProgressRepository.updateProgress(
            bookId = bookId,
            serverId = book.serverId,
            percentage = 1f,
            locatorJson = null,
        )
        val next = chooseNext(book) ?: return null
        Timber.d("Audiobook: '%s' ended, starting '%s'", book.title, next.book.title)
        // Not saved first: the finished mark would be overwritten by the position at the end.
        return switchTo(player, next.book, saveCurrent = false)
    }

    /** Starts [next] in [player], taking it out of Up Next; with [saveCurrent], saves where the book before was first. */
    suspend fun switchTo(player: Player, next: Book, saveCurrent: Boolean = true): LoadedAudiobook? {
        if (saveCurrent) saveProgress(player)
        appPreferencesRepository.updateAudiobookUpNext { it - next.id }
        val loaded = load(next.id) ?: return null
        return loaded.takeIf { start(player, it, autoPlay = true) }
    }

    private fun restorePosition(player: Player, locatorJson: String?, percentage: Float) {
        if (locatorJson != null && locatorJson.contains("positionMs")) {
            runCatching {
                val json = JSONObject(locatorJson)
                val positionMs = json.optLong("positionMs", 0)
                val trackIndex = json.optInt("trackIndex", 0)
                if (player.mediaItemCount > 1) player.seekTo(trackIndex, positionMs) else player.seekTo(positionMs)
            }
        } else if (percentage > 0f && player.duration > 0) {
            player.seekTo((player.duration * percentage).toLong())
        }
    }

    private suspend fun mediaItems(loaded: LoadedAudiobook): List<MediaItem> {
        val (book, server, info) = loaded
        // Authenticated artwork for the notification and lock screen.
        val artworkUri = book.coverUrl?.let { rawUrl ->
            val token = server?.takeIf { it.isGrimmory && grimmoryTokenManager.isLoggedIn(it.id) }
                ?.let { grimmoryTokenManager.getAccessToken(it.id) }
            Uri.parse(if (token != null) "$rawUrl?token=$token" else rawUrl)
        }
        val trackLengths = info?.tracks?.map { it.durationMs }.orEmpty()

        fun item(uri: Uri, title: String?, trackIndex: Int, album: String? = null) = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(book.author)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    .setExtras(trackExtras(book.id, trackLengths, trackIndex))
                    .build()
            )
            .build()

        // Downloaded: the track files of a folder-based book, or its one file.
        val localPath = book.localPath
        if (book.isDownloaded && localPath != null) {
            val localDir = File(localPath)
            val trackFiles = localDir.takeIf { it.isDirectory }?.listFiles()
                ?.filter { it.isFile && it.length() > 0 && !isPartFile(it) }
                ?.sortedBy { it.name }
                .orEmpty()
            if (trackFiles.isNotEmpty()) {
                Timber.d("Audiobook: playing %d local track files from %s", trackFiles.size, localDir.name)
                return trackFiles.mapIndexed { index, file ->
                    item(Uri.fromFile(file), file.nameWithoutExtension, index, album = book.title)
                }
            }
            Timber.d("Audiobook: playing single local file %s", localPath)
            return listOf(item(Uri.fromFile(localDir), book.title, 0))
        }

        // Folder-based streaming: one URL a track.
        val tracks = info?.tracks
        val grimmoryBookId = book.grimmoryBookId
        if (info?.folderBased == true && !tracks.isNullOrEmpty() && server != null && grimmoryBookId != null) {
            Timber.d("Audiobook: building %d track stream URLs", tracks.size)
            return tracks.mapIndexed { index, track ->
                val trackUrl = grimmoryClient.audiobookStreamUrl(server.url, server.id, grimmoryBookId, track.index)
                item(Uri.parse(trackUrl ?: ""), track.title ?: track.fileName, index)
            }
        }

        // Single-file streaming.
        if (grimmoryBookId == null || server == null || !server.isGrimmory) return emptyList()
        val token = grimmoryTokenManager.getAccessToken(server.id) ?: return emptyList()
        Timber.d("Audiobook: single-stream URL (not folder-based)")
        return listOf(item(Uri.parse("${serverOrigin(server.url)}/api/v1/audiobooks/$grimmoryBookId/stream?token=$token"), book.title, 0))
    }
}

/** Where a player was in an audiobook, read on the main thread so it can be saved off it. */
data class PlaybackSnapshot(
    val bookId: String,
    val trackIndex: Int,
    val trackCount: Int,
    val positionMs: Long,
    /** The length of the track now playing; not positive while it is unknown. */
    val durationMs: Long,
    /** Where the track now playing starts in the book; negative when the track lengths are unknown. */
    val trackStartMs: Long,
    /** The whole book's length; 0 when unknown. */
    val bookLengthMs: Long,
) {
    /**
     * How far through the book this is, from 0 to 1: by the track lengths when they are
     * known, else counting each track as an equal part. Null while the track's length is
     * not yet known.
     */
    fun percentage(): Float? {
        if (durationMs <= 0) return null
        val fraction = if (trackStartMs >= 0 && bookLengthMs > 0) {
            (trackStartMs + positionMs).toFloat() / bookLengthMs
        } else {
            (trackIndex + positionMs.toFloat() / durationMs) / trackCount.coerceAtLeast(1)
        }
        return fraction.coerceIn(0f, 1f)
    }
}

/**
 * The extras each track carries: its book's id, and with [trackLengths] (one a track, in
 * order) where track [trackIndex] starts in the book and how long the book is.
 */
internal fun trackExtras(bookId: String, trackLengths: List<Long>, trackIndex: Int): Bundle = Bundle().apply {
    putString(EXTRA_BOOK_ID, bookId)
    val bookLength = trackLengths.sum()
    if (trackIndex < trackLengths.size && bookLength > 0) {
        putLong(EXTRA_TRACK_START_MS, trackLengths.take(trackIndex).sum())
        putLong(EXTRA_BOOK_LENGTH_MS, bookLength)
    }
}

private val MediaItem.bookId: String? get() = mediaMetadata.extras?.getString(EXTRA_BOOK_ID)

private const val EXTRA_BOOK_ID = "com.ember.reader.audiobook.BOOK_ID"
private const val EXTRA_TRACK_START_MS = "com.ember.reader.audiobook.TRACK_START_MS"
private const val EXTRA_BOOK_LENGTH_MS = "com.ember.reader.audiobook.BOOK_LENGTH_MS"
