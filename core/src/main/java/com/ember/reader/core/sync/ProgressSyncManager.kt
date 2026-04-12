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
        Timber.d("Sync pull: starting for '${book.title}' server='${server.name}'")
        val kosyncDeferred = async { pullKosync(server, book) }
        val grimmoryDeferred = async { pullGrimmory(server, book) }
        val results = listOfNotNull(kosyncDeferred.await(), grimmoryDeferred.await())
        val best = results.maxByOrNull { it.progress.percentage }
        Timber.d("Sync pull: ${results.size} source(s) found, best=${best?.let { "${it.progress.percentage} from ${it.source}" } ?: "none"}")
        best
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
        val fileHash = book.fileHash
        if (fileHash == null) {
            Timber.d("Sync pull: skipping kosync for '${book.title}' — no fileHash")
            return null
        }
        if (server.kosyncUsername.isBlank()) {
            Timber.d("Sync pull: skipping kosync for '${book.title}' — no kosync credentials")
            return null
        }
        Timber.d("Sync pull: trying kosync for '${book.title}' hash=$fileHash")
        val remote = readingProgressRepository.pullKosyncProgress(server, book.id, fileHash)
            .onFailure { Timber.w(it, "Failed to pull kosync progress for ${book.title}") }
            .getOrNull()
        if (remote == null) {
            Timber.d("Sync pull: kosync returned nothing for '${book.title}'")
            return null
        }
        Timber.d("Sync pull: kosync returned ${remote.progress.percentage} from ${remote.deviceName}")
        return RemoteSyncResult(remote.progress, remote.deviceName ?: "Another device")
    }

    private suspend fun pullGrimmory(server: Server, book: Book): RemoteSyncResult? {
        val grimmoryBookId = book.grimmoryBookId
        if (grimmoryBookId == null) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — no grimmoryBookId (opdsEntryId=${book.opdsEntryId})")
            return null
        }
        if (!server.isGrimmory) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — server not Grimmory")
            return null
        }
        if (!grimmoryTokenManager.isLoggedIn(server.id)) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — not logged in to Grimmory")
            return null
        }
        Timber.d("Sync pull: trying Grimmory for '${book.title}' grimmoryBookId=$grimmoryBookId")
        return runCatching {
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
            // readProgress is 0-1 scale, reflects whichever client pushed last.
            // Grimmory doesn't expose epubProgress/fileProgress in the book detail response,
            // so readProgress is the only available progress field.
            val pct = detail.readProgress
            Timber.d("Sync pull: Grimmory returned readProgress=$pct for '${book.title}'")
            if (pct == null || pct <= 0f) return@runCatching null
            RemoteSyncResult(
                progress = ReadingProgress.fromRemote(book.id, server.id, pct),
                source = detail.koreaderProgress?.device ?: "Grimmory",
            )
        }.onFailure {
            Timber.w(it, "Failed to pull Grimmory progress for ${book.title}")
        }.getOrNull()
    }

    private suspend fun pushKosync(server: Server, book: Book): Boolean {
        val fileHash = book.fileHash
        if (fileHash == null) {
            Timber.d("Sync push: skipping kosync for '${book.title}' — no fileHash")
            return false
        }
        if (server.kosyncUsername.isBlank()) {
            Timber.d("Sync push: skipping kosync for '${book.title}' — no kosync credentials")
            return false
        }
        Timber.d("Sync push: pushing kosync for '${book.title}' hash=$fileHash")
        return readingProgressRepository.pushKosyncProgress(server, book.id, fileHash)
            .onFailure { Timber.w(it, "Failed to push kosync progress for ${book.title}") }
            .isSuccess
    }

    private suspend fun pushGrimmory(server: Server, book: Book, percentage: Float): Boolean {
        val grimmoryBookId = book.grimmoryBookId ?: return false
        if (!server.isGrimmory || !grimmoryTokenManager.isLoggedIn(server.id)) return false
        return runCatching {
            val pct = percentage.toGrimmoryPercentage()
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrNull()
            val fileId = detail?.primaryFile?.id

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
