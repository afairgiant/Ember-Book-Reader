package com.ember.reader.ui.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val serverId: Long? = savedStateHandle.get<Long>("serverId")?.takeIf { it != -1L }

    private val _uiState = MutableStateFlow(ServerFormUiState())
    val uiState: StateFlow<ServerFormUiState> = _uiState.asStateFlow()

    init {
        if (serverId != null) {
            viewModelScope.launch {
                serverRepository.getById(serverId)?.let { server ->
                    _uiState.update {
                        it.copy(
                            name = server.name,
                            url = server.url,
                            opdsUsername = server.opdsUsername,
                            opdsPassword = server.opdsPassword,
                            kosyncUsername = server.kosyncUsername,
                            kosyncPassword = server.kosyncPassword,
                            isEditing = true,
                        )
                    }
                }
            }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }
    fun updateUrl(value: String) = _uiState.update { it.copy(url = value) }
    fun updateOpdsUsername(value: String) = _uiState.update { it.copy(opdsUsername = value) }
    fun updateOpdsPassword(value: String) = _uiState.update { it.copy(opdsPassword = value) }
    fun updateKosyncUsername(value: String) = _uiState.update { it.copy(kosyncUsername = value) }
    fun updateKosyncPassword(value: String) = _uiState.update { it.copy(kosyncPassword = value) }

    fun testOpdsConnection() {
        val state = _uiState.value
        if (state.url.isBlank() || state.opdsUsername.isBlank()) return

        _uiState.update { it.copy(opdsTestResult = TestResult.Testing) }
        viewModelScope.launch {
            val result = serverRepository.testOpdsConnection(
                url = state.url,
                username = state.opdsUsername,
                password = state.opdsPassword,
            )
            _uiState.update {
                it.copy(
                    opdsTestResult = result.fold(
                        onSuccess = { msg -> TestResult.Success(msg) },
                        onFailure = { err -> TestResult.Error(err.message ?: "Connection failed") },
                    ),
                )
            }
        }
    }

    fun testKosyncConnection() {
        val state = _uiState.value
        if (state.url.isBlank() || state.kosyncUsername.isBlank()) return

        _uiState.update { it.copy(kosyncTestResult = TestResult.Testing) }
        viewModelScope.launch {
            val result = serverRepository.testKosyncConnection(
                url = state.url,
                username = state.kosyncUsername,
                password = state.kosyncPassword,
            )
            _uiState.update {
                it.copy(
                    kosyncTestResult = result.fold(
                        onSuccess = { TestResult.Success("Connected") },
                        onFailure = { err -> TestResult.Error(err.message ?: "Auth failed") },
                    ),
                )
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank() || state.url.isBlank()) {
            _uiState.update { it.copy(validationError = "Name and URL are required") }
            return
        }

        _uiState.update { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            val server = Server(
                id = serverId ?: 0,
                name = state.name.trim(),
                url = state.url.trim().trimEnd('/'),
                opdsUsername = state.opdsUsername.trim(),
                opdsPassword = state.opdsPassword,
                kosyncUsername = state.kosyncUsername.trim(),
                kosyncPassword = state.kosyncPassword,
            )
            serverRepository.save(server)
            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }
}

data class ServerFormUiState(
    val name: String = "",
    val url: String = "",
    val opdsUsername: String = "",
    val opdsPassword: String = "",
    val kosyncUsername: String = "",
    val kosyncPassword: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val opdsTestResult: TestResult = TestResult.Idle,
    val kosyncTestResult: TestResult = TestResult.Idle,
)

sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    data class Success(val message: String) : TestResult
    data class Error(val message: String) : TestResult
}
