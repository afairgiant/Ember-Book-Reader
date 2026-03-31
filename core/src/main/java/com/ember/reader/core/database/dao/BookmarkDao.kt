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
