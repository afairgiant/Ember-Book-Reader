package com.ember.reader.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.hardcover.HardcoverTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.SyncStatus
import com.ember.reader.core.sync.SyncStatusProber
import com.ember.reader.core.sync.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    syncStatusRepository: SyncStatusRepository,
    private val syncStatusProber: SyncStatusProber,
    private val hardcoverTokenManager: HardcoverTokenManager
) : ViewModel() {
    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatuses: StateFlow<Map<Long, SyncStatus>> = syncStatusRepository.statuses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isHardcoverConnected: Boolean
        get() = hardcoverTokenManager.isConnected()

    init {
        // Refresh per-server sync health on screen open so the dot is
        // accurate without waiting for an actual push/pull/background-sync.
        viewModelScope.launch {
            val currentServers = serverRepository.observeAll().first()
            syncStatusProber.probeAll(currentServers)
        }
    }
}
