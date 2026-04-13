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
    private val appPreferencesRepository: AppPreferencesRepository
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
                updatedAt = now
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
