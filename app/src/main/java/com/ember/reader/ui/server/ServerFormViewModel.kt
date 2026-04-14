package com.ember.reader.ui.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.GrimmoryUserPermissions
import com.ember.reader.core.model.Server
import com.ember.reader.core.model.Server.Companion.GRIMMORY_OPDS_PATH
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ServerFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository
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
                            grimmoryUsername = server.grimmoryUsername,
                            grimmoryPassword = server.grimmoryPassword,
                            isGrimmory = server.isGrimmory,
                            isEditing = true
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
    fun updateGrimmoryUsername(value: String) =
        _uiState.update { it.copy(grimmoryUsername = value) }
    fun updateGrimmoryPassword(value: String) =
        _uiState.update { it.copy(grimmoryPassword = value) }

    fun testOpdsConnection() {
        val state = _uiState.value
        if (state.url.isBlank() || state.opdsUsername.isBlank()) return

        _uiState.update { it.copy(opdsTestResult = TestResult.Testing) }
        viewModelScope.launch {
            // Also detect if this is a Grimmory server
            val isGrimmory = serverRepository.detectGrimmory(state.url)
            val opdsUrl = if (isGrimmory && "/opds" !in state.url) {
                state.url.trimEnd('/') + GRIMMORY_OPDS_PATH
            } else {
                state.url
            }
            val result = serverRepository.testOpdsConnection(
                url = opdsUrl,
                username = state.opdsUsername,
                password = state.opdsPassword
            )
            _uiState.update {
                it.copy(
                    isGrimmory = isGrimmory,
                    opdsTestResult = result.fold(
                        onSuccess = { msg -> TestResult.Success(msg) },
                        onFailure = { err -> TestResult.Error(err.message ?: "Connection failed") }
                    )
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
                password = state.kosyncPassword
            )
            _uiState.update {
                it.copy(
                    kosyncTestResult = result.fold(
                        onSuccess = { TestResult.Success("Connected") },
                        onFailure = { err -> TestResult.Error(err.message ?: "Auth failed") }
                    )
                )
            }
        }
    }

    fun testOpdsWithGrimmoryCredentials() {
        val state = _uiState.value
        if (state.url.isBlank() || state.grimmoryUsername.isBlank()) return

        _uiState.update { it.copy(opdsTestResult = TestResult.Testing) }
        viewModelScope.launch {
            val opdsUrl = if (state.isGrimmory && "/opds" !in state.url) {
                state.url.trimEnd('/') + GRIMMORY_OPDS_PATH
            } else {
                state.url
            }
            val result = serverRepository.testOpdsConnection(
                url = opdsUrl,
                username = state.grimmoryUsername,
                password = state.grimmoryPassword
            )
            _uiState.update {
                it.copy(
                    opdsTestResult = result.fold(
                        onSuccess = { msg -> TestResult.Success(msg) },
                        onFailure = { err -> TestResult.Error(err.message ?: "Connection failed") }
                    )
                )
            }
        }
    }

    fun testKosyncWithGrimmoryCredentials() {
        val state = _uiState.value
        if (state.url.isBlank() || state.grimmoryUsername.isBlank()) return

        _uiState.update { it.copy(kosyncTestResult = TestResult.Testing) }
        viewModelScope.launch {
            val result = serverRepository.testKosyncConnection(
                url = state.url,
                username = state.grimmoryUsername,
                password = state.grimmoryPassword
            )
            _uiState.update {
                it.copy(
                    kosyncTestResult = result.fold(
                        onSuccess = { TestResult.Success("Connected") },
                        onFailure = { err -> TestResult.Error(err.message ?: "Auth failed") }
                    )
                )
            }
        }
    }

    fun testGrimmoryConnection() {
        val state = _uiState.value
        if (state.url.isBlank() || state.grimmoryUsername.isBlank()) return

        _uiState.update {
            it.copy(grimmoryTestResult = TestResult.Testing, grimmoryTestPermissions = null)
        }
        viewModelScope.launch {
            val result = serverRepository.testGrimmoryConnection(
                url = state.url,
                username = state.grimmoryUsername,
                password = state.grimmoryPassword
            )
            _uiState.update {
                it.copy(
                    isGrimmory = result.isSuccess || it.isGrimmory,
                    grimmoryTestResult = result.fold(
                        onSuccess = { user -> TestResult.Success("Logged in as ${user.username}") },
                        onFailure = { err -> TestResult.Error(err.message ?: "Login failed") }
                    ),
                    grimmoryTestPermissions = result.getOrNull()?.permissions
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
                grimmoryUsername = state.grimmoryUsername.trim(),
                grimmoryPassword = state.grimmoryPassword,
                isGrimmory = state.isGrimmory
            )
            serverRepository.save(server)
            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }

    fun saveGrimmory(
        useGrimmoryLoginForOpds: Boolean,
        useGrimmoryLoginForKosync: Boolean,
        onSuccess: () -> Unit
    ) {
        val state = _uiState.value
        if (state.name.isBlank() || state.url.isBlank()) {
            _uiState.update { it.copy(validationError = "Name and URL are required") }
            return
        }
        if (state.grimmoryUsername.isBlank()) {
            _uiState.update { it.copy(validationError = "Grimmory username is required") }
            return
        }

        _uiState.update { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            val grimmoryUser = state.grimmoryUsername.trim()
            val grimmoryPass = state.grimmoryPassword

            val opdsUser = if (useGrimmoryLoginForOpds) grimmoryUser else state.opdsUsername.trim()
            val opdsPass = if (useGrimmoryLoginForOpds) grimmoryPass else state.opdsPassword
            val kosyncUser = if (useGrimmoryLoginForKosync) grimmoryUser else state.kosyncUsername.trim()
            val kosyncPass = if (useGrimmoryLoginForKosync) grimmoryPass else state.kosyncPassword

            val server = Server(
                id = serverId ?: 0,
                name = state.name.trim(),
                url = state.url.trim().trimEnd('/'),
                opdsUsername = opdsUser,
                opdsPassword = opdsPass,
                kosyncUsername = kosyncUser,
                kosyncPassword = kosyncPass,
                grimmoryUsername = grimmoryUser,
                grimmoryPassword = grimmoryPass,
                isGrimmory = true
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
    val grimmoryUsername: String = "",
    val grimmoryPassword: String = "",
    val isGrimmory: Boolean = false,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val opdsTestResult: TestResult = TestResult.Idle,
    val kosyncTestResult: TestResult = TestResult.Idle,
    val grimmoryTestResult: TestResult = TestResult.Idle,
    val grimmoryTestPermissions: GrimmoryUserPermissions? = null
)

sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    data class Success(val message: String) : TestResult
    data class Error(val message: String) : TestResult
}
