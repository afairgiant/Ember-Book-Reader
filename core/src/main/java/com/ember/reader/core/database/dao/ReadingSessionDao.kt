package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ember.reader.core.database.entity.ReadingSessionEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startTime DESC")
    fun observeByBookId(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE startTime >= :start AND startTime < :end ORDER BY startTime DESC")
    suspend fun getSessionsInRange(start: Instant, end: Instant): List<ReadingSessionEntity>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_sessions WHERE startTime >= :start AND startTime < :end")
    suspend fun getTotalDurationInRange(start: Instant, end: Instant): Long

    @Query("SELECT * FROM reading_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<ReadingSessionEntity>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_sessions")
    suspend fun getTotalDurationAllTime(): Long

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun getTotalDurationForBook(bookId: String): Long

    @Query("SELECT COUNT(*) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun getSessionCountForBook(bookId: String): Int

    @Query(
        """
        SELECT DISTINCT CAST(startTime / 86400000 AS INTEGER) as day
        FROM reading_sessions
        WHERE startTime >= :start AND startTime < :end
        """
    )
    suspend fun getReadingDaysInRange(start: Instant, end: Instant): List<Long>
}
