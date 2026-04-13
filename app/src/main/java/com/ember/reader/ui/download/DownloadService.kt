package com.ember.reader.ui.download

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.DownloadProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.ui.common.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class DownloadRequest(
    val bookId: String,
    val serverId: Long
)

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var bookRepository: BookRepository

    @Inject lateinit var serverRepository: ServerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val requestChannel = Channel<DownloadRequest>(Channel.UNLIMITED)
    private var currentJob: Job? = null
    private var currentBookTitle: String? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("DownloadService: onCreate")
        scope.launch { processQueue() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                Timber.d("DownloadService: cancel requested")
                currentJob?.cancel()
                return START_NOT_STICKY
            }
        }

        val bookId = intent?.getStringExtra(EXTRA_BOOK_ID)
        val serverId = intent?.getLongExtra(EXTRA_SERVER_ID, -1L) ?: -1L

        if (bookId != null && serverId >= 0) {
            Timber.d("DownloadService: enqueue bookId=%s serverId=%d", bookId, serverId)
            _downloadingBookIds.value = _downloadingBookIds.value + bookId
            requestChannel.trySend(DownloadRequest(bookId, serverId))
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun processQueue() {
        for (request in requestChannel) {
            val book = bookRepository.getById(request.bookId)
            val server = serverRepository.getById(request.serverId)

            if (book == null || server == null || book.isDownloaded) {
                _downloadingBookIds.value = _downloadingBookIds.value - request.bookId
                continue
            }

            currentBookTitle = book.title
            startForeground(
                NotificationHelper.NOTIFICATION_ID_DOWNLOAD_PROGRESS,
                NotificationHelper.buildDownloadProgressNotification(this, book.title, cancelIntent())
            )

            currentJob = scope.launch {
                executeDownload(book, server)
            }
            currentJob?.join()
            currentJob = null

            _downloadingBookIds.value = _downloadingBookIds.value - request.bookId
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun executeDownload(book: Book, server: Server) {
        var lastUpdateTime = 0L

        try {
            val result = bookRepository.downloadBook(book, server) { progress ->
                val now = System.currentTimeMillis()
                if (now - lastUpdateTime < 500) return@downloadBook
                lastUpdateTime = now

                val percent = progress.totalBytes?.let {
                    if (it > 0) ((progress.bytesDownloaded * 100) / it).toInt() else 0
                } ?: 0

                val progressText = buildProgressText(progress)
                NotificationHelper.updateDownloadProgress(
                    this, book.title, percent, progressText, cancelIntent()
                )
            }

            result.onSuccess {
                Timber.d("DownloadService: download complete for %s", book.title)
                NotificationHelper.dismissDownloadProgress(this)
                NotificationHelper.showDownloadComplete(this, book.title, book.id)
            }.onFailure { error ->
                Timber.e(error, "DownloadService: download failed for %s", book.title)
                NotificationHelper.dismissDownloadProgress(this)
                NotificationHelper.showDownloadFailed(this, book.title)
            }
        } catch (e: CancellationException) {
            Timber.d("DownloadService: download cancelled for %s", book.title)
            NotificationHelper.dismissDownloadProgress(this)
        }
    }

    private fun buildProgressText(progress: DownloadProgress): String {
        val trackCount = progress.trackCount
        val trackIndex = progress.trackIndex
        val trackInfo = if (trackCount != null && trackCount > 1 && trackIndex != null) {
            "Track ${trackIndex + 1} of $trackCount \u00B7 "
        } else {
            ""
        }

        val totalBytes = progress.totalBytes
        val sizeInfo = if (totalBytes != null && totalBytes > 0) {
            "${formatBytes(progress.bytesDownloaded)} / ${formatBytes(totalBytes)}"
        } else {
            formatBytes(progress.bytesDownloaded)
        }

        return "$trackInfo$sizeInfo"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    private fun cancelIntent(): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        Timber.d("DownloadService: onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_CANCEL = "com.ember.reader.CANCEL_DOWNLOAD"
        private const val EXTRA_BOOK_ID = "book_id"
        private const val EXTRA_SERVER_ID = "server_id"

        private val _downloadingBookIds = MutableStateFlow<Set<String>>(emptySet())
        val downloadingBookIds: StateFlow<Set<String>> = _downloadingBookIds.asStateFlow()

        fun start(context: Context, bookId: String, serverId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            context.startForegroundService(intent)
        }
    }
}
