package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateBookmarkRequest
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Server
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Gives real EPUB CFIs to bookmarks that lack one.
 *
 * Before Ember could compute CFIs it pushed bookmarks to Grimmory with a progression
 * number in the CFI field, which the web reader cannot open. Unlike an annotation's, a
 * bookmark's CFI can be changed on the server, so those are updated in place. Bookmarks
 * that never synced only need the CFI stored; the normal sync pushes them afterwards.
 */
@Singleton
class BookmarkRepairer @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val grimmoryClient: GrimmoryClient,
    private val bookSyncLocks: BookSyncLocks,
) {

    /**
     * @param server the book's Grimmory server and [grimmoryBookId], or null for a book that
     * does not sync; its bookmarks then only get a CFI stored.
     * @param computeCfis works out each bookmark's real CFI from where its locator says it
     * is, by bookmark id. A bookmark it cannot place is left out.
     */
    suspend fun repair(
        server: Server?,
        bookId: String,
        grimmoryBookId: Long?,
        computeCfis: suspend (List<Bookmark>) -> Map<Long, String>,
    ): Result<RepairSummary> = bookSyncLocks.withBookmarksLock(bookId) { repairUnlocked(server, bookId, grimmoryBookId, computeCfis) }

    private suspend fun repairUnlocked(
        server: Server?,
        bookId: String,
        grimmoryBookId: Long?,
        computeCfis: suspend (List<Bookmark>) -> Map<Long, String>,
    ): Result<RepairSummary> = runCatching {
        val lacking = bookmarkDao.getAllByBookId(bookId)
            .filter { it.deletedAt == null && !EpubCfi.isRealCfi(it.cfi) }
        if (lacking.isEmpty()) return@runCatching RepairSummary(repaired = 0, unresolved = 0)

        val serverCfis = if (server != null && grimmoryBookId != null) {
            grimmoryClient.getBookmarks(server.url, server.id, grimmoryBookId).getOrThrow()
                .associate { it.id to it.cfi }
        } else {
            emptyMap()
        }

        var repaired = 0
        // A bookmark made in the web reader already has a real CFI there: just take it.
        val needCfi = lacking.filter { local ->
            val serverCfi = serverCfis[local.remoteId]
            if (EpubCfi.isRealCfi(serverCfi)) {
                bookmarkDao.update(local.withCfi(serverCfi!!))
                repaired++
                false
            } else {
                true
            }
        }

        val cfis = if (needCfi.isEmpty()) emptyMap() else computeCfis(needCfi.map { it.toDomain() })
        for (local in needCfi) {
            val cfi = cfis[local.id]?.takeIf { EpubCfi.isRealCfi(it) } ?: continue
            val remoteId = local.remoteId
            // Never synced, or its bookmark is gone from the server: the normal sync takes
            // it from here.
            if (server != null && remoteId != null && remoteId in serverCfis) {
                val updated = grimmoryClient
                    .updateBookmark(server.url, server.id, remoteId, UpdateBookmarkRequest(cfi = cfi))
                    .onFailure { Timber.w(it, "BookmarkRepair: could not update bookmark %d", remoteId) }
                    .isSuccess
                if (!updated) continue
            }
            bookmarkDao.update(local.withCfi(cfi))
            repaired++
        }
        RepairSummary(repaired = repaired, unresolved = lacking.size - repaired)
    }.onFailure { Timber.w(it, "BookmarkRepair: failed for book=%s", bookId) }

    private fun BookmarkEntity.withCfi(cfi: String): BookmarkEntity =
        copy(cfi = cfi, locatorJson = CfiLocatorConverter.withUnplacedCfi(locatorJson, cfi))
}
