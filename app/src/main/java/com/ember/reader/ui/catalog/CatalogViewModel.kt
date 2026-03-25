package com.ember.reader.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsFeed
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val opdsClient: OpdsClient,
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private val path: String = savedStateHandle.get<String>("path") ?: "/api/v1/opds"

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var server: Server? = null

    init {
        viewModelScope.launch { loadCatalog() }
    }

    private suspend fun loadCatalog() {
        val loadedServer = serverRepository.getById(serverId)
        if (loadedServer == null) {
            _uiState.value = CatalogUiState.Error("Server not found")
            return
        }
        server = loadedServer
        fetchFeed()
    }

    fun refresh() {
        _uiState.value = CatalogUiState.Loading
        viewModelScope.launch { fetchFeed() }
    }

    private suspend fun fetchFeed() {
        val currentServer = server ?: return
        val result = opdsClient.fetchCatalog(
            baseUrl = currentServer.url,
            username = currentServer.opdsUsername,
            password = currentServer.opdsPassword,
            path = path,
        )
        result.fold(
            onSuccess = { feed ->
                _uiState.value = CatalogUiState.Success(feed = feed)
            },
            onFailure = { error ->
                _uiState.value = CatalogUiState.Error(error.message ?: "Failed to load catalog")
            },
        )
    }
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Success(val feed: OpdsFeed) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}
