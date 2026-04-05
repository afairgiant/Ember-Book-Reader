package com.ember.reader.ui.hardcover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.hardcover.HardcoverBook
import com.ember.reader.core.hardcover.HardcoverBookDetail
import com.ember.reader.core.hardcover.HardcoverClient
import com.ember.reader.core.hardcover.HardcoverStatus
import com.ember.reader.core.hardcover.HardcoverTokenManager
import com.ember.reader.core.hardcover.HardcoverUser
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HardcoverViewModel @Inject constructor(
    private val hardcoverClient: HardcoverClient,
    private val tokenManager: HardcoverTokenManager,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HardcoverUiState>(HardcoverUiState.Loading)
    val uiState: StateFlow<HardcoverUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _selectedBookDetail = MutableStateFlow<HardcoverBookDetail?>(null)
    val selectedBookDetail: StateFlow<HardcoverBookDetail?> = _selectedBookDetail.asStateFlow()

    fun dismissMessage() {
        _message.value = null
    }

    init {
        if (tokenManager.isConnected()) {
            loadProfile()
        } else {
            _uiState.value = HardcoverUiState.NotConnected
        }
    }

    fun connect(token: String) {
        if (token.isBlank()) return
        tokenManager.storeToken(token)
        loadProfile()
    }

    fun selectBook(bookId: Int) {
        viewModelScope.launch {
            hardcoverClient.fetchBookDetail(bookId)
                .onSuccess { _selectedBookDetail.value = it }
                .onFailure { Timber.w(it, "Hardcover: failed to fetch book detail") }
        }
    }

    fun clearSelectedBook() {
        _selectedBookDetail.value = null
    }

    suspend fun findGrimmoryServerId(): Long? {
        return serverRepository.getAll().firstOrNull { it.isGrimmory }?.id
    }

    fun disconnect() {
        tokenManager.disconnect()
        _uiState.value = HardcoverUiState.NotConnected
    }

    private fun loadProfile() {
        _uiState.value = HardcoverUiState.Loading
        viewModelScope.launch {
            hardcoverClient.fetchMe()
                .onSuccess { user ->
                    _uiState.value = HardcoverUiState.Connected(
                        user = user,
                        tabs = listOf(
                            HardcoverStatus.CURRENTLY_READING,
                            HardcoverStatus.WANT_TO_READ,
                            HardcoverStatus.READ,
                            HardcoverStatus.DID_NOT_FINISH,
                        ),
                    )
                    loadAllTabs(user.id)
                }
                .onFailure { error ->
                    Timber.w(error, "Hardcover: failed to fetch profile")
                    tokenManager.disconnect()
                    _uiState.value = HardcoverUiState.NotConnected
                    _message.value = "Invalid or expired token"
                }
        }
    }

    private fun loadAllTabs(userId: Int) {
        val statusIds = listOf(
            HardcoverStatus.CURRENTLY_READING,
            HardcoverStatus.WANT_TO_READ,
            HardcoverStatus.READ,
            HardcoverStatus.DID_NOT_FINISH,
        )
        statusIds.forEach { statusId ->
            viewModelScope.launch {
                hardcoverClient.fetchBooksByStatus(userId, statusId)
                    .onSuccess { books ->
                        val current = _uiState.value
                        if (current is HardcoverUiState.Connected) {
                            _uiState.value = current.copy(
                                booksByStatus = current.booksByStatus + (statusId to books),
                            )
                        }
                    }
                    .onFailure { Timber.w(it, "Hardcover: failed to fetch status $statusId") }
            }
        }
    }
}

sealed interface HardcoverUiState {
    data object Loading : HardcoverUiState
    data object NotConnected : HardcoverUiState
    data class Connected(
        val user: HardcoverUser,
        val tabs: List<Int>,
        val booksByStatus: Map<Int, List<HardcoverBook>> = emptyMap(),
    ) : HardcoverUiState
}
