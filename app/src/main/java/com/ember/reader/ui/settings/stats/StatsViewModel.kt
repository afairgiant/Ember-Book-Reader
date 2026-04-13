package com.ember.reader.ui.settings.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryBookDistributions
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryFavoriteDay
import com.ember.reader.core.grimmory.GrimmoryGenreStat
import com.ember.reader.core.grimmory.GrimmoryPeakHour
import com.ember.reader.core.grimmory.GrimmorySessionScatter
import com.ember.reader.core.grimmory.GrimmoryStreakResponse
import com.ember.reader.core.grimmory.GrimmoryTimelineEntry
import com.ember.reader.core.model.ReadingSession
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ReadingSessionRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.Year
import java.time.temporal.WeekFields
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val readingSessionRepository: ReadingSessionRepository,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val serverRepository: ServerRepository,
    private val grimmoryClient: GrimmoryClient
) : ViewModel() {

    private val _stats = MutableStateFlow(StatsData())
    val stats: StateFlow<StatsData> = _stats.asStateFlow()

    init {
        viewModelScope.launch { loadStats() }
    }

    fun refresh() {
        viewModelScope.launch { loadStats() }
    }

    fun loadTimeline(year: Int, week: Int) {
        viewModelScope.launch {
            val server = findGrimmoryServer() ?: return@launch
            val result = grimmoryClient.getReadingTimeline(
                server.url,
                server.id,
                year,
                week
            ).getOrNull()
            _stats.value = _stats.value.copy(timeline = result)
        }
    }

    private suspend fun loadStats() {
        val todayDuration = readingSessionRepository.getTotalDurationToday()
        val weekDuration = readingSessionRepository.getTotalDurationThisWeek()
        val monthDuration = readingSessionRepository.getTotalDurationThisMonth()
        val allTimeDuration = readingSessionRepository.getTotalDurationAllTime()
        val streak = readingSessionRepository.getCurrentStreak()
        val recentSessions = readingSessionRepository.getRecentSessions(15)
        val readingDays = readingSessionRepository.getReadingDays(84)

        val estimatedCompletion = calculateEstimatedCompletion()

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
            estimatedMinutesToFinish = estimatedCompletion
        )

        val grimmoryServer = findGrimmoryServer()
        if (grimmoryServer != null) {
            loadGrimmoryStats(grimmoryServer)
        }
    }

    private suspend fun findGrimmoryServer(): Server? =
        serverRepository.getAll().firstOrNull { it.isGrimmory }

    private suspend fun loadGrimmoryStats(server: Server) {
        val baseUrl = server.url
        val serverId = server.id
        val currentYear = Year.now().value

        try {
            coroutineScope {
                val streakDeferred = async {
                    grimmoryClient.getReadingStreak(baseUrl, serverId).getOrNull()
                }
                val peakHoursDeferred = async {
                    grimmoryClient.getPeakHours(baseUrl, serverId).getOrNull()
                }
                val favoriteDaysDeferred = async {
                    grimmoryClient.getFavoriteDays(baseUrl, serverId).getOrNull()
                }
                val distributionsDeferred = async {
                    grimmoryClient.getBookDistributions(baseUrl, serverId).getOrNull()
                }
                val genresDeferred = async {
                    grimmoryClient.getGenreStats(baseUrl, serverId).getOrNull()
                }
                val timelineDeferred = async {
                    val week = LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear())
                    grimmoryClient.getReadingTimeline(baseUrl, serverId, currentYear, week).getOrNull()
                }
                val scatterDeferred = async {
                    grimmoryClient.getSessionScatter(baseUrl, serverId, currentYear).getOrNull()
                }

                val grimmoryStreak = streakDeferred.await()
                val peakHours = peakHoursDeferred.await()
                val favoriteDays = favoriteDaysDeferred.await()
                val distributions = distributionsDeferred.await()
                val genres = genresDeferred.await()
                val timeline = timelineDeferred.await()
                val scatter = scatterDeferred.await()

                _stats.value = _stats.value.copy(
                    isGrimmoryConnected = true,
                    grimmoryStreak = grimmoryStreak,
                    peakHours = peakHours,
                    favoriteDays = favoriteDays,
                    bookDistributions = distributions,
                    genreStats = genres?.sortedByDescending { it.totalDurationSeconds }?.take(8),
                    timeline = timeline,
                    sessionScatter = scatter,
                    grimmoryServerUrl = server.url
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load Grimmory stats, falling back to local only")
        }
    }

    private suspend fun calculateEstimatedCompletion(): Long? {
        val allProgress = readingProgressRepository.observeAll().first()
        val currentBook = allProgress
            .filter { it.percentage > 0f && it.percentage < 0.99f }
            .maxByOrNull { it.lastReadAt }
            ?: return null

        val bookSessions = readingSessionRepository.getSessionsForBook(currentBook.bookId)
        if (bookSessions.isEmpty()) return null

        val totalSessionTime = bookSessions.sumOf { it.durationSeconds }
        val totalProgressMade = bookSessions.sumOf {
            (it.endProgress - it.startProgress).toDouble()
        }.toFloat()
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
    // Grimmory remote stats
    val isGrimmoryConnected: Boolean = false,
    val grimmoryStreak: GrimmoryStreakResponse? = null,
    val peakHours: List<GrimmoryPeakHour>? = null,
    val favoriteDays: List<GrimmoryFavoriteDay>? = null,
    val bookDistributions: GrimmoryBookDistributions? = null,
    val genreStats: List<GrimmoryGenreStat>? = null,
    val timeline: List<GrimmoryTimelineEntry>? = null,
    val sessionScatter: List<GrimmorySessionScatter>? = null,
    val grimmoryServerUrl: String? = null
)
