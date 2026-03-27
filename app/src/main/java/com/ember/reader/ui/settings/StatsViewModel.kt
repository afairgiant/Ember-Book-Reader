package com.ember.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.ReadingSession
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ReadingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val readingSessionRepository: ReadingSessionRepository,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow(StatsData())
    val stats: StateFlow<StatsData> = _stats.asStateFlow()

    init {
        viewModelScope.launch { loadStats() }
    }

    fun refresh() {
        viewModelScope.launch { loadStats() }
    }

    private suspend fun loadStats() {
        val todayDuration = readingSessionRepository.getTotalDurationToday()
        val weekDuration = readingSessionRepository.getTotalDurationThisWeek()
        val monthDuration = readingSessionRepository.getTotalDurationThisMonth()
        val allTimeDuration = readingSessionRepository.getTotalDurationAllTime()
        val streak = readingSessionRepository.getCurrentStreak()
        val recentSessions = readingSessionRepository.getRecentSessions(15)
        val readingDays = readingSessionRepository.getReadingDays(84)

        // Estimate time to finish current book
        val estimatedCompletion = calculateEstimatedCompletion()

        // Map session bookIds to titles
        val bookTitles = mutableMapOf<String, String>()
        for (session in recentSessions) {
            if (session.bookId !in bookTitles) {
                bookTitles[session.bookId] = bookRepository.getById(session.bookId)?.title ?: "Unknown"
            }
        }

        _stats.value = StatsData(
            todaySeconds = todayDuration,
            weekSeconds = weekDuration,
            monthSeconds = monthDuration,
            allTimeSeconds = allTimeDuration,
            currentStreak = streak,
            recentSessions = recentSessions,
            bookTitles = bookTitles,
            readingDays = readingDays,
            estimatedMinutesToFinish = estimatedCompletion,
        )
    }

    private suspend fun calculateEstimatedCompletion(): Long? {
        // Find the most recently read book that isn't finished
        val allProgress = readingProgressRepository.observeAll().first()
        val currentBook = allProgress
            .filter { it.percentage > 0f && it.percentage < 0.99f }
            .maxByOrNull { it.lastReadAt }
            ?: return null

        val bookSessions = readingSessionRepository.getSessionsForBook(currentBook.bookId)
        if (bookSessions.isEmpty()) return null

        val totalSessionTime = bookSessions.sumOf { it.durationSeconds }
        val totalProgressMade = bookSessions.sumOf { (it.endProgress - it.startProgress).toDouble() }.toFloat()
        if (totalProgressMade <= 0) return null

        val remainingProgress = 1f - currentBook.percentage
        val secondsPerProgress = totalSessionTime / totalProgressMade
        return (remainingProgress * secondsPerProgress / 60).toLong()
    }
}

data class StatsData(
    val todaySeconds: Long = 0,
    val weekSeconds: Long = 0,
    val monthSeconds: Long = 0,
    val allTimeSeconds: Long = 0,
    val currentStreak: Int = 0,
    val recentSessions: List<ReadingSession> = emptyList(),
    val bookTitles: Map<String, String> = emptyMap(),
    val readingDays: Set<Long> = emptySet(),
    val estimatedMinutesToFinish: Long? = null,
)

fun Long.formatDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
