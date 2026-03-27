package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ReadingSessionDao
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.model.ReadingSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingSessionRepository @Inject constructor(
    private val readingSessionDao: ReadingSessionDao,
) {

    suspend fun saveSession(session: ReadingSession) {
        readingSessionDao.insert(session.toEntity())
    }

    suspend fun getTotalDurationToday(): Long {
        val (start, end) = todayRange()
        return readingSessionDao.getTotalDurationInRange(start, end)
    }

    suspend fun getTotalDurationThisWeek(): Long {
        val today = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val start = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return readingSessionDao.getTotalDurationInRange(start, end)
    }

    suspend fun getTotalDurationThisMonth(): Long {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)
        val start = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return readingSessionDao.getTotalDurationInRange(start, end)
    }

    suspend fun getTotalDurationAllTime(): Long =
        readingSessionDao.getTotalDurationAllTime()

    suspend fun getRecentSessions(limit: Int = 20): List<ReadingSession> =
        readingSessionDao.getRecentSessions(limit).map { it.toDomain() }

    suspend fun getSessionsForBook(bookId: String): List<ReadingSession> =
        readingSessionDao.getSessionsInRange(Instant.EPOCH, Instant.now())
            .filter { it.bookId == bookId }
            .map { it.toDomain() }

    suspend fun getTotalDurationForBook(bookId: String): Long =
        readingSessionDao.getTotalDurationForBook(bookId)

    /**
     * Returns a set of day indices (epoch days) that had reading activity
     * in the last N days. Used for streak calendar.
     */
    suspend fun getReadingDays(daysBack: Int = 84): Set<Long> {
        val end = Instant.now()
        val start = LocalDate.now().minusDays(daysBack.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant()
        return readingSessionDao.getReadingDaysInRange(start, end).toSet()
    }

    /**
     * Calculates current reading streak (consecutive days ending today or yesterday).
     */
    suspend fun getCurrentStreak(): Int {
        val readingDays = getReadingDays(365)
        if (readingDays.isEmpty()) return 0

        val today = LocalDate.now().toEpochDay()
        var streak = 0
        var day = today

        // Allow starting from today or yesterday
        if (day !in readingDays && (day - 1) !in readingDays) return 0
        if (day !in readingDays) day -= 1

        while (day in readingDays) {
            streak++
            day--
        }
        return streak
    }

    private fun todayRange(): Pair<Instant, Instant> {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return start to end
    }
}
