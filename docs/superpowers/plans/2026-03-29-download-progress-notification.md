# Download Progress Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persistent foreground service notification with real-time download progress, cancel support, surviving screen navigation and phone lock.

**Architecture:** Downloads move from ViewModels to a DownloadService foreground service. Progress callbacks are threaded from Ktor byte-read loops through BookRepository up to the service, which updates a notification. A companion-object StateFlow lets ViewModels observe download state without binding.

**Tech Stack:** Android Foreground Service, NotificationCompat, Kotlin Coroutines Channels, Ktor streaming

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `core/.../model/DownloadProgress.kt` | Create | Progress data class |
| `core/.../grimmory/GrimmoryClient.kt` | Modify | Add onProgress to download methods |
| `core/.../opds/OpdsClient.kt` | Modify | Add onProgress to downloadBookToFile |
| `core/.../repository/BookRepository.kt` | Modify | Thread progress through to callers |
| `app/.../ui/common/NotificationHelper.kt` | Modify | Add progress notification builders |
| `app/.../ui/download/DownloadService.kt` | Create | Foreground service owning downloads |
| `app/.../ui/book/BookDetailViewModel.kt` | Modify | Delegate to DownloadService |
| `app/.../ui/library/LibraryViewModel.kt` | Modify | Delegate to DownloadService |
| `app/src/main/AndroidManifest.xml` | Modify | Declare service + permission |

---

### Task 1: DownloadProgress data class

**Files:**
- Create: `core/src/main/java/com/ember/reader/core/model/DownloadProgress.kt`

- [ ] **Step 1: Create DownloadProgress.kt**

```kotlin
package com.ember.reader.core.model

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val trackIndex: Int? = null,
    val trackCount: Int? = null,
    val trackBytesDownloaded: Long? = null,
    val trackTotalBytes: Long? = null,
)
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/model/DownloadProgress.kt
git commit -m "feat: add DownloadProgress data class"
```

---

### Task 2: Add progress callbacks to download clients

**Files:**
- Modify: `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryClient.kt:121-150` (downloadBook)
- Modify: `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryClient.kt:200-223` (downloadFromUrl)
- Modify: `core/src/main/java/com/ember/reader/core/opds/OpdsClient.kt:106-133` (downloadBookToFile)

- [ ] **Step 1: Modify GrimmoryClient.downloadBook() to accept and call onProgress**

Change the method signature and byte-read loop. The `Content-Length` header provides total size. Add `import io.ktor.http.contentLength` if not present.

```kotlin
suspend fun downloadBook(
    baseUrl: String,
    serverId: Long,
    grimmoryBookId: Long,
    destination: File,
    onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
): Result<Unit> = withAuth(baseUrl, serverId) { token ->
    httpClient.prepareGet("${serverOrigin(baseUrl)}/api/v1/books/$grimmoryBookId/download") {
        header("Authorization", "Bearer $token")
        timeout {
            requestTimeoutMillis = 600_000
            socketTimeoutMillis = 120_000
        }
    }.execute { response ->
        if (!response.status.isSuccess()) {
            error("Download failed: ${response.status}")
        }
        val totalBytes = response.contentLength()
        val channel = response.bodyAsChannel()
        withContext(Dispatchers.IO) {
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                while (!channel.isClosedForRead) {
                    val bytes = channel.readAvailable(buffer)
                    if (bytes > 0) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        onProgress?.invoke(downloaded, totalBytes)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Modify GrimmoryClient.downloadFromUrl() the same way**

```kotlin
suspend fun downloadFromUrl(
    url: String,
    destination: File,
    onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
): Result<Unit> = runCatching {
    httpClient.prepareGet(url) {
        timeout {
            requestTimeoutMillis = 600_000
            socketTimeoutMillis = 120_000
        }
    }.execute { response ->
        if (!response.status.isSuccess()) {
            error("Download failed: ${response.status}")
        }
        val totalBytes = response.contentLength()
        val channel = response.bodyAsChannel()
        withContext(Dispatchers.IO) {
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                while (!channel.isClosedForRead) {
                    val bytes = channel.readAvailable(buffer)
                    if (bytes > 0) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        onProgress?.invoke(downloaded, totalBytes)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Modify OpdsClient.downloadBookToFile() the same way**

```kotlin
suspend fun downloadBookToFile(
    baseUrl: String,
    username: String,
    password: String,
    downloadPath: String,
    destination: File,
    onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
): Result<Unit> = runCatching {
    val url = resolveUrl(baseUrl, downloadPath)
    httpClient.prepareGet(url) {
        header("Authorization", basicAuth(username, password))
    }.execute { response ->
        if (!response.status.isSuccess()) {
            error("Download failed: ${response.status}")
        }
        val totalBytes = response.contentLength()
        val channel = response.bodyAsChannel()
        withContext(Dispatchers.IO) {
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                while (!channel.isClosedForRead) {
                    val bytes = channel.readAvailable(buffer)
                    if (bytes > 0) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        onProgress?.invoke(downloaded, totalBytes)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Add import if needed**

Add `import io.ktor.http.contentLength` to both `GrimmoryClient.kt` and `OpdsClient.kt` if not already present.

- [ ] **Step 5: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/grimmory/GrimmoryClient.kt core/src/main/java/com/ember/reader/core/opds/OpdsClient.kt
git commit -m "feat: add progress callbacks to download clients"
```

---

### Task 3: Thread progress through BookRepository

**Files:**
- Modify: `core/src/main/java/com/ember/reader/core/repository/BookRepository.kt:217-343`

- [ ] **Step 1: Add onProgress parameter to downloadBook()**

Change the signature at line 217 and pass it through to both Grimmory and OPDS download calls:

```kotlin
suspend fun downloadBook(
    book: Book,
    server: Server,
    onProgress: ((DownloadProgress) -> Unit)? = null,
): Result<Book> = runCatching {
    val grimmoryBookId = book.grimmoryBookId
    Timber.d("Download: isGrimmory=${server.isGrimmory} loggedIn=${grimmoryTokenManager.isLoggedIn(server.id)} grimmoryBookId=$grimmoryBookId format=${book.format}")

    // For audiobooks from Grimmory, check if folder-based and handle accordingly
    if (book.format == com.ember.reader.core.model.BookFormat.AUDIOBOOK &&
        server.isGrimmory && grimmoryTokenManager.isLoggedIn(server.id) && grimmoryBookId != null
    ) {
        return@runCatching downloadAudiobook(book, server, grimmoryBookId, onProgress)
    }

    val downloadUrl = book.downloadUrl
        ?: error("No download URL for book: ${book.title}")

    val extension = when (book.format) {
        com.ember.reader.core.model.BookFormat.EPUB -> "epub"
        com.ember.reader.core.model.BookFormat.PDF -> "pdf"
        com.ember.reader.core.model.BookFormat.AUDIOBOOK -> "m4b"
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
            },
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
            },
        ).getOrThrow()
    }

    // ... rest of validation, hash, metadata extraction unchanged ...
```

Add `import com.ember.reader.core.model.DownloadProgress` to the file.

- [ ] **Step 2: Add onProgress to downloadAudiobook()**

Change signature and wrap per-track progress into overall progress:

```kotlin
private suspend fun downloadAudiobook(
    book: Book,
    server: Server,
    grimmoryBookId: Long,
    onProgress: ((DownloadProgress) -> Unit)? = null,
): Book {
    val infoResult = grimmoryClient.getAudiobookInfo(server.url, server.id, grimmoryBookId)
    infoResult.onFailure { error ->
        Timber.e(error, "Audiobook download: failed to fetch audiobook info for grimmoryBookId=%d", grimmoryBookId)
    }
    val info = infoResult.getOrNull()
    Timber.d("Audiobook download: info=%s folderBased=%s tracks=%d", info != null, info?.folderBased, info?.tracks?.size ?: 0)

    val tracks = info?.tracks
    if (info != null && info.folderBased && !tracks.isNullOrEmpty()) {
        val audiobookDir = File(booksDir, "audiobook_${book.id}").also { it.mkdirs() }
        Timber.d("Downloading folder-based audiobook: ${tracks.size} tracks to ${audiobookDir.name}")

        val totalBytes = tracks.sumOf { it.fileSizeBytes }
        var completedBytes = 0L

        for (track in tracks) {
            val trackFile = File(audiobookDir, "%03d_%s".format(track.index, track.fileName ?: "track${track.index}.m4a"))
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
                        trackTotalBytes = trackTotalBytes,
                    )
                )
            }.getOrThrow()
            completedBytes += trackFile.length()
        }

        bookDao.updateLocalPath(book.id, audiobookDir.absolutePath, Instant.now())
        return book.copy(
            localPath = audiobookDir.absolutePath,
            downloadedAt = Instant.now(),
        )
    } else {
        // Single file: download directly
        val file = File(booksDir, "${book.id}.m4b")
        grimmoryClient.downloadBook(
            server.url, server.id, grimmoryBookId, file,
            onProgress = onProgress?.let { callback ->
                { bytesRead, totalBytes ->
                    callback(DownloadProgress(bytesDownloaded = bytesRead, totalBytes = totalBytes))
                }
            },
        ).getOrThrow()

        Timber.d("Download complete: file=${file.name} size=${file.length()}")
        if (file.length() < 100) {
            file.delete()
            error("Downloaded file is too small — server may have returned an error")
        }

        val fileHash = PartialMd5.compute(file)
        bookDao.updateLocalPath(book.id, file.absolutePath, Instant.now())
        bookDao.updateFileHash(book.id, fileHash)

        return book.copy(
            localPath = file.absolutePath,
            fileHash = fileHash,
            downloadedAt = Instant.now(),
        )
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/repository/BookRepository.kt
git commit -m "feat: thread download progress through BookRepository"
```

---

### Task 4: Add progress notification methods to NotificationHelper

**Files:**
- Modify: `app/src/main/java/com/ember/reader/ui/common/NotificationHelper.kt`

- [ ] **Step 1: Add constants and progress notification methods**

Add these after the existing constants (after line 21) and methods:

```kotlin
// Add after line 21 (after CHANNEL_ACTIVITY constant)
const val NOTIFICATION_ID_DOWNLOAD_PROGRESS = 9001

// Add these methods before the private hasPermission method (before line 170)

fun buildDownloadProgressNotification(
    context: Context,
    bookTitle: String,
    cancelIntent: PendingIntent,
): android.app.Notification {
    return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Downloading \"$bookTitle\"")
        .setContentText("Starting download...")
        .setProgress(100, 0, true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
        .build()
}

fun updateDownloadProgress(
    context: Context,
    bookTitle: String,
    progress: Int,
    progressText: String,
    cancelIntent: PendingIntent,
) {
    if (!hasPermission(context)) return

    val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Downloading \"$bookTitle\"")
        .setContentText(progressText)
        .setProgress(100, progress, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DOWNLOAD_PROGRESS, notification)
}

fun showDownloadFailed(context: Context, bookTitle: String) {
    if (!hasPermission(context)) return

    val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle("Download Failed")
        .setContentText(bookTitle)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(openAppIntent(context))
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify("download_error".hashCode(), notification)
}

fun dismissDownloadProgress(context: Context) {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_DOWNLOAD_PROGRESS)
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/common/NotificationHelper.kt
git commit -m "feat: add download progress notification methods"
```

---

### Task 5: Create DownloadService

**Files:**
- Create: `app/src/main/java/com/ember/reader/ui/download/DownloadService.kt`

- [ ] **Step 1: Create the DownloadService**

```kotlin
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
    val serverId: Long,
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
                NotificationHelper.buildDownloadProgressNotification(this, book.title, cancelIntent()),
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
                    this, book.title, percent, progressText, cancelIntent(),
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
        val trackInfo = if (progress.trackCount != null && progress.trackCount > 1 && progress.trackIndex != null) {
            "Track ${progress.trackIndex + 1} of ${progress.trackCount} \u00B7 "
        } else {
            ""
        }

        val sizeInfo = if (progress.totalBytes != null && progress.totalBytes > 0) {
            "${formatBytes(progress.bytesDownloaded)} / ${formatBytes(progress.totalBytes)}"
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/download/DownloadService.kt
git commit -m "feat: add DownloadService foreground service"
```

---

### Task 6: Declare service in AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add FOREGROUND_SERVICE_DATA_SYNC permission**

Add after the existing `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission line:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

- [ ] **Step 2: Add DownloadService declaration**

Add after the `AudiobookPlaybackService` closing `</service>` tag (after line 50), before the `<activity>` tag:

```xml
<service
    android:name=".ui.download.DownloadService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: declare DownloadService in manifest"
```

---

### Task 7: Wire ViewModels to use DownloadService

**Files:**
- Modify: `app/src/main/java/com/ember/reader/ui/book/BookDetailViewModel.kt:53-54,112-129`
- Modify: `app/src/main/java/com/ember/reader/ui/library/LibraryViewModel.kt:64,276-286`

- [ ] **Step 1: Update BookDetailViewModel.downloadBook()**

Replace the `_downloading` MutableStateFlow field (lines 53-54) with one that observes the service:

```kotlin
private val _downloading = MutableStateFlow(false)
val downloading: StateFlow<Boolean> = _downloading.asStateFlow()
```

Add to the `init` block (or create one if it doesn't exist) to observe service state:

```kotlin
init {
    // ... existing init code ...
    viewModelScope.launch {
        DownloadService.downloadingBookIds.collect { ids ->
            val bookVal = _book.value
            _downloading.value = bookVal != null && bookVal.id in ids
            // Refresh book when download finishes (was downloading, now isn't)
            if (bookVal != null && bookVal.id !in ids && bookVal.isDownloaded.not()) {
                bookRepository.getById(bookVal.id)?.let { refreshed ->
                    if (refreshed.isDownloaded) _book.value = refreshed
                }
            }
        }
    }
}
```

Replace `downloadBook()` (lines 112-129):
```kotlin
fun downloadBook() {
    val book = _book.value ?: return
    val server = _server.value ?: return
    if (book.isDownloaded) return

    DownloadService.start(context, book.id, server.id)
}
```

Add import: `import com.ember.reader.ui.download.DownloadService`

- [ ] **Step 2: Update LibraryViewModel.downloadBook()**

Replace the `downloadBook()` method (lines 276-286):

```kotlin
fun downloadBook(book: Book) {
    val currentServer = server ?: return
    DownloadService.start(context, book.id, currentServer.id)
}
```

Update the downloading check - anywhere the UI checks `_downloadingBooks` to show a spinner, replace with `DownloadService.downloadingBookIds`. Find where `_downloadingBooks` is collected in the UI and replace:

```kotlin
// Old field (remove):
// private val _downloadingBooks = MutableStateFlow<Set<String>>(emptySet())

// New - expose the service's state directly:
val downloadingBooks: StateFlow<Set<String>> = DownloadService.downloadingBookIds
```

Add import: `import com.ember.reader.ui.download.DownloadService`

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (fix any remaining references to old `_downloadingBooks` or `_downloading` fields)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/book/BookDetailViewModel.kt app/src/main/java/com/ember/reader/ui/library/LibraryViewModel.kt
git commit -m "feat: wire ViewModels to use DownloadService"
```

---

### Task 8: Integration test - build and run on device

- [ ] **Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test checklist**

Test on device:
1. Download an ebook from Grimmory - verify progress notification appears with percentage and byte count
2. Download a multi-track audiobook - verify notification shows track info and overall progress
3. Lock phone during download - verify download continues and notification persists
4. Navigate away from book detail during download - verify download continues
5. Tap Cancel on notification - verify download stops and partial files are cleaned up
6. Download an ebook from an OPDS (non-Grimmory) server if available - verify progress works

- [ ] **Step 3: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix: integration fixes for download progress notification"
```
