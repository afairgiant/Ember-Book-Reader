# Download Progress Notification

Persistent foreground service notification that shows real-time download progress for ebook and audiobook downloads, survives screen navigation and phone locking, and supports cancellation.

## Architecture

### DownloadService (New)

A started foreground service that owns the download lifecycle. User-initiated downloads move out of ViewModels and into this service. SyncWorker auto-downloads continue using BookRepository directly (they already run in a background worker).

**Responsibilities:**
- Starts as a foreground service with a persistent progress notification
- Survives screen navigation, app backgrounding, and phone locking
- Runs downloads in its own coroutine scope (not tied to any ViewModel)
- Supports cancel via notification action and pending intent
- Updates notification with real-time byte-level progress
- Handles one download at a time (queues additional requests)

**Lifecycle:**
1. Started via `context.startForegroundService(intent)` with bookId + serverId extras
2. Immediately calls `startForeground()` with progress notification
3. Runs download coroutine
4. On completion/failure/cancel: updates notification, calls `stopForeground()`, stops self

**Location:** `app/src/main/java/com/ember/reader/ui/download/DownloadService.kt`

### Download Queue

Downloads are processed one at a time. If a download is already in progress when a new one is requested, the new request is queued and processed after the current one finishes. This avoids competing for bandwidth and keeps the notification simple.

A `Channel<DownloadRequest>` in the service handles queuing. The notification shows the queue size when there are pending downloads.

## Notification Layout

### Single File (ebook or single-file audiobook)

```
Downloading "Book Title"
[=====>              ] 45% - 12.3 MB / 27.1 MB
[Cancel]
```

### Multi-Track Audiobook

```
Downloading "Audiobook Title"
Track 3 of 12 - 45%
[========>           ] Overall: 25%
[Cancel]
```

The overall percentage for multi-track is calculated as:
`(completed_tracks_bytes + current_track_downloaded_bytes) / total_audiobook_bytes`

Track file sizes come from `AudiobookInfo.tracks[].fileSizeBytes`.

### Completion

Replaces the progress notification with the existing "Download complete" notification (already implemented in `NotificationHelper.showDownloadComplete()`).

### Failure

Shows a brief error notification: "Download failed: Book Title" then dismisses.

### Cancel

- Cancels the coroutine job (cooperative cancellation)
- Cleans up partial files:
  - Single file: deletes the partial file
  - Multi-track: keeps already-completed tracks, deletes the in-progress track
- Dismisses the notification
- Does NOT clean up the audiobook directory (partial downloads can be resumed later since `downloadAudiobook` already skips existing tracks)

## Progress Callback Plumbing

### Download Client Changes

Add an optional `onProgress` lambda to the three download methods:

**GrimmoryClient.downloadBook():**
```kotlin
suspend fun downloadBook(
    baseUrl: String,
    serverId: Long,
    grimmoryBookId: Long,
    destination: File,
    onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
): Result<Unit>
```

Progress is reported from the existing byte-buffer read loop. `totalBytes` comes from the `Content-Length` response header (nullable since chunked transfers won't have it).

Same pattern for `GrimmoryClient.downloadFromUrl()` and `OpdsClient.downloadBookToFile()`.

### BookRepository Changes

**downloadBook():**
```kotlin
suspend fun downloadBook(
    book: Book,
    server: Server,
    onProgress: ((DownloadProgress) -> Unit)? = null,
): Result<Book>
```

**downloadAudiobook():**
Wraps per-track progress into overall audiobook progress using track file sizes from `AudiobookInfo`. Calls `onProgress` with a `DownloadProgress` that includes both track-level and overall-level info.

### DownloadProgress Data Class

```kotlin
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val trackIndex: Int? = null,
    val trackCount: Int? = null,
    val trackBytesDownloaded: Long? = null,
    val trackTotalBytes: Long? = null,
)
```

**Location:** `core/src/main/java/com/ember/reader/core/model/DownloadProgress.kt`

## NotificationHelper Changes

Add two methods:

**showDownloadProgress():** Creates the initial foreground notification with indeterminate progress (before total size is known). Returns the notification ID for updates.

**updateDownloadProgress():** Updates the existing notification with current progress bar, percentage text, and track info. Throttled to update at most every 500ms to avoid notification spam.

Both use the existing `CHANNEL_DOWNLOADS` channel.

## ViewModel Changes

`BookDetailViewModel.downloadBook()` and `LibraryViewModel.downloadBook()` change from calling `bookRepository.downloadBook()` directly to starting the `DownloadService` via intent:

```kotlin
fun downloadBook() {
    val book = _book.value ?: return
    val server = _server.value ?: return
    DownloadService.start(context, book.id, server.id)
    _downloading.value = true
}
```

The "downloading" state in the ViewModel is observed via a companion object `StateFlow<Set<String>>` on `DownloadService` that holds the set of currently downloading book IDs. ViewModels collect this flow to update their UI state. This avoids binding to the service.

## Manifest Changes

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".ui.download.DownloadService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

## File Summary

| File | Change |
|------|--------|
| `app/.../ui/download/DownloadService.kt` | **New** - Foreground service |
| `core/.../model/DownloadProgress.kt` | **New** - Progress data class |
| `core/.../grimmory/GrimmoryClient.kt` | Modify - Add onProgress to downloadBook, downloadFromUrl |
| `core/.../opds/OpdsClient.kt` | Modify - Add onProgress to downloadBookToFile |
| `core/.../repository/BookRepository.kt` | Modify - Add onProgress, pass through to clients |
| `app/.../ui/common/NotificationHelper.kt` | Modify - Add progress notification methods |
| `app/.../ui/book/BookDetailViewModel.kt` | Modify - Start service instead of direct download |
| `app/.../ui/library/LibraryViewModel.kt` | Modify - Start service instead of direct download |
| `app/src/main/AndroidManifest.xml` | Modify - Declare service + permission |

## Out of Scope

- Partial download visibility in library (separate feature, noted for later)
- Download queue UI (just notification for now)
- Wi-Fi only download preference
- Parallel downloads
