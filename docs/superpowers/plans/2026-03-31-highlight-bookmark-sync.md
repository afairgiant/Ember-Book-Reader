# Highlight & Bookmark Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bidirectional sync of EPUB highlights and bookmarks between Ember and Grimmory with tombstone-based deletion tracking and last-write-wins conflict resolution.

**Architecture:** Add `remoteId`, `updatedAt`, `deletedAt` columns to highlight/bookmark tables via Room migration. New Grimmory client methods for annotation/bookmark CRUD. Two sync managers (HighlightSyncManager, BookmarkSyncManager) implement the sync algorithm. SyncWorker calls them when settings are enabled. Two commits: highlights first, then bookmarks.

**Tech Stack:** Room (migration), Ktor (Grimmory API), Kotlin Coroutines, DataStore (preferences)

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `core/.../database/EmberDatabase.kt` | Modify | Add MIGRATION_6_7 for new columns |
| `core/.../database/entity/HighlightEntity.kt` | Modify | Add remoteId, updatedAt, deletedAt |
| `core/.../database/entity/BookmarkEntity.kt` | Modify | Add remoteId, updatedAt, deletedAt |
| `core/.../database/dao/HighlightDao.kt` | Modify | Soft delete, filter tombstones, sync queries |
| `core/.../database/dao/BookmarkDao.kt` | Modify | Same |
| `core/.../database/EntityMappers.kt` | Modify | Map new fields |
| `core/.../model/Highlight.kt` | Modify | Add remoteId, updatedAt, deletedAt |
| `core/.../model/Bookmark.kt` | Modify | Add remoteId, updatedAt, deletedAt |
| `core/.../model/HighlightColor.kt` | Modify | Add hex conversion helpers |
| `core/.../repository/HighlightRepository.kt` | Modify | Soft delete when sync enabled |
| `core/.../repository/BookmarkRepository.kt` | Modify | Same |
| `core/.../repository/AppPreferencesRepository.kt` | Modify | Add syncHighlights, syncBookmarks |
| `core/.../grimmory/GrimmoryModels.kt` | Modify | Add annotation + bookmark DTOs |
| `core/.../grimmory/GrimmoryClient.kt` | Modify | Add annotation + bookmark CRUD |
| `core/.../sync/HighlightSyncManager.kt` | Create | Highlight sync algorithm |
| `core/.../sync/BookmarkSyncManager.kt` | Create | Bookmark sync algorithm |
| `core/.../sync/CfiLocatorConverter.kt` | Create | CFI ↔ Locator JSON conversion |
| `core/.../sync/worker/SyncWorker.kt` | Modify | Call sync managers |
| `app/.../ui/settings/AppSettingsScreen.kt` | Modify | Two sync toggles |
| `app/.../ui/settings/SettingsViewModel.kt` | Modify | Expose new prefs |

---

### Task 1: Database migration + Entity/Model/Mapper changes

**Files:**
- Modify: `core/src/main/java/com/ember/reader/core/database/EmberDatabase.kt`
- Modify: `core/src/main/java/com/ember/reader/core/database/entity/HighlightEntity.kt`
- Modify: `core/src/main/java/com/ember/reader/core/database/entity/BookmarkEntity.kt`
- Modify: `core/src/main/java/com/ember/reader/core/model/Highlight.kt`
- Modify: `core/src/main/java/com/ember/reader/core/model/Bookmark.kt`
- Modify: `core/src/main/java/com/ember/reader/core/database/EntityMappers.kt`

- [ ] **Step 1: Add columns to HighlightEntity**

Replace the full `HighlightEntity` class in `core/src/main/java/com/ember/reader/core/database/entity/HighlightEntity.kt`:

```kotlin
package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ember.reader.core.model.HighlightColor
import java.time.Instant

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val color: HighlightColor = HighlightColor.YELLOW,
    val annotation: String? = null,
    val selectedText: String? = null,
    val createdAt: Instant,
    val remoteId: Long? = null,
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
)
```

- [ ] **Step 2: Add columns to BookmarkEntity**

Replace the full `BookmarkEntity` class in `core/src/main/java/com/ember/reader/core/database/entity/BookmarkEntity.kt`:

```kotlin
package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val title: String? = null,
    val createdAt: Instant,
    val remoteId: Long? = null,
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
)
```

- [ ] **Step 3: Update domain models**

Replace `core/src/main/java/com/ember/reader/core/model/Highlight.kt`:

```kotlin
package com.ember.reader.core.model

import java.time.Instant

data class Highlight(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val color: HighlightColor = HighlightColor.YELLOW,
    val annotation: String? = null,
    val selectedText: String? = null,
    val createdAt: Instant = Instant.now(),
    val remoteId: Long? = null,
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
)
```

Replace `core/src/main/java/com/ember/reader/core/model/Bookmark.kt`:

```kotlin
package com.ember.reader.core.model

import java.time.Instant

data class Bookmark(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val title: String? = null,
    val createdAt: Instant = Instant.now(),
    val remoteId: Long? = null,
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null,
)
```

- [ ] **Step 4: Update EntityMappers.kt**

Replace the highlight and bookmark mapper functions (lines 113-147) with versions that map the new fields:

```kotlin
fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    title = title,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    title = title,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun HighlightEntity.toDomain(): Highlight = Highlight(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    color = color,
    annotation = annotation,
    selectedText = selectedText,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Highlight.toEntity(): HighlightEntity = HighlightEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    color = color,
    annotation = annotation,
    selectedText = selectedText,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
```

- [ ] **Step 5: Add Room migration**

In `EmberDatabase.kt`, change `version = 6` to `version = 7` and add MIGRATION_6_7:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE highlights ADD COLUMN remoteId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE highlights ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE highlights ADD COLUMN deletedAt INTEGER DEFAULT NULL")
        db.execSQL("UPDATE highlights SET updatedAt = createdAt")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN remoteId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
        db.execSQL("UPDATE bookmarks SET updatedAt = createdAt")
    }
}
```

Add `MIGRATION_6_7` to the `addMigrations()` call in the database builder.

- [ ] **Step 6: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/database/ core/src/main/java/com/ember/reader/core/model/Highlight.kt core/src/main/java/com/ember/reader/core/model/Bookmark.kt
git commit -m "feat: add remoteId, updatedAt, deletedAt to highlights and bookmarks"
```

---

### Task 2: DAO changes + Repository soft delete

**Files:**
- Modify: `core/src/main/java/com/ember/reader/core/database/dao/HighlightDao.kt`
- Modify: `core/src/main/java/com/ember/reader/core/database/dao/BookmarkDao.kt`
- Modify: `core/src/main/java/com/ember/reader/core/repository/HighlightRepository.kt`
- Modify: `core/src/main/java/com/ember/reader/core/repository/BookmarkRepository.kt`
- Modify: `core/src/main/java/com/ember/reader/core/repository/AppPreferencesRepository.kt`

- [ ] **Step 1: Update HighlightDao**

Replace the full file:

```kotlin
package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ember.reader.core.database.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByBookId(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId")
    suspend fun getAllByBookId(bookId: String): List<HighlightEntity>

    @Query("SELECT DISTINCT bookId FROM highlights WHERE deletedAt IS NULL")
    suspend fun getBookIdsWithHighlights(): List<String>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Query("UPDATE highlights SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAt: Long)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM highlights WHERE deletedAt IS NOT NULL AND bookId = :bookId")
    suspend fun cleanupTombstones(bookId: String)
}
```

- [ ] **Step 2: Update BookmarkDao**

Replace the full file:

```kotlin
package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ember.reader.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByBookId(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId")
    suspend fun getAllByBookId(bookId: String): List<BookmarkEntity>

    @Query("SELECT DISTINCT bookId FROM bookmarks WHERE deletedAt IS NULL")
    suspend fun getBookIdsWithBookmarks(): List<String>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAt: Long)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE deletedAt IS NOT NULL AND bookId = :bookId")
    suspend fun cleanupTombstones(bookId: String)
}
```

- [ ] **Step 3: Add sync settings to AppPreferencesRepository**

Add to the Keys object (after `SYNC_NOTIFICATIONS` key):

```kotlin
val SYNC_HIGHLIGHTS = booleanPreferencesKey("sync_highlights")
val SYNC_BOOKMARKS = booleanPreferencesKey("sync_bookmarks")
```

Add flows and update methods (after the `syncNotifications` block):

```kotlin
val syncHighlightsFlow: Flow<Boolean> =
    context.appPreferencesDataStore.data.map { it[Keys.SYNC_HIGHLIGHTS] ?: false }

suspend fun getSyncHighlights(): Boolean =
    context.appPreferencesDataStore.data.first()[Keys.SYNC_HIGHLIGHTS] ?: false

suspend fun updateSyncHighlights(enabled: Boolean) {
    context.appPreferencesDataStore.edit { it[Keys.SYNC_HIGHLIGHTS] = enabled }
}

val syncBookmarksFlow: Flow<Boolean> =
    context.appPreferencesDataStore.data.map { it[Keys.SYNC_BOOKMARKS] ?: false }

suspend fun getSyncBookmarks(): Boolean =
    context.appPreferencesDataStore.data.first()[Keys.SYNC_BOOKMARKS] ?: false

suspend fun updateSyncBookmarks(enabled: Boolean) {
    context.appPreferencesDataStore.edit { it[Keys.SYNC_BOOKMARKS] = enabled }
}
```

Add `import kotlinx.coroutines.flow.first` if not already present.

- [ ] **Step 4: Update HighlightRepository for soft delete**

Replace the full file:

```kotlin
package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao,
    private val appPreferencesRepository: AppPreferencesRepository,
) {

    fun observeByBookId(bookId: String): Flow<List<Highlight>> =
        highlightDao.observeByBookId(bookId).map { entities -> entities.map { it.toDomain() } }

    suspend fun addHighlight(
        bookId: String,
        locatorJson: String,
        color: HighlightColor,
        annotation: String? = null,
        selectedText: String? = null
    ): Long {
        val now = Instant.now()
        return highlightDao.insert(
            HighlightEntity(
                bookId = bookId,
                locatorJson = locatorJson,
                color = color,
                annotation = annotation,
                selectedText = selectedText,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun updateHighlight(highlight: Highlight, annotation: String?, color: HighlightColor) {
        highlightDao.update(
            highlight.copy(annotation = annotation, color = color, updatedAt = Instant.now()).toEntity()
        )
    }

    suspend fun deleteHighlight(id: Long) {
        if (appPreferencesRepository.getSyncHighlights()) {
            highlightDao.softDeleteById(id, Instant.now().toEpochMilli())
        } else {
            highlightDao.deleteById(id)
        }
    }
}
```

- [ ] **Step 5: Update BookmarkRepository for soft delete**

Replace the full file:

```kotlin
package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.model.Bookmark
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val appPreferencesRepository: AppPreferencesRepository,
) {

    fun observeByBookId(bookId: String): Flow<List<Bookmark>> =
        bookmarkDao.observeByBookId(bookId).map { entities -> entities.map { it.toDomain() } }

    suspend fun addBookmark(bookId: String, locatorJson: String, title: String?): Long {
        val now = Instant.now()
        return bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                locatorJson = locatorJson,
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun deleteBookmark(id: Long) {
        if (appPreferencesRepository.getSyncBookmarks()) {
            bookmarkDao.softDeleteById(id, Instant.now().toEpochMilli())
        } else {
            bookmarkDao.deleteById(id)
        }
    }
}
```

- [ ] **Step 6: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/database/dao/ core/src/main/java/com/ember/reader/core/repository/HighlightRepository.kt core/src/main/java/com/ember/reader/core/repository/BookmarkRepository.kt core/src/main/java/com/ember/reader/core/repository/AppPreferencesRepository.kt
git commit -m "feat: DAO soft delete, sync settings, and repository integration"
```

---

### Task 3: Grimmory DTOs + Client methods + Color mapping

**Files:**
- Modify: `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryModels.kt`
- Modify: `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryClient.kt`
- Modify: `core/src/main/java/com/ember/reader/core/model/HighlightColor.kt`

- [ ] **Step 1: Add color conversion to HighlightColor**

Replace `core/src/main/java/com/ember/reader/core/model/HighlightColor.kt`:

```kotlin
package com.ember.reader.core.model

enum class HighlightColor(val argb: Long, val hex: String) {
    YELLOW(0xFFFFEB3B, "#FFEB3B"),
    GREEN(0xFF4CAF50, "#4CAF50"),
    BLUE(0xFF2196F3, "#2196F3"),
    PINK(0xFFE91E63, "#E91E63"),
    ORANGE(0xFFFF9800, "#FF9800"),
    PURPLE(0xFF9C27B0, "#9C27B0");

    companion object {
        fun fromHex(hex: String?): HighlightColor {
            if (hex == null) return YELLOW
            val normalized = hex.uppercase().removePrefix("#")
            return entries.firstOrNull { it.hex.removePrefix("#") == normalized } ?: YELLOW
        }
    }
}
```

- [ ] **Step 2: Add Grimmory DTOs**

Append to the end of `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryModels.kt`:

```kotlin
// Annotation/Highlight sync
@Serializable
data class GrimmoryAnnotation(
    val id: Long,
    val cfi: String? = null,
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

// Bookmark sync
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

- [ ] **Step 3: Add Grimmory Client annotation methods**

Add these methods to `GrimmoryClient.kt` (before the `audiobookStreamUrl` method):

```kotlin
    // --- Annotation/Highlight sync ---

    suspend fun getAnnotations(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
    ): Result<List<GrimmoryAnnotation>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/annotations/book/$bookId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Get annotations failed: ${response.status}")
        response.body<List<GrimmoryAnnotation>>()
    }

    suspend fun createAnnotation(
        baseUrl: String,
        serverId: Long,
        request: CreateAnnotationRequest,
    ): Result<GrimmoryAnnotation> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/annotations") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Create annotation failed: ${response.status}")
        response.body<GrimmoryAnnotation>()
    }

    suspend fun updateAnnotation(
        baseUrl: String,
        serverId: Long,
        annotationId: Long,
        request: UpdateAnnotationRequest,
    ): Result<GrimmoryAnnotation> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.put("${serverOrigin(baseUrl)}/api/v1/annotations/$annotationId") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Update annotation failed: ${response.status}")
        response.body<GrimmoryAnnotation>()
    }

    suspend fun deleteAnnotation(
        baseUrl: String,
        serverId: Long,
        annotationId: Long,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.delete("${serverOrigin(baseUrl)}/api/v1/annotations/$annotationId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Delete annotation failed: ${response.status}")
    }

    // --- Bookmark sync ---

    suspend fun getBookmarks(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
    ): Result<List<GrimmoryBookmark>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/bookmarks/book/$bookId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Get bookmarks failed: ${response.status}")
        response.body<List<GrimmoryBookmark>>()
    }

    suspend fun createBookmark(
        baseUrl: String,
        serverId: Long,
        request: CreateBookmarkRequest,
    ): Result<GrimmoryBookmark> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookmarks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Create bookmark failed: ${response.status}")
        response.body<GrimmoryBookmark>()
    }

    suspend fun updateBookmark(
        baseUrl: String,
        serverId: Long,
        bookmarkId: Long,
        request: UpdateBookmarkRequest,
    ): Result<GrimmoryBookmark> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.put("${serverOrigin(baseUrl)}/api/v1/bookmarks/$bookmarkId") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Update bookmark failed: ${response.status}")
        response.body<GrimmoryBookmark>()
    }

    suspend fun deleteBookmark(
        baseUrl: String,
        serverId: Long,
        bookmarkId: Long,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.delete("${serverOrigin(baseUrl)}/api/v1/bookmarks/$bookmarkId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Delete bookmark failed: ${response.status}")
    }
```

Add `import io.ktor.client.request.delete` to the imports if not present.

- [ ] **Step 4: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/grimmory/ core/src/main/java/com/ember/reader/core/model/HighlightColor.kt
git commit -m "feat: Grimmory annotation + bookmark API client and DTOs"
```

---

### Task 4: CFI ↔ Locator converter

**Files:**
- Create: `core/src/main/java/com/ember/reader/core/sync/CfiLocatorConverter.kt`

- [ ] **Step 1: Create CfiLocatorConverter.kt**

```kotlin
package com.ember.reader.core.sync

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Converts between Readium Locator JSON (used locally) and EPUB CFI strings (used by Grimmory).
 */
object CfiLocatorConverter {

    /**
     * Extract CFI string from a Readium Locator JSON string.
     * Readium stores CFI in locations.fragments[] array.
     */
    fun extractCfi(locatorJson: String): String? = runCatching {
        val json = JSONObject(locatorJson)
        val locations = json.optJSONObject("locations") ?: return@runCatching null

        // Try fragments array first (Readium 3.x format)
        val fragments = locations.optJSONArray("fragments")
        if (fragments != null && fragments.length() > 0) {
            val fragment = fragments.getString(0)
            // Strip "epubcfi(" prefix and ")" suffix if present
            return@runCatching fragment
                .removePrefix("epubcfi(")
                .removeSuffix(")")
                .ifBlank { null }
        }

        // Fall back to progression-based identifier
        locations.optString("progression").ifBlank { null }
    }.onFailure {
        Timber.w(it, "CfiLocatorConverter: failed to extract CFI from locator")
    }.getOrNull()

    /**
     * Build a minimal Readium Locator JSON from a CFI string and optional metadata.
     * This locator can be used by Readium to navigate to the highlight position.
     */
    fun buildLocatorJson(
        cfi: String,
        selectedText: String? = null,
        chapterTitle: String? = null,
    ): String {
        val locations = JSONObject().apply {
            put("fragments", JSONArray().put("epubcfi($cfi)"))
        }
        val text = JSONObject().apply {
            if (selectedText != null) put("highlight", selectedText)
        }
        return JSONObject().apply {
            put("href", hrefFromCfi(cfi))
            put("type", "application/xhtml+xml")
            put("locations", locations)
            if (text.length() > 0) put("text", text)
            if (chapterTitle != null) put("title", chapterTitle)
        }.toString()
    }

    /**
     * Extract chapter title from a Readium Locator JSON string.
     */
    fun extractTitle(locatorJson: String): String? = runCatching {
        JSONObject(locatorJson).optString("title").ifBlank { null }
    }.getOrNull()

    /**
     * Derive an href from CFI. CFI paths like /6/4[chap01]!/4/2 contain
     * the resource reference. If we can't parse it, return a generic href.
     */
    private fun hrefFromCfi(cfi: String): String {
        // CFI format varies; return empty href and let Readium resolve via fragments
        return ""
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/sync/CfiLocatorConverter.kt
git commit -m "feat: add CFI to Locator JSON converter for sync"
```

---

### Task 5: HighlightSyncManager

**Files:**
- Create: `core/src/main/java/com/ember/reader/core/sync/HighlightSyncManager.kt`

- [ ] **Step 1: Create HighlightSyncManager.kt**

```kotlin
package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.grimmory.CreateAnnotationRequest
import com.ember.reader.core.grimmory.GrimmoryAnnotation
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateAnnotationRequest
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.model.Server
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class HighlightSyncManager @Inject constructor(
    private val highlightDao: HighlightDao,
    private val grimmoryClient: GrimmoryClient,
) {

    suspend fun syncHighlightsForBook(server: Server, bookId: String, grimmoryBookId: Long) {
        Timber.d("HighlightSync: syncing book=%s grimmoryId=%d", bookId, grimmoryBookId)

        val serverAnnotations = grimmoryClient.getAnnotations(server.url, server.id, grimmoryBookId)
            .getOrElse {
                Timber.e(it, "HighlightSync: failed to fetch annotations")
                return
            }

        val localHighlights = highlightDao.getAllByBookId(bookId)
        val localByRemoteId = localHighlights.filter { it.remoteId != null }.associateBy { it.remoteId!! }
        val serverById = serverAnnotations.associateBy { it.id }

        // Process server annotations
        for (serverAnnotation in serverAnnotations) {
            val local = localByRemoteId[serverAnnotation.id]

            if (local != null) {
                if (local.deletedAt != null) {
                    // Local tombstoned → delete on server
                    grimmoryClient.deleteAnnotation(server.url, server.id, serverAnnotation.id)
                        .onSuccess { Timber.d("HighlightSync: deleted remote annotation %d", serverAnnotation.id) }
                        .onFailure { Timber.w(it, "HighlightSync: failed to delete remote annotation %d", serverAnnotation.id) }
                } else {
                    // Both active → compare timestamps, update loser
                    val serverTime = parseTimestamp(serverAnnotation.updatedAt)
                    if (serverTime != null && serverTime.isAfter(local.updatedAt)) {
                        // Server is newer → update local
                        highlightDao.update(local.copy(
                            color = HighlightColor.fromHex(serverAnnotation.color),
                            annotation = serverAnnotation.note,
                            selectedText = serverAnnotation.text ?: local.selectedText,
                            updatedAt = serverTime,
                        ))
                        Timber.d("HighlightSync: updated local highlight %d from server", local.id)
                    } else if (serverTime != null && local.updatedAt.isAfter(serverTime)) {
                        // Local is newer → update server
                        grimmoryClient.updateAnnotation(server.url, server.id, serverAnnotation.id,
                            UpdateAnnotationRequest(
                                color = local.color.hex,
                                note = local.annotation,
                            )
                        ).onSuccess { Timber.d("HighlightSync: updated remote annotation %d", serverAnnotation.id) }
                    }
                }
            } else {
                // Not matched locally → check tombstones by CFI
                val cfi = serverAnnotation.cfi ?: continue
                val tombstone = localHighlights.find {
                    it.deletedAt != null && it.remoteId == null && CfiLocatorConverter.extractCfi(it.locatorJson) == cfi
                }
                if (tombstone != null) {
                    // Was deleted locally before it got a remoteId (edge case) - skip
                    continue
                }
                // New from server → create locally
                val locatorJson = CfiLocatorConverter.buildLocatorJson(
                    cfi = cfi,
                    selectedText = serverAnnotation.text,
                    chapterTitle = serverAnnotation.chapterTitle,
                )
                val now = Instant.now()
                highlightDao.insert(HighlightEntity(
                    bookId = bookId,
                    locatorJson = locatorJson,
                    color = HighlightColor.fromHex(serverAnnotation.color),
                    annotation = serverAnnotation.note,
                    selectedText = serverAnnotation.text,
                    createdAt = parseTimestamp(serverAnnotation.createdAt) ?: now,
                    remoteId = serverAnnotation.id,
                    updatedAt = parseTimestamp(serverAnnotation.updatedAt) ?: now,
                ))
                Timber.d("HighlightSync: created local highlight from remote %d", serverAnnotation.id)
            }
        }

        // Process local highlights
        for (local in localHighlights) {
            if (local.remoteId == null && local.deletedAt == null) {
                // New local → push to server
                val cfi = CfiLocatorConverter.extractCfi(local.locatorJson) ?: continue
                val chapterTitle = CfiLocatorConverter.extractTitle(local.locatorJson)
                grimmoryClient.createAnnotation(server.url, server.id,
                    CreateAnnotationRequest(
                        bookId = grimmoryBookId,
                        cfi = cfi,
                        text = local.selectedText,
                        color = local.color.hex,
                        note = local.annotation,
                        chapterTitle = chapterTitle,
                    )
                ).onSuccess { created ->
                    highlightDao.update(local.copy(remoteId = created.id))
                    Timber.d("HighlightSync: pushed local highlight %d → remote %d", local.id, created.id)
                }.onFailure {
                    Timber.w(it, "HighlightSync: failed to push highlight %d", local.id)
                }
            } else if (local.remoteId == null && local.deletedAt != null) {
                // Tombstoned but never synced → just clean up
                highlightDao.deleteById(local.id)
            } else if (local.remoteId != null && !serverById.containsKey(local.remoteId)) {
                // Had remoteId but not on server → server deleted it
                highlightDao.deleteById(local.id)
                Timber.d("HighlightSync: removed local highlight %d (server-deleted)", local.id)
            }
        }

        // Clean up remaining tombstones
        highlightDao.cleanupTombstones(bookId)
        Timber.d("HighlightSync: completed for book=%s", bookId)
    }

    private fun parseTimestamp(timestamp: String?): Instant? = runCatching {
        if (timestamp == null) return null
        Instant.parse(timestamp)
    }.getOrNull()
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/sync/HighlightSyncManager.kt
git commit -m "feat: add HighlightSyncManager with bidirectional sync algorithm"
```

---

### Task 6: BookmarkSyncManager

**Files:**
- Create: `core/src/main/java/com/ember/reader/core/sync/BookmarkSyncManager.kt`

- [ ] **Step 1: Create BookmarkSyncManager.kt**

```kotlin
package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.grimmory.CreateBookmarkRequest
import com.ember.reader.core.grimmory.GrimmoryBookmark
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateBookmarkRequest
import com.ember.reader.core.model.Server
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class BookmarkSyncManager @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val grimmoryClient: GrimmoryClient,
) {

    suspend fun syncBookmarksForBook(server: Server, bookId: String, grimmoryBookId: Long) {
        Timber.d("BookmarkSync: syncing book=%s grimmoryId=%d", bookId, grimmoryBookId)

        val serverBookmarks = grimmoryClient.getBookmarks(server.url, server.id, grimmoryBookId)
            .getOrElse {
                Timber.e(it, "BookmarkSync: failed to fetch bookmarks")
                return
            }

        val localBookmarks = bookmarkDao.getAllByBookId(bookId)
        val localByRemoteId = localBookmarks.filter { it.remoteId != null }.associateBy { it.remoteId!! }
        val serverById = serverBookmarks.associateBy { it.id }

        // Process server bookmarks
        for (serverBookmark in serverBookmarks) {
            val local = localByRemoteId[serverBookmark.id]

            if (local != null) {
                if (local.deletedAt != null) {
                    // Local tombstoned → delete on server
                    grimmoryClient.deleteBookmark(server.url, server.id, serverBookmark.id)
                        .onSuccess { Timber.d("BookmarkSync: deleted remote bookmark %d", serverBookmark.id) }
                        .onFailure { Timber.w(it, "BookmarkSync: failed to delete remote bookmark %d", serverBookmark.id) }
                } else {
                    // Both active → compare timestamps, update loser
                    val serverTime = parseTimestamp(serverBookmark.updatedAt)
                    if (serverTime != null && serverTime.isAfter(local.updatedAt)) {
                        // Server is newer → update local
                        bookmarkDao.update(local.copy(
                            title = serverBookmark.title ?: local.title,
                            updatedAt = serverTime,
                        ))
                        Timber.d("BookmarkSync: updated local bookmark %d from server", local.id)
                    } else if (serverTime != null && local.updatedAt.isAfter(serverTime)) {
                        // Local is newer → update server
                        grimmoryClient.updateBookmark(server.url, server.id, serverBookmark.id,
                            UpdateBookmarkRequest(title = local.title)
                        ).onSuccess { Timber.d("BookmarkSync: updated remote bookmark %d", serverBookmark.id) }
                    }
                }
            } else {
                // Not matched locally → new from server
                val cfi = serverBookmark.cfi ?: continue
                val locatorJson = CfiLocatorConverter.buildLocatorJson(
                    cfi = cfi,
                    chapterTitle = serverBookmark.title,
                )
                val now = Instant.now()
                bookmarkDao.insert(BookmarkEntity(
                    bookId = bookId,
                    locatorJson = locatorJson,
                    title = serverBookmark.title,
                    createdAt = parseTimestamp(serverBookmark.createdAt) ?: now,
                    remoteId = serverBookmark.id,
                    updatedAt = parseTimestamp(serverBookmark.updatedAt) ?: now,
                ))
                Timber.d("BookmarkSync: created local bookmark from remote %d", serverBookmark.id)
            }
        }

        // Process local bookmarks
        for (local in localBookmarks) {
            if (local.remoteId == null && local.deletedAt == null) {
                // New local → push to server
                val cfi = CfiLocatorConverter.extractCfi(local.locatorJson) ?: continue
                grimmoryClient.createBookmark(server.url, server.id,
                    CreateBookmarkRequest(
                        bookId = grimmoryBookId,
                        cfi = cfi,
                        title = local.title,
                    )
                ).onSuccess { created ->
                    bookmarkDao.update(local.copy(remoteId = created.id))
                    Timber.d("BookmarkSync: pushed local bookmark %d → remote %d", local.id, created.id)
                }.onFailure {
                    Timber.w(it, "BookmarkSync: failed to push bookmark %d", local.id)
                }
            } else if (local.remoteId == null && local.deletedAt != null) {
                // Tombstoned but never synced → just clean up
                bookmarkDao.deleteById(local.id)
            } else if (local.remoteId != null && !serverById.containsKey(local.remoteId)) {
                // Had remoteId but not on server → server deleted it
                bookmarkDao.deleteById(local.id)
                Timber.d("BookmarkSync: removed local bookmark %d (server-deleted)", local.id)
            }
        }

        // Clean up remaining tombstones
        bookmarkDao.cleanupTombstones(bookId)
        Timber.d("BookmarkSync: completed for book=%s", bookId)
    }

    private fun parseTimestamp(timestamp: String?): Instant? = runCatching {
        if (timestamp == null) return null
        Instant.parse(timestamp)
    }.getOrNull()
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/sync/BookmarkSyncManager.kt
git commit -m "feat: add BookmarkSyncManager with bidirectional sync algorithm"
```

---

### Task 7: SyncWorker integration + Settings UI

**Files:**
- Modify: `core/src/main/java/com/ember/reader/core/sync/worker/SyncWorker.kt`
- Modify: `app/src/main/java/com/ember/reader/ui/settings/AppSettingsScreen.kt`
- Modify: `app/src/main/java/com/ember/reader/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Add sync managers to SyncWorker**

Add `HighlightSyncManager` and `BookmarkSyncManager` as injected dependencies in the SyncWorker constructor. Add `AppPreferencesRepository` if not already present.

After the existing Grimmory progress sync block (around the end of the Grimmory sync section), add:

```kotlin
// Highlight sync
if (appPreferencesRepository.getSyncHighlights()) {
    val bookIdsWithHighlights = highlightDao.getBookIdsWithHighlights()
    for (bid in bookIdsWithHighlights) {
        val book = bookRepository.getById(bid) ?: continue
        val gid = book.grimmoryBookId ?: continue
        if (book.serverId != server.id) continue
        runCatching { highlightSyncManager.syncHighlightsForBook(server, bid, gid) }
            .onFailure { Timber.e(it, "SyncWorker: highlight sync failed for book=%s", bid) }
    }
}

// Bookmark sync
if (appPreferencesRepository.getSyncBookmarks()) {
    val bookIdsWithBookmarks = bookmarkDao.getBookIdsWithBookmarks()
    for (bid in bookIdsWithBookmarks) {
        val book = bookRepository.getById(bid) ?: continue
        val gid = book.grimmoryBookId ?: continue
        if (book.serverId != server.id) continue
        runCatching { bookmarkSyncManager.syncBookmarksForBook(server, bid, gid) }
            .onFailure { Timber.e(it, "SyncWorker: bookmark sync failed for book=%s", bid) }
    }
}
```

The exact injection and placement depends on the SyncWorker's current structure - the implementer should read the file, add the injected fields, and place the code in the Grimmory server block.

- [ ] **Step 2: Add settings to SettingsViewModel**

Add to `SettingsViewModel.kt`:

```kotlin
val syncHighlights: StateFlow<Boolean> = appPreferencesRepository.syncHighlightsFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

val syncBookmarks: StateFlow<Boolean> = appPreferencesRepository.syncBookmarksFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

fun updateSyncHighlights(enabled: Boolean) {
    viewModelScope.launch { appPreferencesRepository.updateSyncHighlights(enabled) }
}

fun updateSyncBookmarks(enabled: Boolean) {
    viewModelScope.launch { appPreferencesRepository.updateSyncBookmarks(enabled) }
}
```

- [ ] **Step 3: Add toggles to AppSettingsScreen**

In `AppSettingsScreen.kt`, collect the new states:

```kotlin
val syncHighlights by viewModel.syncHighlights.collectAsStateWithLifecycle()
val syncBookmarks by viewModel.syncBookmarks.collectAsStateWithLifecycle()
```

Inside the Sync `SettingsGroup`, after the sync notifications toggle, add:

```kotlin
HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

SettingsToggleRow(
    icon = Icons.Default.FormatColorFill,
    title = "Sync highlights",
    subtitle = "Sync highlights with Grimmory",
    checked = syncHighlights,
    onCheckedChange = { viewModel.updateSyncHighlights(it) },
)

HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

SettingsToggleRow(
    icon = Icons.Default.BookmarkBorder,
    title = "Sync bookmarks",
    subtitle = "Sync bookmarks with Grimmory",
    checked = syncBookmarks,
    onCheckedChange = { viewModel.updateSyncBookmarks(it) },
)
```

Add `import androidx.compose.material.icons.filled.FormatColorFill` and `import androidx.compose.material.icons.filled.BookmarkBorder` to imports.

- [ ] **Step 4: Build**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit highlights sync (first commit)**

```bash
git add core/src/main/java/com/ember/reader/core/sync/ app/src/main/java/com/ember/reader/ui/settings/
git commit -m "feat: highlight sync with Grimmory - bidirectional with tombstone tracking"
```

- [ ] **Step 6: Commit bookmark sync (second commit)**

Since all the bookmark code is included in the same tasks, the implementer should split the commits logically:
- First commit: everything needed for highlight sync to work end-to-end
- Second commit: bookmark sync manager, SyncWorker bookmark block, bookmark settings toggle

If already committed together, use `git reset HEAD~1` and re-commit in two parts.

Alternatively, if the implementer builds incrementally:
- After Step 5 commit, verify highlight sync works
- Then add the bookmark-specific code and commit:
```bash
git commit -m "feat: bookmark sync with Grimmory - bidirectional with tombstone tracking"
```

---

### Task 8: Integration build and verification

- [ ] **Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test checklist**

1. Open app, go to Settings → enable "Sync highlights"
2. Open an EPUB book, create a highlight
3. Tap "Sync Now" → check Logcat for `HighlightSync:` logs showing push
4. Create a highlight on Grimmory web reader for the same book
5. Sync again → verify the web highlight appears locally
6. Delete a local highlight → Sync → verify it's removed from server
7. Repeat steps 2-6 for bookmarks with "Sync bookmarks" enabled
