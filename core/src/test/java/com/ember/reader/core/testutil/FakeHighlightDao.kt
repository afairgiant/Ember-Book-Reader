package com.ember.reader.core.testutil

import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory implementation of [HighlightDao] for testing sync managers.
 * Backed by a simple list — captures real insert/update/delete semantics
 * so sync managers' read-after-write loops work correctly.
 */
class FakeHighlightDao : HighlightDao {

    private val highlights = mutableListOf<HighlightEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<HighlightEntity>>(emptyList())

    /** Returns a snapshot of all stored entities (including tombstoned). */
    val all: List<HighlightEntity> get() = highlights.toList()

    override fun observeByBookId(bookId: String): Flow<List<HighlightEntity>> =
        flow.map { it.filter { h -> h.bookId == bookId && h.deletedAt == null } }

    override suspend fun getAllByBookId(bookId: String): List<HighlightEntity> =
        highlights.filter { it.bookId == bookId }

    override suspend fun getBookIdsWithHighlights(): List<String> =
        highlights.filter { it.deletedAt == null }.map { it.bookId }.distinct()

    override suspend fun insert(highlight: HighlightEntity): Long {
        val id = nextId++
        highlights.add(highlight.copy(id = id))
        emitUpdate()
        return id
    }

    override suspend fun update(highlight: HighlightEntity) {
        val index = highlights.indexOfFirst { it.id == highlight.id }
        if (index >= 0) {
            highlights[index] = highlight
            emitUpdate()
        }
    }

    override suspend fun softDeleteById(id: Long, deletedAt: Long) {
        val index = highlights.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = highlights[index]
            highlights[index] = existing.copy(
                deletedAt = java.time.Instant.ofEpochMilli(deletedAt),
                updatedAt = java.time.Instant.ofEpochMilli(deletedAt),
            )
            emitUpdate()
        }
    }

    override suspend fun deleteById(id: Long) {
        highlights.removeAll { it.id == id }
        emitUpdate()
    }

    override suspend fun cleanupTombstones(bookId: String) {
        highlights.removeAll { it.bookId == bookId && it.deletedAt != null }
        emitUpdate()
    }

    private suspend fun emitUpdate() {
        flow.value = highlights.toList()
    }
}
