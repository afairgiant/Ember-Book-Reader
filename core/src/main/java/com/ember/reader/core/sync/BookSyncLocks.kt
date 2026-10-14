package com.ember.reader.core.sync

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lets one pass at a time sync a book's highlights, and one its bookmarks. The periodic
 * worker, Sync now, the reader, the notebook and the repairers can all sync the same book;
 * two passes at once would each see an item as not yet on the server and both create it.
 *
 * Highlights and bookmarks have separate locks, so a highlight sync never waits on a
 * bookmark one. Neither lock is reentrant: code holding one must not sync the same kind again.
 */
@Singleton
class BookSyncLocks @Inject constructor() {

    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withHighlightsLock(bookId: String, block: suspend () -> T): T =
        lockFor("highlights:$bookId").withLock { block() }

    suspend fun <T> withBookmarksLock(bookId: String, block: suspend () -> T): T =
        lockFor("bookmarks:$bookId").withLock { block() }

    private fun lockFor(key: String): Mutex = locks.getOrPut(key) { Mutex() }
}
