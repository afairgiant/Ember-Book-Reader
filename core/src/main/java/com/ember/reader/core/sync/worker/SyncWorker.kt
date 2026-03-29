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
import com.ember.reader.core.repository.AppPreferencesRepository
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
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val appPreferencesRepository: AppPreferencesRepository
) : CoroutineWorker(context, params) {

    private var syncPushed = 0
    private var syncPulled = 0
    private var syncErrors = 0

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker: starting progress sync")
        syncPushed = 0
        syncPulled = 0
        syncErrors = 0

        val servers = serverRepository.getAll()
        if (servers.isEmpty()) return Result.success()

        for (server in servers) {
            runCatching {
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
                        syncPulled += bookHashPairs.size
                        Timber.d("SyncWorker: kosync pulled for ${bookHashPairs.size} books on ${server.name}")
                    }
                }

                // Grimmory native push/pull
                if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
                    syncGrimmoryProgress(server)

                    // Auto-download books marked as Reading
                    if (appPreferencesRepository.getAutoDownloadReading()) {
                        autoDownloadReadingBooks(server)
                    }
                }
            }.onFailure {
                syncErrors++
                Timber.w(it, "SyncWorker: sync failed for ${server.name}")
            }
        }

        Timber.d("SyncWorker: sync complete — pushed=$syncPushed pulled=$syncPulled errors=$syncErrors")
        showSyncNotification()
        return Result.success()
    }

    private suspend fun showSyncNotification() {
        if (!appPreferencesRepository.getSyncNotifications()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val parts = mutableListOf<String>()
        if (syncPushed > 0) parts.add("$syncPushed pushed")
        if (syncPulled > 0) parts.add("$syncPulled pulled")
        if (syncErrors > 0) parts.add("$syncErrors errors")
        val text = if (parts.isNotEmpty()) parts.joinToString(" · ") else "Up to date"

        val notification = NotificationCompat.Builder(applicationContext, "ember_sync")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Sync Complete")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify("sync".hashCode(), notification)
    }

    private suspend fun syncGrimmoryProgress(server: com.ember.reader.core.model.Server) {
        val downloadedBooks = bookRepository.getDownloadedBooksForServer(server.id)

        // Pull continue-reading from Grimmory first to get server state
        val serverProgress = mutableMapOf<Long, Float>() // grimmoryBookId -> percentage (0-1)
        runCatching {
            val continueReading = grimmoryClient.getContinueReading(
                baseUrl = server.url,
                serverId = server.id
            ).getOrThrow()

            for (summary in continueReading) {
                val rawPct = summary.readProgress ?: continue
                if (rawPct <= 0f) continue
                val percentage = if (rawPct > 1f) rawPct / 100f else rawPct
                serverProgress[summary.id] = percentage

                // Pull if server is meaningfully ahead of local
                val localBook = downloadedBooks.find { it.grimmoryBookId == summary.id }
                if (localBook != null) {
                    val localProgress = readingProgressRepository.getByBookId(localBook.id)
                    val localPct = localProgress?.percentage ?: 0f
                    // Only pull if server is at least 1% ahead — avoids counting rounding diffs
                    if (percentage > localPct + 0.01f) {
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
                        syncPulled++
                        Timber.d("SyncWorker: pulled Grimmory progress for ${localBook.title}: ${(percentage * 100).toInt()}% (local was ${(localPct * 100).toInt()}%)")
                    }
                }
            }
        }.onFailure {
            Timber.w(it, "SyncWorker: failed to pull Grimmory continue-reading")
        }

        // Push local progress to Grimmory — only if local is ahead of server
        for (book in downloadedBooks) {
            val grimmoryBookId = book.grimmoryBookId ?: continue
            val progress = readingProgressRepository.getByBookId(book.id) ?: continue
            if (progress.percentage <= 0f) continue

            val serverPct = serverProgress[grimmoryBookId] ?: 0f
            // Only push if local is meaningfully ahead (>0.5% difference)
            // Only push if local is at least 1% ahead of server
            if (progress.percentage <= serverPct + 0.01f) continue

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
                readingProgressRepository.markSynced(book.id)
                syncPushed++
                Timber.d("SyncWorker: pushed Grimmory progress for ${book.title}: ${pct}% (server was ${(serverPct * 100).toInt()}%)")
            }.onFailure {
                Timber.w(it, "SyncWorker: failed to push Grimmory progress for ${book.title}")
            }
        }
    }

    private suspend fun autoDownloadReadingBooks(server: com.ember.reader.core.model.Server) {
        val downloadedTitles = mutableListOf<String>()
        runCatching {
            val continueReading = grimmoryClient.getContinueReading(
                baseUrl = server.url,
                serverId = server.id
            ).getOrThrow()

            for (summary in continueReading) {
                val opdsEntryId = "urn:booklore:book:${summary.id}"
                val existing = bookRepository.getByOpdsEntryId(opdsEntryId, server.id)

                // Skip if already downloaded
                if (existing?.isDownloaded == true) continue

                // Find or create the book entry, then download
                val book = existing ?: run {
                    // Book not in DB yet — create a minimal entry for download
                    val newBook = com.ember.reader.core.model.Book(
                        id = java.util.UUID.randomUUID().toString(),
                        serverId = server.id,
                        opdsEntryId = opdsEntryId,
                        title = summary.title,
                        author = summary.authors.firstOrNull(),
                        downloadUrl = "/api/v1/opds/${summary.id}/download",
                        format = when (summary.primaryFileType?.uppercase()) {
                            "PDF" -> com.ember.reader.core.model.BookFormat.PDF
                            else -> com.ember.reader.core.model.BookFormat.EPUB
                        },
                        coverUrl = "${com.ember.reader.core.network.serverOrigin(server.url)}/api/v1/media/book/${summary.id}/cover"
                    )
                    bookRepository.addLocalBook(newBook)
                    newBook
                }

                bookRepository.downloadBook(book, server).onSuccess { downloadedBook ->
                    downloadedTitles.add(summary.title)
                    Timber.d("SyncWorker: auto-downloaded '${summary.title}'")

                    // Pull reading progress for the newly downloaded book
                    val rawPct = summary.readProgress
                    if (rawPct != null && rawPct > 0f) {
                        val percentage = if (rawPct > 1f) rawPct / 100f else rawPct
                        readingProgressRepository.applyRemoteProgress(
                            com.ember.reader.core.model.ReadingProgress(
                                bookId = downloadedBook.id,
                                serverId = server.id,
                                percentage = percentage,
                                lastReadAt = java.time.Instant.now(),
                                syncedAt = java.time.Instant.now(),
                                needsSync = false
                            )
                        )
                        Timber.d("SyncWorker: applied progress ${(percentage * 100).toInt()}% for '${summary.title}'")
                    }
                }.onFailure {
                    Timber.w(it, "SyncWorker: failed to auto-download '${summary.title}'")
                }
            }
        }.onFailure {
            Timber.w(it, "SyncWorker: auto-download reading books failed")
        }

        // Notify about auto-downloaded books
        if (downloadedTitles.isNotEmpty()) {
            showAutoDownloadNotification(downloadedTitles)
        }
    }

    private fun showAutoDownloadNotification(titles: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = if (titles.size == 1) "Book Auto-Downloaded" else "${titles.size} Books Auto-Downloaded"
        val text = titles.joinToString(", ")

        val builder = NotificationCompat.Builder(applicationContext, "ember_downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (titles.size > 1) {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            titles.forEach { style.addLine(it) }
            builder.setStyle(style)
        }

        NotificationManagerCompat.from(applicationContext).notify("auto_dl".hashCode(), builder.build())
    }

    companion object {
        const val WORK_NAME = "ember_progress_sync"
    }
}
