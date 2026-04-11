package com.ember.reader.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryBookSummary
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ReadingSessionRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val bookRepository: BookRepository,
    readingProgressRepository: ReadingProgressRepository,
    private val readingSessionRepository: ReadingSessionRepository,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryClient: GrimmoryClient,
) : ViewModel() {

    private val _quickStats = MutableStateFlow<QuickStats?>(null)
    val quickStats: StateFlow<QuickStats?> = _quickStats.asStateFlow()

    private var grimmoryContentJob: Job? = null

    init {
        viewModelScope.launch {
            // Load local stats immediately as fallback
            val todaySeconds = readingSessionRepository.getTotalDurationToday()
            val localStreak = readingSessionRepository.getCurrentStreak()
            _quickStats.value = QuickStats(todaySeconds = todaySeconds, currentStreak = localStreak)
        }
        viewModelScope.launch {
            serverRepository.observeAll()
                .map { servers -> servers.firstOrNull { it.isGrimmory } }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { grimmoryServer ->
                    if (grimmoryServer != null) {
                        grimmoryContentJob?.cancel()
                        grimmoryContentJob = launch { loadGrimmoryContent(grimmoryServer) }
                    }
                }
        }
    }

    private suspend fun loadGrimmoryContent(grimmoryServer: Server) {
        if (!grimmoryTokenManager.isLoggedIn(grimmoryServer.id)) {
            serverRepository.tryGrimmoryRelogin(grimmoryServer)
        }
        if (grimmoryTokenManager.isLoggedIn(grimmoryServer.id)) {
            loadRecentlyAdded(grimmoryServer)
            loadGrimmoryStats(grimmoryServer)
        } else {
            // Token may not be ready yet — retry once after a short delay
            kotlinx.coroutines.delay(2000)
            if (!grimmoryTokenManager.isLoggedIn(grimmoryServer.id)) {
                serverRepository.tryGrimmoryRelogin(grimmoryServer)
            }
            if (grimmoryTokenManager.isLoggedIn(grimmoryServer.id)) {
                loadRecentlyAdded(grimmoryServer)
                loadGrimmoryStats(grimmoryServer)
            }
        }
    }

    private suspend fun loadGrimmoryStats(server: Server) {
        grimmoryClient.getReadingStreak(server.url, server.id)
            .onSuccess { streak ->
                val current = _quickStats.value
                _quickStats.value = QuickStats(
                    todaySeconds = current?.todaySeconds ?: 0L,
                    currentStreak = streak.currentStreak,
                )
            }
            .onFailure { Timber.w(it, "Failed to load Grimmory streak") }
    }

    private suspend fun loadRecentlyAdded(server: Server) {
        grimmoryAppClient.getRecentlyAdded(server.url, server.id, limit = 10)
            .onSuccess { books ->
                _recentlyAdded.value = books.map { summary ->
                    val opdsEntryId = "urn:booklore:book:${summary.id}"
                    val localBook = bookRepository.getByOpdsEntryId(opdsEntryId, server.id)
                    val localId = localBook?.id ?: bookRepository.ensureBookExists(
                        serverId = server.id,
                        opdsEntryId = opdsEntryId,
                        title = summary.title,
                        author = summary.authors.joinToString(", ").ifBlank { null },
                        coverUrl = grimmoryAppClient.coverUrl(server.url, summary.id, summary.coverUpdatedOn),
                        format = when (summary.primaryFileType?.uppercase()) {
                            "PDF" -> BookFormat.PDF
                            else -> BookFormat.EPUB
                        },
                    )
                    RecentlyAddedBook(
                        summary = summary,
                        coverUrl = grimmoryAppClient.coverUrl(server.url, summary.id, summary.coverUpdatedOn),
                        serverId = server.id,
                        localBookId = localId,
                    )
                }
            }
            .onFailure { Timber.w(it, "Failed to load recently added books") }
    }

    private val _recentlyAdded = MutableStateFlow<List<RecentlyAddedBook>>(emptyList())
    val recentlyAdded: StateFlow<List<RecentlyAddedBook>> = _recentlyAdded.asStateFlow()

    val recentlyReading: StateFlow<List<RecentBook>> = bookRepository.observeRecentlyReading()
        .combine(readingProgressRepository.observeAll()) { books, progressList ->
            val progressMap = progressList.associateBy { it.bookId }
            books.map { book ->
                RecentBook(book = book, percentage = progressMap[book.id]?.percentage ?: 0f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

}

data class QuickStats(
    val todaySeconds: Long,
    val currentStreak: Int,
)

data class RecentBook(
    val book: Book,
    val percentage: Float
)

data class RecentlyAddedBook(
    val summary: GrimmoryBookSummary,
    val coverUrl: String,
    val serverId: Long,
    val localBookId: String? = null,
)
