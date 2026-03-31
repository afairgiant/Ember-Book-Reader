# Highlight & Bookmark Sync with Grimmory

Bidirectional sync of EPUB highlights and bookmarks between Ember and Grimmory, using tombstone-based deletion tracking for correct behavior in all cases.

## Database Changes

### highlights table - add columns

| Column | Type | Default | Purpose |
|--------|------|---------|---------|
| `remoteId` | Long? | null | Grimmory annotation ID |
| `updatedAt` | Instant | now | Last modification time |
| `deletedAt` | Instant? | null | Soft delete tombstone |

### bookmarks table - add columns

| Column | Type | Default | Purpose |
|--------|------|---------|---------|
| `remoteId` | Long? | null | Grimmory bookmark ID |
| `updatedAt` | Instant | now | Last modification time |
| `deletedAt` | Instant? | null | Soft delete tombstone |

Existing rows get `updatedAt = createdAt`, `remoteId = null`, `deletedAt = null` via Room migration.

## Format Conversion

### Highlight Colors

| Ember Enum | Hex (push to Grimmory) |
|------------|----------------------|
| YELLOW | #FFEB3B |
| GREEN | #4CAF50 |
| BLUE | #2196F3 |
| PINK | #E91E63 |
| ORANGE | #FF9800 |
| PURPLE | #9C27B0 |

On pull: match hex to nearest enum value. Unknown colors default to YELLOW.

### Location Format

**Push (Ember → Grimmory):**
- Parse `locatorJson` as JSON
- Extract `locations.fragments[0]` as CFI string (Readium stores CFI there)
- If no fragments, fall back to constructing CFI from `href`
- Send `selectedText`, `color` (hex), `style` = "highlight", `chapterTitle` from locator title

**Pull (Grimmory → Ember):**
- Build a Readium Locator JSON from Grimmory's `cfi`, `text`, and `chapterTitle`
- Format: `{"href": "<derived from cfi>", "type": "application/xhtml+xml", "locations": {"fragments": ["epubcfi(<cfi>)"]}, "text": {"highlight": "<text>"}}`

### Bookmark Location

**Push:** Extract CFI from `locatorJson` the same way as highlights. Send `title`, `color` if present.

**Pull:** Reconstruct Locator JSON from CFI + title.

### Style Mapping

Ember only supports "highlight" style. On pull, all Grimmory styles (underline, strikethrough, squiggly) map to highlight. On push, always send "highlight".

## Sync Algorithm

Per book, per type (highlights or bookmarks). Runs independently - a failure in one does not affect the other.

```
1. Fetch all server items for this book
2. Fetch all local items for this book (including soft-deleted tombstones)
3. Build lookup maps: localByRemoteId, serverById

For each server item:
  - Find local match by remoteId
  - MATCHED + local tombstoned → DELETE on server
  - MATCHED + both active → compare updatedAt, keep newer (update loser)
  - NOT MATCHED + no tombstone for this remoteId → CREATE locally (new from server)

For each local item:
  - If no remoteId + active → CREATE on server, store returned remoteId
  - If no remoteId + tombstoned → hard delete locally (never synced)
  - If has remoteId + not in server response → DELETE locally (server-side deletion)

Clean up: hard delete all tombstones after successful sync
```

## Grimmory Client Methods

### Annotations (Highlights)

```kotlin
suspend fun getAnnotations(baseUrl: String, serverId: Long, bookId: Long): Result<List<GrimmoryAnnotation>>
suspend fun createAnnotation(baseUrl: String, serverId: Long, request: CreateAnnotationRequest): Result<GrimmoryAnnotation>
suspend fun updateAnnotation(baseUrl: String, serverId: Long, annotationId: Long, request: UpdateAnnotationRequest): Result<GrimmoryAnnotation>
suspend fun deleteAnnotation(baseUrl: String, serverId: Long, annotationId: Long): Result<Unit>
```

### Bookmarks

```kotlin
suspend fun getBookmarks(baseUrl: String, serverId: Long, bookId: Long): Result<List<GrimmoryBookmark>>
suspend fun createBookmark(baseUrl: String, serverId: Long, request: CreateBookmarkRequest): Result<GrimmoryBookmark>
suspend fun updateBookmark(baseUrl: String, serverId: Long, bookmarkId: Long, request: UpdateBookmarkRequest): Result<GrimmoryBookmark>
suspend fun deleteBookmark(baseUrl: String, serverId: Long, bookmarkId: Long): Result<Unit>
```

## Grimmory DTOs

```kotlin
@Serializable
data class GrimmoryAnnotation(
    val id: Long,
    val cfi: String,
    val text: String? = null,
    val color: String? = null,
    val style: String? = null,
    val note: String? = null,
    val chapterTitle: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateAnnotationRequest(
    val bookId: Long,
    val cfi: String,
    val text: String? = null,
    val color: String? = null,
    val style: String = "highlight",
    val note: String? = null,
    val chapterTitle: String? = null,
)

@Serializable
data class UpdateAnnotationRequest(
    val color: String? = null,
    val style: String? = null,
    val note: String? = null,
)

@Serializable
data class GrimmoryBookmark(
    val id: Long,
    val cfi: String? = null,
    val title: String? = null,
    val color: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateBookmarkRequest(
    val bookId: Long,
    val cfi: String,
    val title: String? = null,
)

@Serializable
data class UpdateBookmarkRequest(
    val title: String? = null,
    val color: String? = null,
    val notes: String? = null,
)
```

## Sync Managers

### HighlightSyncManager

Single class responsible for syncing highlights for one book. Injected with `HighlightDao`, `GrimmoryClient`.

```kotlin
suspend fun syncHighlights(server: Server, book: Book)
```

Implements the sync algorithm above. Handles all error cases gracefully - logs failures, doesn't throw.

### BookmarkSyncManager

Same pattern for bookmarks. Injected with `BookmarkDao`, `GrimmoryClient`.

```kotlin
suspend fun syncBookmarks(server: Server, book: Book)
```

## Soft Delete Integration

When sync is **enabled** for that type:
- `HighlightRepository.deleteHighlight(id)` sets `deletedAt = now` instead of hard deleting
- `BookmarkRepository.deleteBookmark(id)` sets `deletedAt = now` instead of hard deleting
- All queries that surface highlights/bookmarks to the UI filter `WHERE deletedAt IS NULL`

When sync is **disabled**:
- Hard delete as before (no tombstones needed)

The repositories need access to the sync settings to decide which delete behavior to use.

## Settings

Two new toggles in `AppPreferencesRepository`:
- `syncHighlights: Boolean` (default false)
- `syncBookmarks: Boolean` (default false)

Displayed in the Settings screen under the Sync group card:
- "Sync highlights" / "Sync highlights with Grimmory"
- "Sync bookmarks" / "Sync bookmarks with Grimmory"

## SyncWorker Integration

In the existing `SyncWorker`, after progress sync, for each Grimmory server:

```kotlin
if (syncHighlightsEnabled) {
    for (book in booksWithHighlights) {
        highlightSyncManager.syncHighlights(server, book)
    }
}
if (syncBookmarksEnabled) {
    for (book in booksWithBookmarks) {
        bookmarkSyncManager.syncBookmarks(server, book)
    }
}
```

Only syncs books that have local highlights/bookmarks OR are from the current server (to pull new ones).

## File Summary

| File | Change |
|------|--------|
| `core/.../database/entity/HighlightEntity.kt` | Add remoteId, updatedAt, deletedAt columns |
| `core/.../database/entity/BookmarkEntity.kt` | Add remoteId, updatedAt, deletedAt columns |
| `core/.../database/dao/HighlightDao.kt` | Add soft delete queries, filter deletedAt IS NULL |
| `core/.../database/dao/BookmarkDao.kt` | Add soft delete queries, filter deletedAt IS NULL |
| `core/.../database/EmberDatabase.kt` | Room migration for new columns |
| `core/.../model/Highlight.kt` | Add remoteId, updatedAt, deletedAt fields |
| `core/.../model/Bookmark.kt` | Add remoteId, updatedAt, deletedAt fields |
| `core/.../repository/HighlightRepository.kt` | Soft delete when sync enabled |
| `core/.../repository/BookmarkRepository.kt` | Soft delete when sync enabled |
| `core/.../grimmory/GrimmoryClient.kt` | Add annotation + bookmark CRUD methods |
| `core/.../grimmory/GrimmoryModels.kt` | Add DTOs for annotations + bookmarks |
| `core/.../sync/HighlightSyncManager.kt` | **New** - highlight sync logic |
| `core/.../sync/BookmarkSyncManager.kt` | **New** - bookmark sync logic |
| `core/.../sync/CfiLocatorConverter.kt` | **New** - CFI ↔ Locator JSON conversion |
| `core/.../sync/worker/SyncWorker.kt` | Call sync managers when enabled |
| `core/.../repository/AppPreferencesRepository.kt` | Add syncHighlights, syncBookmarks prefs |
| `app/.../ui/settings/AppSettingsScreen.kt` | Add two sync toggles |
| `app/.../ui/settings/SettingsViewModel.kt` | Expose new prefs |

## Out of Scope

- Syncing underline/strikethrough/squiggly styles (Ember only supports highlight)
- Syncing PDF annotations (different format entirely)
- Syncing audiobook bookmarks (position_ms format, no CFI)
- Highlight conflict UI (no user prompt - last-write-wins automatically)
- Bulk initial import wizard
