package com.ember.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.SyncFrequency
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.repository.SyncPreferencesRepository
import com.ember.reader.core.repository.ThemeMode
import com.ember.reader.core.sync.worker.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val syncScheduler: SyncScheduler,
    private val serverRepository: ServerRepository,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryTokenManager: GrimmoryTokenManager
) : ViewModel() {

    val syncFrequency: StateFlow<SyncFrequency> =
        syncPreferencesRepository.syncFrequencyFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncFrequency.ON_OPEN_CLOSE)

    val themeMode: StateFlow<ThemeMode> = appPreferencesRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val keepScreenOn: StateFlow<Boolean> = appPreferencesRepository.keepScreenOnFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoCleanup: StateFlow<Boolean> = appPreferencesRepository.autoCleanupFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoDownloadReading: StateFlow<Boolean> = appPreferencesRepository.autoDownloadReadingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * False when every connected Grimmory server has explicit `canDownload == false`
     * — in that state the toggle is meaningless because [SyncWorker.autoDownloadReadingBooks]
     * would skip every server. Permissive while permissions are unknown (`null`) and when
     * no Grimmory servers are configured at all (the global pref is harmless then).
     */
    val autoDownloadReadingEnabled: StateFlow<Boolean> = serverRepository.observeAll()
        .map { servers ->
            val grimmoryServers = servers.filter { it.isGrimmory }
            grimmoryServers.isEmpty() || grimmoryServers.any { it.canDownload != false }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val syncNotifications: StateFlow<Boolean> = appPreferencesRepository.syncNotificationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val syncHighlights: StateFlow<Boolean> = appPreferencesRepository.syncHighlightsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncBookmarks: StateFlow<Boolean> = appPreferencesRepository.syncBookmarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Safety net: if permission flips to denied after the user turned the
        // toggle on, force the pref off so reconnecting to a download-capable
        // server doesn't silently reactivate an old enabled state.
        viewModelScope.launch {
            combine(autoDownloadReadingEnabled, autoDownloadReading) { enabled, on ->
                !enabled && on
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { appPreferencesRepository.updateAutoDownloadReading(false) }
        }
    }

    val readingStats: StateFlow<ReadingStats> = combine(
        bookRepository.observeDownloadedBooks(),
        readingProgressRepository.observeAll()
    ) { books, progressList ->
        val progressMap = progressList.associate { it.bookId to it.percentage }
        val downloaded = books.size
        val reading = books.count { book ->
            val p = progressMap[book.id] ?: 0f
            p > 0f && p < 0.99f
        }
        val completed = books.count { book ->
            val p = progressMap[book.id] ?: 0f
            p >= 0.99f
        }
        ReadingStats(downloaded = downloaded, reading = reading, completed = completed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingStats())

    fun isGrimmoryLoggedIn(serverId: Long): Boolean = grimmoryTokenManager.isLoggedIn(serverId)

    fun updateSyncFrequency(frequency: SyncFrequency) {
        viewModelScope.launch {
            syncPreferencesRepository.updateSyncFrequency(frequency)
            syncScheduler.applyFrequency(frequency)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPreferencesRepository.updateThemeMode(mode) }
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateKeepScreenOn(enabled) }
    }

    fun updateAutoCleanup(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateAutoCleanup(enabled) }
    }

    fun updateAutoDownloadReading(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateAutoDownloadReading(enabled) }
    }

    fun updateSyncNotifications(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateSyncNotifications(enabled) }
    }

    fun updateSyncHighlights(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateSyncHighlights(enabled) }
    }

    fun updateSyncBookmarks(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateSyncBookmarks(enabled) }
    }

    val isSyncing: StateFlow<Boolean> = syncScheduler.isSyncRunning()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun syncNow() {
        syncScheduler.syncNow()
    }
}

data class ReadingStats(
    val downloaded: Int = 0,
    val reading: Int = 0,
    val completed: Int = 0
)
