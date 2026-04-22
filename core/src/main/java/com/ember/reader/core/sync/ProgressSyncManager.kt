package com.ember.reader.core.sync

import com.ember.reader.core.coroutine.runCatchingCancellable
import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryEpubProgress
import com.ember.reader.core.grimmory.GrimmoryFileProgress
import com.ember.reader.core.grimmory.GrimmoryHttpException
import com.ember.reader.core.grimmory.GrimmoryUpdateProgressRequest
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.toGrimmoryPercentage
import com.ember.reader.core.repository.BookRepository
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

/**
 * Why a per-source sync attempt was skipped. Skipping is not a failure — it
 * means the channel isn't in play for this book/server, and the UI should
 * report it differently from a real error.
 */
enum class SkipReason {
    NoLocalProgress,
    NoFileHash,
    NoKosyncCreds,
    KosyncDisabled,
    NoGrimmoryBookId,
    ServerNotGrimmory,
    GrimmoryNotLoggedIn
}

/**
 * Per-source sync outcome. Exposed so callers can distinguish between
 * "succeeded", "skipped for reason X", and "failed with error Y" —
 * collapsing these to a single boolean hides real failures from users
 * (e.g. Grimmory pushed but kosync silently 401'd).
 */
sealed interface SourceOutcome<out T> {
    data class Ok<T>(val value: T?) : SourceOutcome<T>
    data class Skipped(val reason: SkipReason) : SourceOutcome<Nothing>
    data class Failure(val error: Throwable) : SourceOutcome<Nothing>
}

/**
 * Result of a combined kosync + Grimmory progress push. Both channels run
 * concurrently and either succeed, skip (not applicable), or fail.
 */
data class PushResult(
    val kosync: SourceOutcome<Unit>,
    val grimmory: SourceOutcome<Unit>
) {
    val anySucceeded: Boolean get() = kosync is SourceOutcome.Ok || grimmory is SourceOutcome.Ok
    val anyFailed: Boolean get() = kosync is SourceOutcome.Failure || grimmory is SourceOutcome.Failure
    val allFailed: Boolean get() = kosync is SourceOutcome.Failure && grimmory is SourceOutcome.Failure
    val allSkipped: Boolean get() = kosync is SourceOutcome.Skipped && grimmory is SourceOutcome.Skipped

    fun firstActionableError(): Throwable? {
        val errors = listOfNotNull(
            (kosync as? SourceOutcome.Failure)?.error,
            (grimmory as? SourceOutcome.Failure)?.error
        )
        return errors.firstOrNull { it is GrimmoryAuthExpiredException }
            ?: errors.firstOrNull { it is GrimmoryHttpException }
            ?: errors.firstOrNull()
    }

    companion object {
        /** Neither source had anything to push (no local progress yet). */
        val NothingToPush = PushResult(
            kosync = SourceOutcome.Skipped(SkipReason.NoLocalProgress),
            grimmory = SourceOutcome.Skipped(SkipReason.NoLocalProgress)
        )
    }
}

@Singleton
class ProgressSyncManager @Inject constructor(
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookRepository: BookRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val syncStatusRepository: SyncStatusRepository
) {

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
     * concurrently. Returns a per-source [PushResult] so callers can tell
     * "succeeded", "skipped because not applicable", and "failed" apart —
     * hiding a channel failure behind a blanket boolean means the user
     * thinks they're synced when they aren't.
     */
    suspend fun pushProgress(server: Server, book: Book): PushResult {
        val progress = readingProgressRepository.getByBookId(book.id) ?: return PushResult.NothingToPush
        if (progress.percentage <= 0f) return PushResult.NothingToPush

        return coroutineScope {
            val kosyncDeferred = async { pushKosync(server, book) }
            val grimmoryDeferred = async { pushGrimmory(server, book, progress.percentage) }
            val kosync = kosyncDeferred.await()
            val grimmory = grimmoryDeferred.await()

            reportStatus(server.id, listOf(kosync, grimmory))

            PushResult(kosync = kosync, grimmory = grimmory)
        }
    }

    /**
     * Classify a per-server sync attempt and report it.
     *
     * - Any [SourceOutcome.Failure] → unhealthy. Even when another channel
     *   succeeded, the user deserves to know the failed channel is drifting;
     *   hiding that behind "at least something worked" is how stale kosync
     *   state went unnoticed until the user spot-checked the server.
     * - Otherwise, any success → healthy.
     * - All skipped → nothing to report (the server isn't in play for this op).
     */
    private suspend fun reportStatus(serverId: Long, outcomes: List<SourceOutcome<*>>) {
        val failures = outcomes.filterIsInstance<SourceOutcome.Failure>().map { it.error }
        val hasSuccess = outcomes.any { it is SourceOutcome.Ok<*> }
        when {
            failures.isNotEmpty() -> {
                val error = failures.firstOrNull { it is GrimmoryAuthExpiredException }
                    ?: failures.firstOrNull { it is GrimmoryHttpException }
                    ?: failures.first()
                syncStatusRepository.reportFailure(serverId, error)
            }
            hasSuccess -> syncStatusRepository.reportSuccess(serverId)
            else -> Unit // all skipped — not a sync event
        }
    }

    private suspend fun pullKosync(server: Server, book: Book): SourceOutcome<RemoteSyncResult> {
        if (!server.kosyncEnabled) {
            Timber.d("Sync pull: skipping kosync for '${book.title}' — kosync disabled on server")
            return SourceOutcome.Skipped(SkipReason.KosyncDisabled)
        }
        val fileHash = book.fileHash
        if (fileHash == null) {
            Timber.d("Sync pull: skipping kosync for '${book.title}' — no fileHash")
            return SourceOutcome.Skipped(SkipReason.NoFileHash)
        }
        if (server.kosyncUsername.isBlank()) {
            Timber.d("Sync pull: skipping kosync for '${book.title}' — no kosync credentials")
            return SourceOutcome.Skipped(SkipReason.NoKosyncCreds)
        }
        Timber.d("Sync pull: trying kosync for '${book.title}' hash=$fileHash")
        val first = readingProgressRepository.pullKosyncProgress(server, book.id, fileHash)
        val retried = first.recoverKosync404(book.id, fileHash) { refreshedHash ->
            Timber.i("Sync pull: retrying kosync for '${book.title}' with refreshed hash=$refreshedHash")
            readingProgressRepository.pullKosyncProgress(server, book.id, refreshedHash)
        }
        return retried.fold(
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
            return SourceOutcome.Skipped(SkipReason.NoGrimmoryBookId)
        }
        if (!server.isGrimmory) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — server not Grimmory")
            return SourceOutcome.Skipped(SkipReason.ServerNotGrimmory)
        }
        if (!grimmoryTokenManager.isLoggedIn(server.id)) {
            Timber.d("Sync pull: skipping Grimmory for '${book.title}' — not logged in to Grimmory")
            return SourceOutcome.Skipped(SkipReason.GrimmoryNotLoggedIn)
        }
        Timber.d("Sync pull: trying Grimmory for '${book.title}' grimmoryBookId=$grimmoryBookId")
        return runCatchingCancellable {
            val progress = grimmoryClient.getAppBookProgress(server.url, server.id, grimmoryBookId).getOrThrow()
            // Grimmory stores progress with per-source scales:
            //  - epubProgress.percentage:     0-100 (what Ember + web reader push)
            //  - koreaderProgress.percentage: 0-1   (KOReader protocol)
            //  - readProgress:                falls through to whichever stored value exists,
            //                                 so scale is ambiguous — we pick typed sources first.
            val pct = resolveGrimmoryPercentage(progress)
            Timber.d("Sync pull: Grimmory resolved pct=$pct (readProgress=${progress.readProgress} epub=${progress.epubProgress?.percentage} koreader=${progress.koreaderProgress?.percentage}) for '${book.title}'")
            if (pct == null || pct <= 0f) {
                null
            } else {
                RemoteSyncResult(
                    progress = ReadingProgress.fromRemote(book.id, server.id, pct),
                    source = progress.koreaderProgress?.device ?: "Grimmory"
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
        if (!server.kosyncEnabled) {
            Timber.d("Sync push: skipping kosync for '${book.title}' — kosync disabled on server")
            return SourceOutcome.Skipped(SkipReason.KosyncDisabled)
        }
        val fileHash = book.fileHash
        if (fileHash == null) {
            Timber.d("Sync push: skipping kosync for '${book.title}' — no fileHash")
            return SourceOutcome.Skipped(SkipReason.NoFileHash)
        }
        if (server.kosyncUsername.isBlank()) {
            Timber.d("Sync push: skipping kosync for '${book.title}' — no kosync credentials")
            return SourceOutcome.Skipped(SkipReason.NoKosyncCreds)
        }
        Timber.d("Sync push: pushing kosync for '${book.title}' hash=$fileHash user=${server.kosyncUsername}")
        val first = readingProgressRepository.pushKosyncProgress(server, book.id, fileHash)
        val retried = first.recoverKosync404(book.id, fileHash) { refreshedHash ->
            Timber.i("Sync push: retrying kosync for '${book.title}' with refreshed hash=$refreshedHash")
            readingProgressRepository.pushKosyncProgress(server, book.id, refreshedHash)
        }
        return retried.fold(
            onSuccess = {
                Timber.d("Sync push: kosync OK for '${book.title}'")
                SourceOutcome.Ok(Unit)
            },
            onFailure = { error ->
                Timber.w(error, "Sync push: kosync FAILED for '${book.title}' (hash=$fileHash)")
                SourceOutcome.Failure(error)
            }
        )
    }

    /**
     * On a kosync 404 (server doesn't recognise our hash — almost always stale
     * after a metadata edit rewrote the EPUB), re-download the file so the
     * partial-MD5 re-computes to whatever Grimmory now has stored, then retry
     * once with the new hash. If the refresh fails or the hash didn't actually
     * change (book genuinely unknown to server), propagate the original 404.
     */
    private suspend fun <T> Result<T>.recoverKosync404(
        bookId: String,
        oldHash: String,
        retry: suspend (refreshedHash: String) -> Result<T>
    ): Result<T> {
        val err = exceptionOrNull() as? KosyncHttpException ?: return this
        if (err.statusCode != 404) return this

        // Check if a concurrent path (e.g. pull running alongside push) already
        // refreshed the file, so we don't download the same book twice.
        val alreadyRefreshed = bookRepository.getById(bookId)?.fileHash
        if (alreadyRefreshed != null && alreadyRefreshed != oldHash) {
            Timber.i("Kosync 404: hash already refreshed by another path ($oldHash -> $alreadyRefreshed); retrying")
            return retry(alreadyRefreshed)
        }

        val refreshed = bookRepository.refreshDownloadedFile(bookId).getOrElse { refreshErr ->
            Timber.w(refreshErr, "Kosync 404: refresh failed, keeping original 404 for book $bookId")
            return this
        }
        val newHash = refreshed.fileHash
        if (newHash == null || newHash == oldHash) {
            Timber.w("Kosync 404: hash unchanged after refresh (book not on server), not retrying")
            return this
        }
        Timber.i("Kosync 404 recovered: hash $oldHash -> $newHash for book $bookId; retrying")
        return retry(newHash)
    }

    private suspend fun pushGrimmory(
        server: Server,
        book: Book,
        percentage: Float
    ): SourceOutcome<Unit> {
        val grimmoryBookId = book.grimmoryBookId
            ?: return SourceOutcome.Skipped(SkipReason.NoGrimmoryBookId)
        if (!server.isGrimmory) return SourceOutcome.Skipped(SkipReason.ServerNotGrimmory)
        if (!grimmoryTokenManager.isLoggedIn(server.id)) {
            return SourceOutcome.Skipped(SkipReason.GrimmoryNotLoggedIn)
        }
        return runCatchingCancellable {
            val pct = percentage.toGrimmoryPercentage()
            val detail = grimmoryClient.getBookDetail(server.url, server.id, grimmoryBookId).getOrNull()
            val fileId = detail?.primaryFile?.id

            Timber.d(
                "Sync push: Grimmory request for '${book.title}' bookId=$grimmoryBookId fileId=$fileId pct=$pct cfi=$PLACEHOLDER_CFI"
            )
            grimmoryClient.putAppBookProgress(
                baseUrl = server.url,
                serverId = server.id,
                grimmoryBookId = grimmoryBookId,
                request = GrimmoryUpdateProgressRequest(
                    fileProgress = fileId?.let { GrimmoryFileProgress(bookFileId = it, progressPercent = pct) },
                    epubProgress = GrimmoryEpubProgress(cfi = PLACEHOLDER_CFI, percentage = pct)
                )
            ).getOrThrow()
            Timber.d("Sync push: Grimmory OK for '${book.title}' pct=$pct%")
        }.fold(
            onSuccess = { SourceOutcome.Ok(Unit) },
            onFailure = { error ->
                Timber.w(error, "Sync push: Grimmory FAILED for '${book.title}' (bookId=$grimmoryBookId)")
                SourceOutcome.Failure(error)
            }
        )
    }

    /**
     * Converts Grimmory's mixed-scale progress fields to Ember's 0-1 scale.
     *
     * Priority:
     * 1. `koreaderProgress.percentage` (always 0-1, KOReader protocol)
     * 2. `epubProgress.percentage` (always 0-100, what Ember/web reader push — divide)
     * 3. `readProgress` (ambiguous: falls through to whichever source last wrote, so
     *    scale depends on that source. Heuristic: values > 1 are 0-100.)
     */
    private fun resolveGrimmoryPercentage(
        progress: com.ember.reader.core.grimmory.GrimmoryAppBookProgress
    ): Float? {
        progress.koreaderProgress?.percentage?.takeIf { it > 0f }?.let {
            return it.coerceIn(0f, 1f)
        }
        progress.epubProgress?.percentage?.takeIf { it > 0f }?.let {
            return (it / 100f).coerceIn(0f, 1f)
        }
        val raw = progress.readProgress ?: return null
        if (raw <= 0f) return null
        return if (raw > 1f) {
            (raw / 100f).coerceIn(0f, 1f)
        } else {
            raw
        }
    }

    companion object {
        const val PLACEHOLDER_CFI = "epubcfi(/6/2)"
    }
}
