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

    /** Every one in every book, newest first, for the notes screen. */
    @Query("SELECT * FROM bookmarks WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

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

    /** Replaces only the locator, leaving `updatedAt` alone so sync does not see an edit. */
    @Query("UPDATE bookmarks SET locatorJson = :locatorJson WHERE id = :id")
    suspend fun updateLocator(id: Long, locatorJson: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
