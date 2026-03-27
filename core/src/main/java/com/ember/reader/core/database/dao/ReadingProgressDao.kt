package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ember.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun observeByBookId(bookId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress")
    fun observeAll(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE needsSync = 1 AND serverId = :serverId")
    suspend fun getUnsyncedProgress(serverId: Long): List<ReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query(
        """
        UPDATE reading_progress
        SET syncedAt = :syncedAt, needsSync = 0
        WHERE bookId = :bookId
        """
    )
    suspend fun markSynced(bookId: String, syncedAt: java.time.Instant)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)
}
