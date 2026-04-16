package com.ember.reader.ui.settings.stats

import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryGenreStat
import com.ember.reader.core.grimmory.GrimmoryPeakHour
import com.ember.reader.core.grimmory.GrimmoryStreakResponse
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.ReadingSession
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ReadingSessionRepository
import com.ember.reader.core.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class StatsViewModelTest {

    private val readingSessionRepository: ReadingSessionRepository = mockk(relaxed = true)
    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val readingProgressRepository: ReadingProgressRepository = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val grimmoryClient: GrimmoryClient = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val testServer = Server(
        id = 1L, name = "Grimmory", url = "http://localhost",
        opdsUsername = "", opdsPassword = "",
        kosyncUsername = "", kosyncPassword = "",
        isGrimmory = true
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Default stubs for local stats
        coEvery { readingSessionRepository.getTotalDurationToday() } returns 1800L
        coEvery { readingSessionRepository.getTotalDurationThisWeek() } returns 7200L
        coEvery { readingSessionRepository.getTotalDurationThisMonth() } returns 36000L
        coEvery { readingSessionRepository.getTotalDurationAllTime() } returns 360000L
        coEvery { readingSessionRepository.getCurrentStreak() } returns 5
        coEvery { readingSessionRepository.getRecentSessions(any()) } returns emptyList()
        coEvery { readingSessionRepository.getReadingDays(any()) } returns emptySet()
        coEvery { readingProgressRepository.observeAll() } returns flowOf(emptyList())
        coEvery { serverRepository.getAll() } returns emptyList()

        // Default Grimmory stubs (return failure so getOrNull() → null)
        coEvery {
            grimmoryClient.getReadingStreak(any(), any())
        } returns Result.failure(Exception("not stubbed"))
        coEvery {
            grimmoryClient.getPeakHours(any(), any())
        } returns Result.failure(Exception("not stubbed"))
        coEvery {
            grimmoryClient.getFavoriteDays(any(), any())
        } returns Result.failure(Exception("not stubbed"))
        coEvery {
            grimmoryClient.getBookDistributions(any(), any())
        } returns Result.failure(Exception("not stubbed"))
        coEvery {
            grimmoryClient.getGenreStats(any(), any())
        } returns Result.failure(Exception("not stubbed"))
        coEvery {
            grimmoryClient.getReadingTimeline(any(), any(), any(), any())
        } returns Result.failure(Exception("not stubbed"))
        coEvery {
            grimmoryClient.getSessionScatter(any(), any(), any())
        } returns Result.failure(Exception("not stubbed"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StatsViewModel(
        readingSessionRepository, bookRepository,
        readingProgressRepository, serverRepository, grimmoryClient
    )

    @Test
    fun `loadStats assembles local session data correctly`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val stats = vm.stats.value
        assertEquals(1800L, stats.todaySeconds)
        assertEquals(7200L, stats.weekSeconds)
        assertEquals(36000L, stats.monthSeconds)
        assertEquals(360000L, stats.allTimeSeconds)
        assertEquals(5, stats.currentStreak)
    }

    @Test
    fun `loadStats resolves book titles for recent sessions`() = runTest {
        val sessions = listOf(
            ReadingSession(
                bookId = "book-1",
                startTime = Instant.now(), endTime = Instant.now(),
                durationSeconds = 600, startProgress = 0.1f, endProgress = 0.2f
            )
        )
        coEvery { readingSessionRepository.getRecentSessions(any()) } returns sessions
        coEvery { bookRepository.getById("book-1") } returns mockk { every { title } returns "My Book" }

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("My Book", vm.stats.value.bookTitles["book-1"])
    }

    @Test
    fun `grimmory stats populated when server exists`() = runTest {
        coEvery { serverRepository.getAll() } returns listOf(testServer)

        val streak = GrimmoryStreakResponse(
            currentStreak = 10, longestStreak = 30, totalReadingDays = 100,
            last52Weeks = emptyList()
        )
        coEvery { grimmoryClient.getReadingStreak(any(), any()) } returns Result.success(streak)

        val peakHours =
            listOf(GrimmoryPeakHour(hourOfDay = 21, sessionCount = 15, totalDurationSeconds = 9000))
        coEvery { grimmoryClient.getPeakHours(any(), any()) } returns Result.success(peakHours)

        val vm = createViewModel()
        advanceUntilIdle()

        val stats = vm.stats.value
        assertTrue(stats.isGrimmoryConnected)
        assertNotNull(stats.grimmoryStreak)
        assertEquals(10, stats.grimmoryStreak!!.currentStreak)
        assertNotNull(stats.peakHours)
        assertEquals(21, stats.peakHours!!.first().hourOfDay)
    }

    @Test
    fun `partial grimmory failure still populates available fields`() = runTest {
        coEvery { serverRepository.getAll() } returns listOf(testServer)

        val streak = GrimmoryStreakResponse(
            currentStreak = 5, longestStreak = 20, totalReadingDays = 50,
            last52Weeks = emptyList()
        )
        coEvery { grimmoryClient.getReadingStreak(any(), any()) } returns Result.success(streak)
        // Peak hours fails
        coEvery { grimmoryClient.getPeakHours(any(), any()) } returns Result.failure(Exception("timeout"))

        val vm = createViewModel()
        advanceUntilIdle()

        val stats = vm.stats.value
        assertTrue(stats.isGrimmoryConnected)
        assertNotNull(stats.grimmoryStreak)
        assertEquals(5, stats.grimmoryStreak!!.currentStreak)
        // Peak hours null because it failed (getOrNull returns null)
        assertNull(stats.peakHours)
    }

    @Test
    fun `genreStats sorted by duration and capped at 8`() = runTest {
        coEvery { serverRepository.getAll() } returns listOf(testServer)

        val genres = (1..12).map { i ->
            GrimmoryGenreStat(
                genre = "Genre $i",
                bookCount = 10, totalSessions = 5,
                totalDurationSeconds = i * 1000L,
                averageSessionsPerBook = 1.0
            )
        }
        coEvery { grimmoryClient.getGenreStats(any(), any()) } returns Result.success(genres)

        val vm = createViewModel()
        advanceUntilIdle()

        val stats = vm.stats.value
        assertNotNull(stats.genreStats)
        assertEquals(8, stats.genreStats!!.size)
        // Should be sorted descending by duration — Genre 12 (12000) first
        assertEquals("Genre 12", stats.genreStats!!.first().genre)
        assertEquals("Genre 5", stats.genreStats!!.last().genre)
    }

    @Test
    fun `estimatedCompletion returns null when no active book`() = runTest {
        // All books either at 0% or 100%
        coEvery { readingProgressRepository.observeAll() } returns flowOf(
            listOf(
                ReadingProgress(bookId = "b1", percentage = 0f),
                ReadingProgress(bookId = "b2", percentage = 1.0f)
            )
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertNull(vm.stats.value.estimatedMinutesToFinish)
    }

    @Test
    fun `estimatedCompletion calculates minutes remaining`() = runTest {
        // A book at 50% with sessions totaling 3600s over 50% progress
        val progress = ReadingProgress(
            bookId = "b1", percentage = 0.5f,
            lastReadAt = Instant.now()
        )
        coEvery { readingProgressRepository.observeAll() } returns flowOf(listOf(progress))
        coEvery { readingSessionRepository.getSessionsForBook("b1") } returns listOf(
            ReadingSession(
                bookId = "b1", startTime = Instant.now(), endTime = Instant.now(),
                durationSeconds = 3600L, startProgress = 0f, endProgress = 0.5f
            )
        )

        val vm = createViewModel()
        advanceUntilIdle()

        val estimate = vm.stats.value.estimatedMinutesToFinish
        assertNotNull(estimate)
        // Remaining = 50%, rate = 3600s / 0.5 = 7200s per 100%, remaining = 0.5 * 7200 / 60 = 60 min
        assertEquals(60L, estimate)
    }
}
