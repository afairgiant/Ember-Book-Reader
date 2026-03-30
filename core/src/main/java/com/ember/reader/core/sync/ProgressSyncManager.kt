package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryEpubProgress
import com.ember.reader.core.grimmory.GrimmoryFileProgress
import com.ember.reader.core.grimmory.GrimmoryProgressRequest
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.normalizeGrimmoryPercentage
import com.ember.reader.core.model.toGrimmoryPercentage
import com.ember.reader.core.repository.ReadingProgressRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

data class RemoteSyncResult(
    val progress: ReadingProgress,
    val source: String,
)

@Singleton
class ProgressSyncManager @Inject constructor(
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
) {

    /**
     * Pull best remote progress from all available sources (kosync + Grimmory).
     * Returns null if no remote progress found.
     */
    suspend fun pullBestProgress(server: Server, book: Book): RemoteSyncResult? {
        val results = mutableListOf<RemoteSyncResult>()

        // kosync
        val fileHash = book.fileHash
        if (fileHash != null && server.kosyncUsername.isNotBlank()) {
            readingProgressRepository.pullProgress(server, book.id, fileHash)
                .getOrNull()?.let { remote ->
                    results.add(RemoteSyncResult(remote.progress, remote.deviceName ?: "kosync"))
                }
        }

        // Grimmory
        val grimmoryBookId = book.grimmoryBookId
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            runCatching {
                val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
                val rawPct = detail.readProgress
                if (rawPct != null && rawPct > 0f) {
                    results.add(
                        RemoteSyncResult(
                            progress = ReadingProgress.fromRemote(book.id, server.id, rawPct.normalizeGrimmoryPercentage()),
                            source = "Grimmory Web Reader",
                        )
                    )
                }
            }.onFailure {
                Timber.w(it, "Failed to pull Grimmory progress for ${book.title}")
            }
        }

        return results.maxByOrNull { it.progress.percentage }
    }

    /**
     * Push local progress to all available endpoints (kosync + Grimmory).
     * Returns true if at least one push succeeded.
     */
    suspend fun pushProgress(server: Server, book: Book): Boolean {
        val progress = readingProgressRepository.getByBookId(book.id) ?: return false
        if (progress.percentage <= 0f) return false

        var pushed = false

        // kosync
        val fileHash = book.fileHash
        if (fileHash != null && server.kosyncUsername.isNotBlank()) {
            readingProgressRepository.pushProgress(server, book.id, fileHash)
                .onSuccess { pushed = true }
                .onFailure { Timber.w(it, "Failed to push kosync progress for ${book.title}") }
        }

        // Grimmory
        val grimmoryBookId = book.grimmoryBookId
        if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null) {
            runCatching {
                val pct = progress.percentage.toGrimmoryPercentage()
                val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrNull()
                val fileId = detail?.files?.firstOrNull()?.id

                grimmoryClient.pushProgress(
                    baseUrl = server.url,
                    serverId = server.id,
                    request = GrimmoryProgressRequest(
                        bookId = grimmoryBookId,
                        fileProgress = fileId?.let { GrimmoryFileProgress(bookFileId = it, progressPercent = pct) },
                        epubProgress = GrimmoryEpubProgress(cfi = "epubcfi(/6/2)", percentage = pct),
                    ),
                ).getOrThrow()
                pushed = true
                Timber.d("Pushed Grimmory progress for ${book.title}: ${pct}%")
            }.onFailure {
                Timber.w(it, "Failed to push Grimmory progress for ${book.title}")
            }
        }

        return pushed
    }
}
