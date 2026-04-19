package com.ember.reader.ui.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.grimmory.BookdropClient
import com.ember.reader.core.grimmory.GrimmoryAppLibraryWithPaths
import com.ember.reader.core.grimmory.GrimmoryUploadClient
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.navigation.Routes
import com.ember.reader.ui.common.friendlyErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class UploadDestination { Library, BookDrop }

data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
)

data class UploadBookUiState(
    val serverName: String = "",
    val destination: UploadDestination = UploadDestination.Library,
    val libraries: List<GrimmoryAppLibraryWithPaths> = emptyList(),
    val librariesLoading: Boolean = false,
    val selectedLibraryId: Long? = null,
    val selectedPathId: Long? = null,
    val selectedFile: PickedFile? = null,
    /** null = idle. 0..1 = uploading. */
    val uploadProgress: Float? = null,
    val errorMessage: String? = null,
    val uploadedSuccessfully: Boolean = false,
) {
    val isUploading: Boolean get() = uploadProgress != null

    val selectedLibrary: GrimmoryAppLibraryWithPaths?
        get() = libraries.firstOrNull { it.id == selectedLibraryId }

    val canUpload: Boolean
        get() = !isUploading && selectedFile != null && when (destination) {
            UploadDestination.Library -> selectedLibraryId != null && selectedPathId != null
            UploadDestination.BookDrop -> true
        }
}

@HiltViewModel
class UploadBookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val bookdropClient: BookdropClient,
    private val uploadClient: GrimmoryUploadClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val serverId: Long = savedStateHandle.get<Long>(Routes.ARG_SERVER_ID)
        ?: error("UploadBookScreen requires a serverId nav argument")

    private val _state = MutableStateFlow(UploadBookUiState())
    val state: StateFlow<UploadBookUiState> = _state.asStateFlow()

    private var uploadJob: Job? = null

    init {
        viewModelScope.launch {
            val server = serverRepository.getById(serverId)
            if (server == null || !server.isGrimmory) {
                _state.update { it.copy(errorMessage = "Server not found") }
                return@launch
            }
            _state.update { it.copy(serverName = server.name) }
            loadLibraries(server)
        }
    }

    fun setDestination(destination: UploadDestination) {
        if (_state.value.isUploading) return
        _state.update { it.copy(destination = destination) }
    }

    fun selectLibrary(libraryId: Long) {
        if (_state.value.isUploading) return
        _state.update { it.copy(selectedLibraryId = libraryId, selectedPathId = null) }
    }

    fun selectPath(pathId: Long) {
        if (_state.value.isUploading) return
        _state.update { it.copy(selectedPathId = pathId) }
    }

    fun setFileFromUri(uri: Uri) {
        if (_state.value.isUploading) return
        val picked = readFileInfo(uri) ?: run {
            _state.update { it.copy(errorMessage = "Couldn't read the selected file") }
            return
        }
        _state.update { it.copy(selectedFile = picked, errorMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun consumeSuccess() {
        _state.update { it.copy(uploadedSuccessfully = false) }
    }

    fun upload() {
        val snapshot = _state.value
        if (!snapshot.canUpload) return
        val file = snapshot.selectedFile ?: return
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            val server = serverRepository.getById(serverId)
            if (server == null) {
                _state.update { it.copy(errorMessage = "Server not found") }
                return@launch
            }
            _state.update { it.copy(uploadProgress = 0f, errorMessage = null) }

            val result = when (snapshot.destination) {
                UploadDestination.Library -> {
                    val libId = snapshot.selectedLibraryId ?: return@launch
                    val pathId = snapshot.selectedPathId ?: return@launch
                    uploadClient.uploadToLibrary(
                        baseUrl = server.url,
                        serverId = serverId,
                        libraryId = libId,
                        pathId = pathId,
                        fileUri = file.uri,
                        displayName = file.name,
                        mimeType = file.mimeType,
                        onProgress = { sent, total -> updateProgress(sent, total) },
                    )
                }
                UploadDestination.BookDrop -> uploadClient.uploadToBookdrop(
                    baseUrl = server.url,
                    serverId = serverId,
                    fileUri = file.uri,
                    displayName = file.name,
                    mimeType = file.mimeType,
                    onProgress = { sent, total -> updateProgress(sent, total) },
                )
            }

            result
                .onSuccess {
                    Timber.d("Upload: ${file.name} -> ${snapshot.destination} succeeded")
                    _state.update {
                        it.copy(uploadProgress = null, uploadedSuccessfully = true)
                    }
                }
                .onFailure { err ->
                    Timber.w(err, "Upload: ${file.name} -> ${snapshot.destination} failed")
                    _state.update {
                        it.copy(
                            uploadProgress = null,
                            errorMessage = friendlyErrorMessage(err),
                        )
                    }
                }
        }
    }

    private fun updateProgress(sent: Long, total: Long) {
        val progress = if (total > 0L) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f
        _state.update { it.copy(uploadProgress = progress) }
    }

    private suspend fun loadLibraries(server: Server) {
        _state.update { it.copy(librariesLoading = true) }
        bookdropClient.getLibrariesWithPaths(server.url, server.id)
            .onSuccess { libs ->
                _state.update { it.copy(libraries = libs, librariesLoading = false) }
            }
            .onFailure { err ->
                _state.update {
                    it.copy(
                        librariesLoading = false,
                        errorMessage = friendlyErrorMessage(err),
                    )
                }
            }
    }

    private fun readFileInfo(uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val cursor = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        ) ?: return null
        return cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIdx >= 0 && !c.isNull(nameIdx)) c.getString(nameIdx) else "book"
            val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else -1L
            PickedFile(uri = uri, name = name, size = size, mimeType = mimeType)
        }
    }
}
