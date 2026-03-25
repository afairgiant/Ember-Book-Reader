package com.ember.reader.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    val uiState: StateFlow<ServerListUiState> = serverRepository.observeAll()
        .map { servers ->
            if (servers.isEmpty()) {
                ServerListUiState.Empty
            } else {
                ServerListUiState.Success(servers)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerListUiState.Loading)

    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            serverRepository.delete(serverId)
        }
    }
}

sealed interface ServerListUiState {
    data object Loading : ServerListUiState
    data object Empty : ServerListUiState
    data class Success(val servers: List<Server>) : ServerListUiState
}
