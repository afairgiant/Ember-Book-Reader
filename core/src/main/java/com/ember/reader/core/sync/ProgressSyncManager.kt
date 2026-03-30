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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
     * Pull best remote progress from all available sources (kosync + Grimmory)
     * concurrently. Returns null if no remote progress found.
     */
    suspend fun pullBestProgress(server: Server, book: Book): RemoteSyncResult? = coroutineScope {
        val kosyncDeferred = async { pullKosync(server, book) }
        val grimmoryDeferred = async { pullGrimmory(server, book) }
        listOfNotNull(kosyncDeferred.await(), grimmoryDeferred.await())
            .maxByOrNull { it.progress.percentage }
    }

    /**
     * Push local progress to all available endpoints (kosync + Grimmory)
     * concurrently. Returns true if at least one push succeeded.
     */
    suspend fun pushProgress(server: Server, book: Book): Boolean {
        val progress = readingProgressRepository.getByBookId(book.id) ?: return false
        if (progress.percentage <= 0f) return false

        return coroutineScope {
            val kosyncDeferred = async { pushKosync(server, book) }
            val grimmoryDeferred = async { pushGrimmory(server, book, progress.percentage) }
            kosyncDeferred.await() || grimmoryDeferred.await()
        }
    }

    private suspend fun pullKosync(server: Server, book: Book): RemoteSyncResult? {
        val fileHash = book.fileHash ?: return null
        if (server.kosyncUsername.isBlank()) return null
        val remote = readingProgressRepository.pullProgress(server, book.id, fileHash)
            .onFailure { Timber.w(it, "Failed to pull kosync progress for ${book.title}") }
            .getOrNull() ?: return null
        return RemoteSyncResult(remote.progress, remote.deviceName ?: "kosync")
    }

    private suspend fun pullGrimmory(server: Server, book: Book): RemoteSyncResult? {
        val grimmoryBookId = book.grimmoryBookId ?: return null
        if (!server.isGrimmory || !grimmoryTokenManager.isLoggedIn(server.id)) return null
        return runCatching {
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
            val rawPct = detail.readProgress ?: return@runCatching null
            if (rawPct <= 0f) return@runCatching null
            RemoteSyncResult(
                progress = ReadingProgress.fromRemote(book.id, server.id, rawPct.normalizeGrimmoryPercentage()),
                source = "Grimmory Web Reader",
            )
        }.onFailure {
            Timber.w(it, "Failed to pull Grimmory progress for ${book.title}")
        }.getOrNull()
    }

    private suspend fun pushKosync(server: Server, book: Book): Boolean {
        val fileHash = book.fileHash ?: return false
        if (server.kosyncUsername.isBlank()) return false
        return readingProgressRepository.pushProgress(server, book.id, fileHash)
            .onFailure { Timber.w(it, "Failed to push kosync progress for ${book.title}") }
            .isSuccess
    }

    private suspend fun pushGrimmory(server: Server, book: Book, percentage: Float): Boolean {
        val grimmoryBookId = book.grimmoryBookId ?: return false
        if (!server.isGrimmory || !grimmoryTokenManager.isLoggedIn(server.id)) return false
        return runCatching {
            val pct = percentage.toGrimmoryPercentage()
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrNull()
            val fileId = detail?.files?.firstOrNull()?.id

            grimmoryClient.pushProgress(
                baseUrl = server.url,
                serverId = server.id,
                request = GrimmoryProgressRequest(
                    bookId = grimmoryBookId,
                    fileProgress = fileId?.let { GrimmoryFileProgress(bookFileId = it, progressPercent = pct) },
                    epubProgress = GrimmoryEpubProgress(cfi = PLACEHOLDER_CFI, percentage = pct),
                ),
            ).getOrThrow()
            Timber.d("Pushed Grimmory progress for ${book.title}: ${pct}%")
        }.onFailure {
            Timber.w(it, "Failed to push Grimmory progress for ${book.title}")
        }.isSuccess
    }

    companion object {
        const val PLACEHOLDER_CFI = "epubcfi(/6/2)"
    }
}
