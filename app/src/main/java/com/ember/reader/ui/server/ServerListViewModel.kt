package com.ember.reader.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    bookRepository: BookRepository,
    readingProgressRepository: ReadingProgressRepository,
) : ViewModel() {

    val uiState: StateFlow<ServerListUiState> = serverRepository.observeAll()
        .map { servers -> ServerListUiState.Success(servers) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerListUiState.Loading)

    private val _coverAuthHeaders = MutableStateFlow<Map<Long, String>>(emptyMap())
    val coverAuthHeaders: StateFlow<Map<Long, String>> = _coverAuthHeaders.asStateFlow()

    init {
        viewModelScope.launch {
            serverRepository.observeAll().collect { servers ->
                _coverAuthHeaders.value = servers.associate { server ->
                    val credentials = "${server.opdsUsername}:${server.opdsPassword}"
                    val encoded = android.util.Base64.encodeToString(
                        credentials.toByteArray(),
                        android.util.Base64.NO_WRAP,
                    )
                    server.id to "Basic $encoded"
                }
            }
        }
    }

    val recentlyReading: StateFlow<List<RecentBook>> = bookRepository.observeRecentlyReading()
        .combine(readingProgressRepository.observeAll()) { books, progressList ->
            books.map { book ->
                val progress = progressList.find { it.bookId == book.id }
                RecentBook(book = book, percentage = progress?.percentage ?: 0f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            serverRepository.delete(serverId)
        }
    }
}

data class RecentBook(
    val book: Book,
    val percentage: Float,
)

sealed interface ServerListUiState {
    data object Loading : ServerListUiState
    data class Success(val servers: List<Server>) : ServerListUiState
}
