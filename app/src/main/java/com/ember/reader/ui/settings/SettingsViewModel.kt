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

    val syncNotifications: StateFlow<Boolean> = appPreferencesRepository.syncNotificationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
