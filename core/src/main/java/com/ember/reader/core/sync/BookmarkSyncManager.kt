package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.grimmory.CreateBookmarkRequest
import com.ember.reader.core.grimmory.GrimmoryBookmark
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateBookmarkRequest
import com.ember.reader.core.model.AudioPosition
import com.ember.reader.core.model.PdfBookmarkPage
import com.ember.reader.core.model.Server
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class BookmarkSyncManager @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val grimmoryClient: GrimmoryClient,
    private val syncStatusRepository: SyncStatusRepository
) {

    suspend fun syncBookmarksForBook(server: Server, bookId: String, grimmoryBookId: Long) {
        Timber.d("BookmarkSync: syncing book=%s grimmoryId=%d", bookId, grimmoryBookId)

        val serverBookmarks = grimmoryClient.getBookmarks(server.url, server.id, grimmoryBookId)
            .onFailure { syncStatusRepository.reportFailure(server.id, it) }
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
                    // Keep a real CFI the server has for a bookmark stored before Ember kept
                    // them. One that arrived with a fake CFI has not been placed in the book;
                    // its locator takes the real one too, so it can be.
                    val serverCfi = serverBookmark.cfi
                    val known = if (local.cfi == null && serverCfi != null && EpubCfi.isRealCfi(serverCfi)) {
                        local.copy(
                            cfi = serverCfi,
                            locatorJson = CfiLocatorConverter.withUnplacedCfi(local.locatorJson, serverCfi),
                        ).also { bookmarkDao.update(it) }
                    } else {
                        local
                    }
                    // Both active → compare timestamps, update loser
                    val serverTime = parseTimestamp(serverBookmark.updatedAt)
                    if (serverTime != null && serverTime.isAfter(local.updatedAt)) {
                        // Server is newer → update local
                        bookmarkDao.update(
                            known.copy(
                                title = serverBookmark.title ?: local.title,
                                updatedAt = serverTime
                            )
                        )
                        Timber.d("BookmarkSync: updated local bookmark %d from server", local.id)
                    } else if (serverTime != null && local.updatedAt.isAfter(serverTime)) {
                        // Local is newer → update server
                        grimmoryClient.updateBookmark(
                            server.url, server.id, serverBookmark.id,
                            UpdateBookmarkRequest(title = local.title)
                        ).onSuccess { Timber.d("BookmarkSync: updated remote bookmark %d", serverBookmark.id) }
                    }
                }
            } else {
                // Not matched locally. It may still be one made here at the same place and
                // not yet pushed; that one is linked below, so it is not made a second time.
                if (localBookmarks.any { it.remoteId == null && it.deletedAt == null && it.isAt(serverBookmark) }) {
                    continue
                }
                // New from server
                val audioPosition = serverBookmark.audioPosition()
                val pdfPage = serverBookmark.pageNumber
                val cfi = serverBookmark.cfi
                val locatorJson = when {
                    audioPosition != null -> audioPosition.toLocatorJson()
                    pdfPage != null -> PdfBookmarkPage.locatorJson(pdfPage, serverBookmark.title)
                    cfi != null -> CfiLocatorConverter.buildLocatorJson(cfi = cfi, chapterTitle = serverBookmark.title)
                    else -> continue
                }
                val now = Instant.now()
                bookmarkDao.insert(
                    BookmarkEntity(
                        bookId = bookId,
                        locatorJson = locatorJson,
                        title = serverBookmark.title,
                        cfi = cfi?.takeIf { audioPosition == null && pdfPage == null && EpubCfi.isRealCfi(it) },
                        createdAt = parseTimestamp(serverBookmark.createdAt) ?: now,
                        remoteId = serverBookmark.id,
                        updatedAt = parseTimestamp(serverBookmark.updatedAt) ?: now
                    )
                )
                Timber.d("BookmarkSync: created local bookmark from remote %d", serverBookmark.id)
            }
        }

        // Process local bookmarks
        for (local in localBookmarks) {
            if (local.remoteId == null && local.deletedAt == null) {
                // New local → push to server. An audiobook's bookmark goes by its position
                // and a PDF's by its page. Otherwise Grimmory's web reader can only open a
                // bookmark from a real CFI, so one without it stays on this device.
                val audioPosition = AudioPosition.fromLocatorJson(local.locatorJson)
                val pdfPage = PdfBookmarkPage.of(local.locatorJson)
                val cfi = local.cfi?.takeIf { audioPosition == null && pdfPage == null && EpubCfi.isRealCfi(it) }
                if (audioPosition == null && pdfPage == null && cfi == null) {
                    Timber.d("BookmarkSync: holding local bookmark %d, no real CFI", local.id)
                    continue
                }
                // The server's own test for "already there".
                val samePlace = { remote: GrimmoryBookmark -> local.isAt(remote) }

                // Check if server already has this place
                val existingOnServer = serverBookmarks.find(samePlace)
                if (existingOnServer != null) {
                    // Link to existing server bookmark instead of creating duplicate
                    bookmarkDao.update(local.copy(remoteId = existingOnServer.id))
                    Timber.d("BookmarkSync: linked local bookmark %d → existing remote %d", local.id, existingOnServer.id)
                    continue
                }

                grimmoryClient.createBookmark(
                    server.url, server.id,
                    CreateBookmarkRequest(
                        bookId = grimmoryBookId,
                        cfi = cfi,
                        positionMs = audioPosition?.positionMs,
                        trackIndex = audioPosition?.trackIndex,
                        pageNumber = pdfPage,
                        title = local.title
                    )
                ).onSuccess { created ->
                    bookmarkDao.update(local.copy(remoteId = created.id))
                    Timber.d("BookmarkSync: pushed local bookmark %d → remote %d", local.id, created.id)
                }.onFailure {
                    // 409 = already exists on server, try to link by refetching
                    if (it.message?.contains("409") == true) {
                        val refreshed = grimmoryClient.getBookmarks(server.url, server.id, grimmoryBookId).getOrNull()
                        val match = refreshed?.find(samePlace)
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
        syncStatusRepository.reportSuccess(server.id)
        Timber.d("BookmarkSync: completed for book=%s", bookId)
    }

    private fun parseTimestamp(timestamp: String?): Instant? = runCatching {
        if (timestamp == null) return null
        Instant.parse(timestamp)
    }.getOrNull()
}

/** Where an audiobook bookmark from the server is; null for a bookmark in a book. */
private fun GrimmoryBookmark.audioPosition(): AudioPosition? = positionMs?.let { AudioPosition(it, trackIndex) }

/**
 * Whether this bookmark is at the place [remote] is, by Grimmory's own rules: an audio
 * position within a few seconds of it, the same PDF page, or the same real CFI.
 */
private fun BookmarkEntity.isAt(remote: GrimmoryBookmark): Boolean {
    val audioPosition = AudioPosition.fromLocatorJson(locatorJson)
    val pdfPage = PdfBookmarkPage.of(locatorJson)
    return when {
        audioPosition != null -> remote.audioPosition()?.isNear(audioPosition) == true
        pdfPage != null -> remote.pageNumber == pdfPage
        else -> cfi != null && EpubCfi.isRealCfi(cfi) && cfi == remote.cfi
    }
}
