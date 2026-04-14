package com.ember.reader.core.sync

import com.ember.reader.core.coroutine.runCatchingCancellable
import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryEpubProgress
import com.ember.reader.core.grimmory.GrimmoryFileProgress
import com.ember.reader.core.grimmory.GrimmoryHttpException
import com.ember.reader.core.grimmory.GrimmoryProgressRequest
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.toGrimmoryPercentage
import com.ember.reader.core.repository.ReadingProgressRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

data class RemoteSyncResult(
    val progress: ReadingProgress,
    val source: String
)

@Singleton
class ProgressSyncManager @Inject constructor(
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val syncStatusRepository: SyncStatusRepository
) {

    /**
     * Per-source sync outcome. Success vs. Skipped matters for status
     * reporting: skipping a channel (no credentials, book not sync-eligible)
     * is not a failure, while contacting the server and getting nothing
     * back is still a healthy sync.
     */
    private sealed interface SourceOutcome<out T> {
        data class Ok<T>(val value: T?) : SourceOutcome<T>
        data object Skipped : SourceOutcome<Nothing>
        data class Failure(val error: Throwable) : SourceOutcome<Nothing>
    }

    /**
     * Pull best remote progress from all available sources (kosync + Grimmory)
     * concurrently. Returns null if no remote progress found.
     */
    suspend fun pullBestProgress(server: Server, book: Book): RemoteSyncResult? = coroutineScope {
        Timber.d("Sync pull: starting for '${book.title}' server='${server.name}'")
        val kosyncDeferred = async { pullKosync(server, book) }
        val grimmoryDeferred = async { pullGrimmory(server, book) }
        val outcomes = listOf(kosyncDeferred.await(), grimmoryDeferred.await())

        reportStatus(server.id, outcomes)

        val best = outcomes
            .filterIsInstance<SourceOutcome.Ok<RemoteSyncResult>>()
            .mapNotNull { it.value }
            .maxByOrNull { it.progress.percentage }
        Timber.d("Sync pull: best=${best?.let { "${it.progress.percentage} from ${it.source}" } ?: "none"}")
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
            val outcomes = listOf(kosyncDeferred.await(), grimmoryDeferred.await())

            reportStatus(server.id, outcomes)

            outcomes.any { it is SourceOutcome.Ok<*> }
        }
    }

    /**
     * Classify a per-server sync attempt and report it.
     *
     * - Any [SourceOutcome.Ok] → healthy; a single working channel is enough.
     * - Otherwise, surface the most actionable failure (Auth > Http > other).
     * - All skipped → nothing to report (the server isn't in play for this op).
     */
    private suspend fun reportStatus(serverId: Long, outcomes: List<SourceOutcome<*>>) {
        val hasSuccess = outcomes.any { it is SourceOutcome.Ok<*> }
        val failures = outcomes.filterIsInstance<SourceOutcome.Failure>().map { it.error }
        when {
            hasSuccess -> syncStatusRepository.reportSuccess(serverId)
            failures.isNotEmpty() -> syncStatusRepository.reportFailure(serverId, mostActionable(failures))
            else -> Unit // all skipped — not a sync event
        }
    }

    private fun mostActionable(errors: List<Throwable>): Throwable =
        errors.firstOrNull { it is GrimmoryAuthExpiredException }
            ?: errors.firstOrNull { it is GrimmoryHttpException }
            ?: errors.first()

    private suspend fun pullKosync(server: Server, book: Book): SourceOutcome<RemoteSyncResult> {
        val fileHash = book.fileHash
        if (fileHash == null) {
            Timber.d("Sync pull: skipping kosync for '${book.title}' — no fileHash")
            return SourceOutcome.Skipped
        }
        if (server.kosyncUsername.isBlank()) {
            Timber.d("Sync pull: skipping kosync for '${book.title}' — no kosync credentials")
            return SourceOutcome.Skipped
        }
        Timber.d("Sync pull: trying kosync for '${book.title}' hash=$fileHash")
        val result = readingProgressRepository.pullKosyncProgress(server, book.id, fileHash)
        return result.fold(
            onSuccess = { remote ->
                if (remote == null) {
                    Timber.d("Sync pull: kosync returned nothing for '${book.title}'")
                    SourceOutcome.Ok(null)
                } else {
                    Timber.d("Sync pull: kosync returned ${remote.progress.percentage} from ${remote.deviceName}")
                    SourceOutcome.Ok(RemoteSyncResult(remote.progress, remote.deviceName ?: "Another device"))
                }
            },
            onFailure = { error ->
                Timber.w(error, "Failed to pull kosync progress for ${book.title}")
                SourceOutcome.Failure(error)
            }
        )
    }

    private suspend fun pullGrimmory(server: Server, book: Book): SourceOutcome<RemoteSyncResult> {
        val grimmoryBookId = book.grimmoryBookId
        if (grimmoryBookId == null) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — no grimmoryBookId (opdsEntryId=${book.opdsEntryId})")
            return SourceOutcome.Skipped
        }
        if (!server.isGrimmory) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — server not Grimmory")
            return SourceOutcome.Skipped
        }
        if (!grimmoryTokenManager.isLoggedIn(server.id)) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — not logged in to Grimmory")
            return SourceOutcome.Skipped
        }
        Timber.d("Sync pull: trying Grimmory for '${book.title}' grimmoryBookId=$grimmoryBookId")
        return runCatchingCancellable {
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrThrow()
            // readProgress is 0-1 scale, reflects whichever client pushed last.
            // Grimmory doesn't expose epubProgress/fileProgress in the book detail response,
            // so readProgress is the only available progress field.
            val pct = detail.readProgress
            Timber.d("Sync pull: Grimmory returned readProgress=$pct for '${book.title}'")
            if (pct == null || pct <= 0f) {
                null
            } else {
                RemoteSyncResult(
                    progress = ReadingProgress.fromRemote(book.id, server.id, pct),
                    source = detail.koreaderProgress?.device ?: "Grimmory"
                )
            }
        }.fold(
            onSuccess = { SourceOutcome.Ok(it) },
            onFailure = { error ->
                Timber.w(error, "Failed to pull Grimmory progress for ${book.title}")
                SourceOutcome.Failure(error)
            }
        )
    }

    private suspend fun pushKosync(server: Server, book: Book): SourceOutcome<Unit> {
        val fileHash = book.fileHash
        if (fileHash == null) {
            Timber.d("Sync push: skipping kosync for '${book.title}' — no fileHash")
            return SourceOutcome.Skipped
        }
        if (server.kosyncUsername.isBlank()) {
            Timber.d("Sync push: skipping kosync for '${book.title}' — no kosync credentials")
            return SourceOutcome.Skipped
        }
        Timber.d("Sync push: pushing kosync for '${book.title}' hash=$fileHash")
        return readingProgressRepository.pushKosyncProgress(server, book.id, fileHash).fold(
            onSuccess = { SourceOutcome.Ok(Unit) },
            onFailure = { error ->
                Timber.w(error, "Failed to push kosync progress for ${book.title}")
                SourceOutcome.Failure(error)
            }
        )
    }

    private suspend fun pushGrimmory(
        server: Server,
        book: Book,
        percentage: Float
    ): SourceOutcome<Unit> {
        val grimmoryBookId = book.grimmoryBookId ?: return SourceOutcome.Skipped
        if (!server.isGrimmory || !grimmoryTokenManager.isLoggedIn(server.id)) return SourceOutcome.Skipped
        return runCatchingCancellable {
            val pct = percentage.toGrimmoryPercentage()
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrNull()
            val fileId = detail?.primaryFile?.id

            grimmoryClient.pushProgress(
                baseUrl = server.url,
                serverId = server.id,
                request = GrimmoryProgressRequest(
                    bookId = grimmoryBookId,
                    fileProgress = fileId?.let { GrimmoryFileProgress(bookFileId = it, progressPercent = pct) },
                    epubProgress = GrimmoryEpubProgress(cfi = PLACEHOLDER_CFI, percentage = pct)
                )
            ).getOrThrow()
            Timber.d("Pushed Grimmory progress for ${book.title}: $pct%")
        }.fold(
            onSuccess = { SourceOutcome.Ok(Unit) },
            onFailure = { error ->
                Timber.w(error, "Failed to push Grimmory progress for ${book.title}")
                SourceOutcome.Failure(error)
            }
        )
    }

    companion object {
        const val PLACEHOLDER_CFI = "epubcfi(/6/2)"
    }
}
