package com.ember.reader.core.testutil

import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory implementation of [BookmarkDao] for testing sync managers.
 * Backed by a simple list — captures real insert/update/delete semantics
 * so sync managers' read-after-write loops work correctly.
 */
class FakeBookmarkDao : BookmarkDao {

    private val bookmarks = mutableListOf<BookmarkEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    /** Returns a snapshot of all stored entities (including tombstoned). */
    val all: List<BookmarkEntity> get() = bookmarks.toList()

    override fun observeByBookId(bookId: String): Flow<List<BookmarkEntity>> =
        flow.map { it.filter { b -> b.bookId == bookId && b.deletedAt == null } }

    override suspend fun getAllByBookId(bookId: String): List<BookmarkEntity> =
        bookmarks.filter { it.bookId == bookId }

    override suspend fun getBookIdsWithBookmarks(): List<String> =
        bookmarks.filter { it.deletedAt == null }.map { it.bookId }.distinct()

    override suspend fun insert(bookmark: BookmarkEntity): Long {
        val id = nextId++
        bookmarks.add(bookmark.copy(id = id))
        emitUpdate()
        return id
    }

    override suspend fun update(bookmark: BookmarkEntity) {
        val index = bookmarks.indexOfFirst { it.id == bookmark.id }
        if (index >= 0) {
            bookmarks[index] = bookmark
            emitUpdate()
        }
    }

    override suspend fun softDeleteById(id: Long, deletedAt: Long) {
        val index = bookmarks.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = bookmarks[index]
            bookmarks[index] = existing.copy(
                deletedAt = java.time.Instant.ofEpochMilli(deletedAt),
                updatedAt = java.time.Instant.ofEpochMilli(deletedAt),
            )
            emitUpdate()
        }
    }

    override suspend fun deleteById(id: Long) {
        bookmarks.removeAll { it.id == id }
        emitUpdate()
    }

    override suspend fun cleanupTombstones(bookId: String) {
        bookmarks.removeAll { it.bookId == bookId && it.deletedAt != null }
        emitUpdate()
    }

    private suspend fun emitUpdate() {
        flow.value = bookmarks.toList()
    }
}
