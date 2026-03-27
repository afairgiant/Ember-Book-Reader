package com.ember.reader.core.sync.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryEpubProgress
import com.ember.reader.core.grimmory.GrimmoryProgressRequest
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookRepository: BookRepository,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker: starting progress sync")

        val servers = serverRepository.getAll()
        if (servers.isEmpty()) return Result.success()

        for (server in servers) {
            // Kosync push/pull
            if (server.kosyncUsername.isNotBlank()) {
                readingProgressRepository.syncUnsyncedProgress(server) { bookId ->
                    bookRepository.getById(bookId)?.fileHash
                }

                val downloadedBooks = bookRepository.getDownloadedBooksForServer(server.id)
                if (downloadedBooks.isNotEmpty()) {
                    val bookHashPairs = downloadedBooks.mapNotNull { book ->
                        val hash = book.fileHash ?: return@mapNotNull null
                        book.id to hash
                    }
                    readingProgressRepository.pullProgressForAllBooks(server, bookHashPairs)
                    Timber.d("SyncWorker: kosync pulled for ${bookHashPairs.size} books on ${server.name}")
                }
            }

            // Grimmory native push/pull
            if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
                syncGrimmoryProgress(server)
            }
        }

        Timber.d("SyncWorker: sync complete")
        showSyncNotification()
        return Result.success()
    }

    private fun showSyncNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(applicationContext, "ember_sync")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Sync Complete")
            .setContentText("Reading progress synced")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify("sync".hashCode(), notification)
    }

    private suspend fun syncGrimmoryProgress(server: com.ember.reader.core.model.Server) {
        // Push unsynced local progress to Grimmory
        val downloadedBooks = bookRepository.getDownloadedBooksForServer(server.id)
        for (book in downloadedBooks) {
            val grimmoryBookId = book.grimmoryBookId ?: continue
            val progress = readingProgressRepository.getByBookId(book.id) ?: continue
            if (!progress.needsSync) continue

            runCatching {
                val pct = kotlin.math.round(progress.percentage * 1000f) / 10f
                grimmoryClient.pushProgress(
                    baseUrl = server.url,
                    serverId = server.id,
                    request = GrimmoryProgressRequest(
                        bookId = grimmoryBookId,
                        epubProgress = GrimmoryEpubProgress(
                            cfi = "epubcfi(/6/2)",
                            percentage = pct
                        )
                    )
                ).getOrThrow()
                Timber.d("SyncWorker: pushed Grimmory progress for ${book.title}")
            }.onFailure {
                Timber.w(it, "SyncWorker: failed to push Grimmory progress for ${book.title}")
            }
        }

        // Pull continue-reading from Grimmory
        runCatching {
            val continueReading = grimmoryClient.getContinueReading(
                baseUrl = server.url,
                serverId = server.id
            ).getOrThrow()

            for (summary in continueReading) {
                val rawPct = summary.readProgress ?: continue
                if (rawPct <= 0f) continue
                // Grimmory returns 0-100, Ember uses 0-1
                val percentage = if (rawPct > 1f) rawPct / 100f else rawPct

                // Find matching local book by grimmoryBookId
                val localBook = downloadedBooks.find { it.grimmoryBookId == summary.id }
                if (localBook != null) {
                    val localProgress = readingProgressRepository.getByBookId(localBook.id)
                    if (localProgress == null || percentage > localProgress.percentage) {
                        readingProgressRepository.applyRemoteProgress(
                            com.ember.reader.core.model.ReadingProgress(
                                bookId = localBook.id,
                                serverId = server.id,
                                percentage = percentage,
                                lastReadAt = java.time.Instant.now(),
                                syncedAt = java.time.Instant.now(),
                                needsSync = false
                            )
                        )
                        Timber.d("SyncWorker: pulled Grimmory progress for ${localBook.title}: ${(percentage * 100).toInt()}%")
                    }
                }
            }
        }.onFailure {
            Timber.w(it, "SyncWorker: failed to pull Grimmory continue-reading")
        }
    }

    companion object {
        const val WORK_NAME = "ember_progress_sync"
    }
}
