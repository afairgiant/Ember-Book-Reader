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
import com.ember.reader.core.grimmory.GrimmoryBookSummary
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.normalizeGrimmoryPercentage
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.sync.BookmarkSyncManager
import com.ember.reader.core.sync.HighlightSyncManager
import com.ember.reader.core.sync.ProgressSyncManager
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
    private val appPreferencesRepository: AppPreferencesRepository,
    private val progressSyncManager: ProgressSyncManager,
    private val highlightSyncManager: HighlightSyncManager,
    private val bookmarkSyncManager: BookmarkSyncManager,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val bookDao: com.ember.reader.core.database.dao.BookDao,
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
                    readingProgressRepository.pushUnsyncedKosyncProgress(server) { bookId ->
                        bookRepository.getById(bookId)?.fileHash
                    }

                    val downloadedBooks = bookRepository.getDownloadedBooksForServer(server.id)
                    if (downloadedBooks.isNotEmpty()) {
                        val bookHashPairs = downloadedBooks.mapNotNull { book ->
                            val hash = book.fileHash ?: return@mapNotNull null
                            book.id to hash
                        }
                        readingProgressRepository.pullKosyncProgressForAllBooks(server, bookHashPairs)
                        Timber.d("SyncWorker: kosync synced for ${bookHashPairs.size} books on ${server.name}")
                    }
                }

                // Grimmory native push/pull
                if (server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id)) {
                    syncGrimmoryProgress(server)

                    // Auto-download books marked as Reading
                    if (appPreferencesRepository.getAutoDownloadReading()) {
                        autoDownloadReadingBooks(server)
                    }

                    // Highlight & bookmark sync — sync all books from this server
                    val syncHighlights = appPreferencesRepository.getSyncHighlights()
                    val syncBookmarks = appPreferencesRepository.getSyncBookmarks()
                    if (syncHighlights || syncBookmarks) {
                        val serverBooks = bookDao.getDownloadedBooksForServer(server.id)
                        for (bookEntity in serverBooks) {
                            val gid = bookEntity.opdsEntryId
                                ?.substringAfterLast(":")
                                ?.toLongOrNull() ?: continue
                            if (syncHighlights) {
                                runCatching { highlightSyncManager.syncHighlightsForBook(server, bookEntity.id, gid) }
                                    .onFailure { Timber.e(it, "SyncWorker: highlight sync failed for book=%s", bookEntity.id) }
                            }
                            if (syncBookmarks) {
                                runCatching { bookmarkSyncManager.syncBookmarksForBook(server, bookEntity.id, gid) }
                                    .onFailure { Timber.e(it, "SyncWorker: bookmark sync failed for book=%s", bookEntity.id) }
                            }
                        }
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

    /**
     * Syncs Grimmory progress for all downloaded books using ProgressSyncManager.
     * For each book: pulls best remote progress (checking epubProgress first),
     * and pushes local progress if ahead of server.
     */
    private suspend fun syncGrimmoryProgress(server: Server) {
        val downloadedBooks = bookRepository.getDownloadedBooksForServer(server.id)

        for (book in downloadedBooks) {
            if (book.grimmoryBookId == null) continue

            runCatching {
                val localProgress = readingProgressRepository.getByBookId(book.id)
                val localPct = localProgress?.percentage ?: 0f

                // Pull — uses getBookDetail which reads epubProgress.percentage correctly
                val remote = progressSyncManager.pullBestProgress(server, book)
                if (remote != null && remote.progress.percentage > localPct + 0.01f) {
                    readingProgressRepository.applyRemoteProgress(remote.progress)
                    syncPulled++
                    Timber.d("SyncWorker: pulled progress for ${book.title}: ${(remote.progress.percentage * 100).toInt()}% from ${remote.source} (local was ${(localPct * 100).toInt()}%)")
                }

                // Push — only if local is meaningfully ahead
                if (localPct > 0f) {
                    val serverPct = remote?.progress?.percentage ?: 0f
                    if (localPct > serverPct + 0.01f) {
                        val pushed = progressSyncManager.pushProgress(server, book)
                        if (pushed) {
                            syncPushed++
                            Timber.d("SyncWorker: pushed progress for ${book.title}: ${(localPct * 100).toInt()}% (server was ${(serverPct * 100).toInt()}%)")
                        }
                    }
                }
            }.onFailure {
                Timber.w(it, "SyncWorker: sync failed for ${book.title}")
            }
        }
    }

    private suspend fun autoDownloadReadingBooks(server: Server) {
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
                    val remote = progressSyncManager.pullBestProgress(server, downloadedBook)
                    if (remote != null) {
                        readingProgressRepository.applyRemoteProgress(remote.progress)
                        Timber.d("SyncWorker: applied progress ${(remote.progress.percentage * 100).toInt()}% for '${summary.title}'")
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
