package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.grimmory.CreateBookmarkRequest
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

                // Check if server already has this CFI (match by position)
                val existingOnServer = serverBookmarks.find { it.cfi == cfi }
                if (existingOnServer != null) {
                    // Link to existing server bookmark instead of creating duplicate
                    bookmarkDao.update(local.copy(remoteId = existingOnServer.id))
                    Timber.d("BookmarkSync: linked local bookmark %d → existing remote %d", local.id, existingOnServer.id)
                    continue
                }

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
                    // 409 = already exists on server, try to link by refetching
                    if (it.message?.contains("409") == true) {
                        val refreshed = grimmoryClient.getBookmarks(server.url, server.id, grimmoryBookId).getOrNull()
                        val match = refreshed?.find { b -> b.cfi == cfi }
                        if (match != null) {
                            bookmarkDao.update(local.copy(remoteId = match.id))
                            Timber.d("BookmarkSync: linked local bookmark %d → remote %d after 409", local.id, match.id)
                        }
                    } else {
                        Timber.w(it, "BookmarkSync: failed to push bookmark %d", local.id)
                    }
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
