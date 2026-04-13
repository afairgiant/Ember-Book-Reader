package com.ember.reader.core.repository

import android.content.Context
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.grimmory.AudiobookTrack
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.DownloadProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.sync.PartialMd5
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class BookDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val opdsClient: OpdsClient,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val metadataExtractor: BookMetadataExtractor
) {

    private val booksDir: File by lazy {
        File(context.filesDir, "books").also { it.mkdirs() }
    }

    suspend fun downloadBook(
        book: Book,
        server: Server,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Result<Book> = runCatching {
        val grimmoryBookId = book.grimmoryBookId
        Timber.d("Download: isGrimmory=${server.isGrimmory} loggedIn=${grimmoryTokenManager.isLoggedIn(server.id)} grimmoryBookId=$grimmoryBookId format=${book.format}")

        // For audiobooks from Grimmory, check if folder-based and handle accordingly
        if (book.format == BookFormat.AUDIOBOOK &&
            server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null
        ) {
            return@runCatching downloadAudiobook(book, server, grimmoryBookId, onProgress)
        }

        val downloadUrl = book.downloadUrl
            ?: error("No download URL for book: ${book.title}")

        val extension = when (book.format) {
            BookFormat.EPUB -> "epub"
            BookFormat.PDF -> "pdf"
            BookFormat.AUDIOBOOK -> "m4b"
        }
        val fileName = "${book.id}.$extension"
        val file = File(booksDir, fileName)

        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            grimmoryClient.downloadBook(
                baseUrl = server.url,
                serverId = server.id,
                grimmoryBookId = grimmoryBookId,
                destination = file,
                onProgress = onProgress?.let { callback ->
                    { bytesRead, totalBytes ->
                        callback(DownloadProgress(bytesDownloaded = bytesRead, totalBytes = totalBytes))
                    }
                }
            ).getOrThrow()
        } else {
            opdsClient.downloadBookToFile(
                baseUrl = server.url,
                username = server.opdsUsername,
                password = server.opdsPassword,
                downloadPath = downloadUrl,
                destination = file,
                onProgress = onProgress?.let { callback ->
                    { bytesRead, totalBytes ->
                        callback(DownloadProgress(bytesDownloaded = bytesRead, totalBytes = totalBytes))
                    }
                }
            ).getOrThrow()
        }

        validateDownloadedFile(file)

        val fileHash = PartialMd5.compute(file)
        bookDao.updateLocalPath(book.id, file.absolutePath, Instant.now())
        bookDao.updateFileHash(book.id, fileHash)

        // Extract and cache cover locally so it works offline
        val metadata = metadataExtractor.extractMetadata(file)
        if (metadata.coverUrl != null) {
            bookDao.getById(book.id)?.let { entity ->
                bookDao.update(entity.copy(coverUrl = metadata.coverUrl))
            }
        }

        book.copy(
            localPath = file.absolutePath,
            fileHash = fileHash,
            coverUrl = metadata.coverUrl ?: book.coverUrl,
            downloadedAt = Instant.now()
        )
    }

    /**
     * Downloads an audiobook from Grimmory. Checks if folder-based (multiple tracks)
     * and downloads each track individually to a subfolder if so.
     */
    private suspend fun downloadAudiobook(
        book: Book,
        server: Server,
        grimmoryBookId: Long,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Book {
        val infoResult = grimmoryClient.getAudiobookInfo(server.url, server.id, grimmoryBookId)
        infoResult.onFailure { error ->
            Timber.e(error, "Audiobook download: failed to fetch audiobook info for grimmoryBookId=%d", grimmoryBookId)
        }
        val info = infoResult.getOrNull()
        Timber.d("Audiobook download: info=%s folderBased=%s tracks=%d", info != null, info?.folderBased, info?.tracks?.size ?: 0)

        val tracks = info?.tracks
        if (info != null && info.folderBased && !tracks.isNullOrEmpty()) {
            return downloadFolderBasedAudiobook(book, server, grimmoryBookId, tracks, onProgress)
        } else {
            return downloadSingleFileAudiobook(book, server, grimmoryBookId, onProgress)
        }
    }

    private suspend fun downloadFolderBasedAudiobook(
        book: Book,
        server: Server,
        grimmoryBookId: Long,
        tracks: List<AudiobookTrack>,
        onProgress: ((DownloadProgress) -> Unit)?
    ): Book {
        val audiobookDir = File(booksDir, "audiobook_${book.id}").also { it.mkdirs() }
        Timber.d("Downloading folder-based audiobook: ${tracks.size} tracks to ${audiobookDir.name}")

        val totalBytes = tracks.sumOf { it.fileSizeBytes }
        var completedBytes = 0L

        for (track in tracks) {
            val trackFile =
                File(audiobookDir, "%03d_%s".format(track.index, track.fileName ?: "track${track.index}.m4a"))
            if (trackFile.exists() && trackFile.length() > 0) {
                Timber.d("Track ${track.index} already downloaded, skipping")
                completedBytes += trackFile.length()
                continue
            }
            val streamUrl = grimmoryClient.audiobookStreamUrl(server.url, server.id, grimmoryBookId, track.index)
                ?: error("Cannot build stream URL for track ${track.index}")
            Timber.d("Downloading track ${track.index}: ${track.title ?: track.fileName}")
            val trackCompletedBytes = completedBytes
            grimmoryClient.downloadFromUrl(streamUrl, trackFile) { bytesRead, trackTotalBytes ->
                onProgress?.invoke(
                    DownloadProgress(
                        bytesDownloaded = trackCompletedBytes + bytesRead,
                        totalBytes = if (totalBytes > 0) totalBytes else null,
                        trackIndex = track.index,
                        trackCount = tracks.size,
                        trackBytesDownloaded = bytesRead,
                        trackTotalBytes = trackTotalBytes
                    )
                )
            }.getOrThrow()
            completedBytes += trackFile.length()
        }

        bookDao.updateLocalPath(book.id, audiobookDir.absolutePath, Instant.now())
        return book.copy(
            localPath = audiobookDir.absolutePath,
            downloadedAt = Instant.now()
        )
    }

    private suspend fun downloadSingleFileAudiobook(
        book: Book,
        server: Server,
        grimmoryBookId: Long,
        onProgress: ((DownloadProgress) -> Unit)?
    ): Book {
        val file = File(booksDir, "${book.id}.m4b")
        grimmoryClient.downloadBook(
            server.url, server.id, grimmoryBookId, file,
            onProgress = onProgress?.let { callback ->
                { bytesRead, totalBytes ->
                    callback(DownloadProgress(bytesDownloaded = bytesRead, totalBytes = totalBytes))
                }
            }
        ).getOrThrow()

        Timber.d("Download complete: file=${file.name} size=${file.length()}")
        validateDownloadedFile(file)

        val fileHash = PartialMd5.compute(file)
        bookDao.updateLocalPath(book.id, file.absolutePath, Instant.now())
        bookDao.updateFileHash(book.id, fileHash)

        return book.copy(
            localPath = file.absolutePath,
            fileHash = fileHash,
            downloadedAt = Instant.now()
        )
    }

    private fun validateDownloadedFile(file: File) {
        if (file.length() < 100) {
            file.delete()
            error("Downloaded file is too small — server may have returned an error")
        }
        val header = ByteArray(5).also { buf -> file.inputStream().use { it.read(buf) } }
        if (header.decodeToString().startsWith("<!") || header.decodeToString().startsWith("<html")) {
            file.delete()
            error("Downloaded file is HTML, not a book — check server URL and credentials")
        }
    }
}
