package com.ember.reader.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerListViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
) : ViewModel() {

    val uiState: StateFlow<ServerListUiState> = serverRepository.observeAll()
        .map { servers -> ServerListUiState.Success(servers) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerListUiState.Loading)

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
