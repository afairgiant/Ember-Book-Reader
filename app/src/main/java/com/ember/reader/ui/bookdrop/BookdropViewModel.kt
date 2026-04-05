package com.ember.reader.ui.bookdrop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.BookdropClient
import com.ember.reader.core.grimmory.BookdropDiscardRequest
import com.ember.reader.core.grimmory.BookdropFile
import com.ember.reader.core.grimmory.BookdropFinalizeFile
import com.ember.reader.core.grimmory.BookdropFinalizeRequest
import com.ember.reader.core.grimmory.BookdropMetadata
import com.ember.reader.core.grimmory.GrimmoryAppLibraryWithPaths
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookdropFileState(
    val file: BookdropFile,
    val editedMetadata: BookdropMetadata,
    val isExpanded: Boolean = false,
    val isChecked: Boolean = false,
    val libraryId: Long? = null,
    val pathId: Long? = null,
)

sealed interface BookdropUiState {
    data object Loading : BookdropUiState
    data object NoServer : BookdropUiState
    data class Success(
        val files: List<BookdropFileState>,
        val libraries: List<GrimmoryAppLibraryWithPaths>,
        val selectedLibraryId: Long?,
        val selectedPathId: Long?,
    ) : BookdropUiState
    data class Error(val message: String) : BookdropUiState
}

@HiltViewModel
class BookdropViewModel @Inject constructor(
    private val bookdropClient: BookdropClient,
    private val serverRepository: ServerRepository,
    private val grimmoryTokenManager: GrimmoryTokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookdropUiState>(BookdropUiState.Loading)
    val uiState: StateFlow<BookdropUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var server: Server? = null

    init {
        viewModelScope.launch {
            val servers = serverRepository.observeAll().first()
            val grimmoryServer = servers.firstOrNull { it.isGrimmory && grimmoryTokenManager.isLoggedIn(it.id) }
            if (grimmoryServer == null) {
                _uiState.value = BookdropUiState.NoServer
                return@launch
            }
            server = grimmoryServer
            loadData()
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadData()
            _isRefreshing.value = false
        }
    }

    fun toggleExpanded(fileId: Long) {
        updateFiles { files ->
            files.map { if (it.file.id == fileId) it.copy(isExpanded = !it.isExpanded) else it }
        }
    }

    fun toggleChecked(fileId: Long) {
        updateFiles { files ->
            files.map { if (it.file.id == fileId) it.copy(isChecked = !it.isChecked) else it }
        }
    }

    fun selectAll() {
        updateFiles { files -> files.map { it.copy(isChecked = true) } }
    }

    fun deselectAll() {
        updateFiles { files -> files.map { it.copy(isChecked = false) } }
    }

    fun selectLibrary(libraryId: Long?) {
        val state = _uiState.value as? BookdropUiState.Success ?: return
        _uiState.value = state.copy(selectedLibraryId = libraryId, selectedPathId = null)
    }

    fun selectPath(pathId: Long?) {
        val state = _uiState.value as? BookdropUiState.Success ?: return
        _uiState.value = state.copy(selectedPathId = pathId)
    }

    fun applyFetchedField(fileId: Long, fieldName: String) {
        updateFiles { files ->
            files.map { fileState ->
                if (fileState.file.id != fileId) return@map fileState
                val fetched = fileState.file.fetchedMetadata ?: return@map fileState
                val current = fileState.editedMetadata
                val updated = when (fieldName) {
                    "title" -> current.copy(title = fetched.title)
                    "subtitle" -> current.copy(subtitle = fetched.subtitle)
                    "authors" -> current.copy(authors = fetched.authors)
                    "publisher" -> current.copy(publisher = fetched.publisher)
                    "publishedDate" -> current.copy(publishedDate = fetched.publishedDate)
                    "seriesName" -> current.copy(seriesName = fetched.seriesName)
                    "seriesNumber" -> current.copy(seriesNumber = fetched.seriesNumber)
                    "language" -> current.copy(language = fetched.language)
                    "isbn13" -> current.copy(isbn13 = fetched.isbn13)
                    "isbn10" -> current.copy(isbn10 = fetched.isbn10)
                    "categories" -> current.copy(categories = fetched.categories)
                    "description" -> current.copy(description = fetched.description)
                    "pageCount" -> current.copy(pageCount = fetched.pageCount)
                    else -> current
                }
                fileState.copy(editedMetadata = updated)
            }
        }
    }

    fun updateField(fileId: Long, fieldName: String, value: String) {
        updateFiles { files ->
            files.map { fileState ->
                if (fileState.file.id != fileId) return@map fileState
                val current = fileState.editedMetadata
                val updated = when (fieldName) {
                    "title" -> current.copy(title = value.ifBlank { null })
                    "subtitle" -> current.copy(subtitle = value.ifBlank { null })
                    "authors" -> current.copy(authors = value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    "publisher" -> current.copy(publisher = value.ifBlank { null })
                    "publishedDate" -> current.copy(publishedDate = value.ifBlank { null })
                    "seriesName" -> current.copy(seriesName = value.ifBlank { null })
                    "seriesNumber" -> current.copy(seriesNumber = value.toFloatOrNull())
                    "language" -> current.copy(language = value.ifBlank { null })
                    "isbn13" -> current.copy(isbn13 = value.ifBlank { null })
                    "isbn10" -> current.copy(isbn10 = value.ifBlank { null })
                    "categories" -> current.copy(categories = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
                    "description" -> current.copy(description = value.ifBlank { null })
                    else -> current
                }
                fileState.copy(editedMetadata = updated)
            }
        }
    }

    fun finalizeSelected() {
        val state = _uiState.value as? BookdropUiState.Success ?: return
        val libraryId = state.selectedLibraryId
        if (libraryId == null) {
            _message.value = "Select a library first"
            return
        }
        val checked = state.files.filter { it.isChecked }
        if (checked.isEmpty()) {
            _message.value = "No files selected"
            return
        }
        val s = server ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            val request = BookdropFinalizeRequest(
                files = checked.map { fileState ->
                    BookdropFinalizeFile(
                        fileId = fileState.file.id,
                        libraryId = fileState.libraryId ?: libraryId,
                        pathId = fileState.pathId ?: state.selectedPathId,
                        metadata = fileState.editedMetadata,
                    )
                },
                defaultLibraryId = libraryId,
                defaultPathId = state.selectedPathId,
            )
            bookdropClient.finalizeImport(s.url, s.id, request)
                .onSuccess { result ->
                    _message.value = "Imported ${result.successCount} book(s)" +
                        if (result.failureCount > 0) ", ${result.failureCount} failed" else ""
                    loadData()
                }
                .onFailure { _message.value = "Finalize failed: ${it.message}" }
            _isRefreshing.value = false
        }
    }

    fun discardSelected() {
        val state = _uiState.value as? BookdropUiState.Success ?: return
        val checked = state.files.filter { it.isChecked }
        if (checked.isEmpty()) {
            _message.value = "No files selected"
            return
        }
        val s = server ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            val request = BookdropDiscardRequest(
                selectedIds = checked.map { it.file.id },
            )
            bookdropClient.discardFiles(s.url, s.id, request)
                .onSuccess {
                    _message.value = "Discarded ${checked.size} file(s)"
                    loadData()
                }
                .onFailure { _message.value = "Discard failed: ${it.message}" }
            _isRefreshing.value = false
        }
    }

    fun rescan() {
        val s = server ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            bookdropClient.rescan(s.url, s.id)
                .onSuccess {
                    _message.value = "Rescan started"
                    loadData()
                }
                .onFailure { _message.value = "Rescan failed: ${it.message}" }
            _isRefreshing.value = false
        }
    }

    private suspend fun loadData() {
        val s = server ?: return
        val filesResult = bookdropClient.getFiles(s.url, s.id)
        val librariesResult = bookdropClient.getLibrariesWithPaths(s.url, s.id)

        filesResult.onSuccess { page ->
            val fileStates = page.content.map { file ->
                BookdropFileState(
                    file = file,
                    editedMetadata = file.originalMetadata ?: BookdropMetadata(),
                )
            }
            val libraries = librariesResult.getOrDefault(emptyList())
            val currentState = _uiState.value as? BookdropUiState.Success
            _uiState.value = BookdropUiState.Success(
                files = fileStates,
                libraries = libraries,
                selectedLibraryId = currentState?.selectedLibraryId,
                selectedPathId = currentState?.selectedPathId,
            )
        }.onFailure { error ->
            _uiState.value = BookdropUiState.Error(error.message ?: "Failed to load book drop")
        }
    }

    private fun updateFiles(transform: (List<BookdropFileState>) -> List<BookdropFileState>) {
        val state = _uiState.value as? BookdropUiState.Success ?: return
        _uiState.value = state.copy(files = transform(state.files))
    }
}
