package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ember.reader.core.database.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByBookId(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId")
    suspend fun getAllByBookId(bookId: String): List<HighlightEntity>

    @Query("SELECT DISTINCT bookId FROM highlights WHERE deletedAt IS NULL")
    suspend fun getBookIdsWithHighlights(): List<String>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Query("UPDATE highlights SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAt: Long)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM highlights WHERE deletedAt IS NOT NULL AND bookId = :bookId")
    suspend fun cleanupTombstones(bookId: String)
}
