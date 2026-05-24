package com.ember.reader.core.testutil

import com.ember.reader.core.database.dao.BookReaderPreferencesDao
import com.ember.reader.core.database.entity.BookReaderPreferencesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [BookReaderPreferencesDao] for testing. Keyed by bookId. */
class FakeBookReaderPreferencesDao : BookReaderPreferencesDao {

    private val rows = mutableListOf<BookReaderPreferencesEntity>()

    /** Snapshot of all stored rows. */
    val all: List<BookReaderPreferencesEntity> get() = rows.toList()

    fun seed(entity: BookReaderPreferencesEntity) {
        rows.removeAll { it.bookId == entity.bookId }
        rows.add(entity)
    }

    override suspend fun get(bookId: String): BookReaderPreferencesEntity? =
        rows.find { it.bookId == bookId }

    override fun observe(bookId: String): Flow<BookReaderPreferencesEntity?> =
        MutableStateFlow(rows.find { it.bookId == bookId })

    override suspend fun getAll(): List<BookReaderPreferencesEntity> = rows.toList()

    override suspend fun upsert(entity: BookReaderPreferencesEntity) {
        rows.removeAll { it.bookId == entity.bookId }
        rows.add(entity)
    }

    override suspend fun delete(bookId: String) {
        rows.removeAll { it.bookId == bookId }
    }
}
