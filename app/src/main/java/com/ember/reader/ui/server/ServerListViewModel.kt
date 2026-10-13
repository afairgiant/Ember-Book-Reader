package com.ember.reader.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryBookSummary
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ReadingSessionRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.NewBooksChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val newBooksChecker: NewBooksChecker
) : ViewModel() {

    private val _quickStats = MutableStateFlow<QuickStats?>(null)
    val quickStats: StateFlow<QuickStats?> = _quickStats.asStateFlow()

    private val _hasGrimmoryServer = MutableStateFlow(false)
    val hasGrimmoryServer: StateFlow<Boolean> = _hasGrimmoryServer.asStateFlow()

    private val _recentlyAddedLoading = MutableStateFlow(false)
    val recentlyAddedLoading: StateFlow<Boolean> = _recentlyAddedLoading.asStateFlow()

    private var grimmoryContentJob: Job? = null

    init {
        viewModelScope.launch {
            // Load local stats immediately as fallback
            val weekSeconds = readingSessionRepository.getTotalDurationThisWeek()
            val localStreak = readingSessionRepository.getCurrentStreak()
            _quickStats.value = QuickStats(weekSeconds = weekSeconds, currentStreak = localStreak)
        }
        viewModelScope.launch {
            serverRepository.observeAll()
                .map { servers -> servers.firstOrNull { it.isGrimmory } }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { grimmoryServer ->
                    _hasGrimmoryServer.value = grimmoryServer != null
                    if (grimmoryServer != null) {
                        // Load cached recently added books from Room immediately
                        loadCachedRecentlyAdded(grimmoryServer)
                        grimmoryContentJob?.cancel()
                        grimmoryContentJob = launch { loadGrimmoryContent(grimmoryServer) }
                    }
                }
        }
    }

    private suspend fun loadGrimmoryContent(grimmoryServer: Server) {
        _recentlyAddedLoading.value = true
        try {
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
        } finally {
            _recentlyAddedLoading.value = false
        }
    }

    private suspend fun loadGrimmoryStats(server: Server) {
        val currentYear = java.time.Year.now().value
        val currentWeek = java.time.LocalDate.now()
            .get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())

        coroutineScope {
            val streakDeferred = async {
                grimmoryClient.getReadingStreak(server.url, server.id).getOrNull()
            }
            val timelineDeferred = async {
                grimmoryClient.getReadingTimeline(server.url, server.id, currentYear, currentWeek)
                    .getOrNull()
            }

            val streak = streakDeferred.await()
            val timeline = timelineDeferred.await()

            val grimmoryWeekSeconds = timeline?.sumOf { it.totalDurationSeconds } ?: 0L

            if (streak != null || grimmoryWeekSeconds > 0L) {
                val current = _quickStats.value
                _quickStats.value = QuickStats(
                    weekSeconds = if (grimmoryWeekSeconds > 0L) {
                        grimmoryWeekSeconds
                    } else {
                        current?.weekSeconds ?: 0L
                    },
                    currentStreak = streak?.currentStreak ?: current?.currentStreak ?: 0
                )
            }
        }
    }

    private suspend fun loadRecentlyAdded(server: Server) {
        grimmoryAppClient.getRecentlyAdded(server.url, server.id, limit = 10)
            .onSuccess { books ->
                val localIds = bookRepository.upsertGrimmorySummaries(server, books)
                val result = books.zip(localIds) { summary, localId ->
                    RecentlyAddedBook(
                        summary = summary,
                        coverUrl = grimmoryAppClient.coverUrl(server.url, summary.id, summary.coverUpdatedOn),
                        serverId = server.id,
                        localBookId = localId
                    )
                }
                _recentlyAdded.value = result
                // They have now been shown, so a later sync need not announce them.
                newBooksChecker.markSeen(server, books)
                // Cache the book IDs for instant display on next launch
                val ids = result.mapNotNull { it.localBookId }
                bookRepository.cacheRecentlyAddedIds(ids)
            }
            .onFailure { Timber.w(it, "Failed to load recently added books") }
    }

    private suspend fun loadCachedRecentlyAdded(server: Server) {
        if (_recentlyAdded.value.isNotEmpty()) return
        val cachedIds = bookRepository.getCachedRecentlyAddedIds()
        if (cachedIds.isEmpty()) return
        val cached = cachedIds.mapNotNull { id ->
            val book = bookRepository.getById(id) ?: return@mapNotNull null
            RecentlyAddedBook(
                summary = GrimmoryBookSummary(
                    id = 0,
                    title = book.title,
                    authors = listOfNotNull(book.author),
                    coverUpdatedOn = null
                ),
                coverUrl = book.coverUrl ?: "",
                serverId = server.id,
                localBookId = book.id
            )
        }
        if (cached.isNotEmpty() && _recentlyAdded.value.isEmpty()) {
            _recentlyAdded.value = cached
        }
    }

    private val _recentlyAdded = MutableStateFlow<List<RecentlyAddedBook>>(emptyList())
    val recentlyAdded: StateFlow<List<RecentlyAddedBook>> = _recentlyAdded.asStateFlow()

    /**
     * What Home offers when no book is in progress; null while there is one, and until the
     * library has been read, so a returning reader never sees it flash by.
     */
    val empty: StateFlow<HomeEmpty?> = combine(
        bookRepository.observeRecentlyReading(),
        serverRepository.observeAll(),
        bookRepository.observeDownloadedBooks()
    ) { reading, servers, downloaded ->
        homeEmptyOf(reading = reading.size, servers = servers.size, downloaded = downloaded.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    val weekSeconds: Long,
    val currentStreak: Int
)

data class RecentBook(
    val book: Book,
    val percentage: Float
)

data class RecentlyAddedBook(
    val summary: GrimmoryBookSummary,
    val coverUrl: String,
    val serverId: Long,
    val localBookId: String? = null
)
