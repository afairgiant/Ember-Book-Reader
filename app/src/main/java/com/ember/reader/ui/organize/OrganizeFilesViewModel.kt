package com.ember.reader.ui.organize

import com.ember.reader.core.grimmory.FileMoveItem
import com.ember.reader.core.grimmory.FileMoveRequest
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryFullBook
import com.ember.reader.core.grimmory.GrimmoryLibraryFull
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.util.FileNamingPatternResolver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Plain (non-Hilt) state holder for the Organize Files modal sheet. Constructed
 * each time the sheet opens via [Factory]; scoped to the sheet, disposed on close.
 *
 * The VM is a plain class so the sheet's parent composable can create it via
 * `remember { factory.create(...) }` without routing book IDs through SavedStateHandle
 * / nav args — there is no nav destination for a modal sheet.
 *
 * Lifecycle: call [onDispose] from the sheet's dismissal path to cancel in-flight
 * work. Rotation is handled by re-creating the VM and re-running [load] —
 * acceptable because both calls are idempotent and complete in under a second
 * for typical selections.
 */
class OrganizeFilesViewModel(
    private val appClient: GrimmoryAppClient,
    private val serverRepository: ServerRepository,
    private val baseUrl: String,
    private val serverId: Long,
    private val bookIds: List<Long>,
    scope: CoroutineScope = MainScope()
) {

    private val _state = MutableStateFlow<OrganizeFilesUiState>(OrganizeFilesUiState.Loading)
    val state: StateFlow<OrganizeFilesUiState> = _state.asStateFlow()

    private val viewModelScope: CoroutineScope = scope

    private var loadedLibraries: List<GrimmoryLibraryFull> = emptyList()
    private var loadedBooks: List<GrimmoryFullBook> = emptyList()
    private var loadJob: Job? = null
    private var submitJob: Job? = null

    init {
        load()
    }

    fun retryLoad() = load()

    private fun load() {
        loadJob?.cancel()
        _state.value = OrganizeFilesUiState.Loading
        loadJob = viewModelScope.launch {
            val result = runCatching {
                coroutineScope {
                    val libs = async { appClient.getFullLibraries(baseUrl, serverId).getOrThrow() }
                    val books = bookIds.map { id ->
                        async { appClient.getBookDetailFull(baseUrl, serverId, id).getOrThrow() }
                    }
                    libs.await() to books.awaitAll()
                }
            }
            result.onSuccess { (libs, books) ->
                loadedLibraries = libs
                loadedBooks = books
                _state.value = OrganizeFilesUiState.Ready(
                    libraries = libs,
                    selectedLibraryId = null,
                    selectedPathId = null,
                    previews = buildPreviews(libs, books, targetLibraryId = null, targetPathId = null)
                )
            }.onFailure { e ->
                Timber.w(e, "OrganizeFiles load failed")
                _state.value = OrganizeFilesUiState.Error(
                    kind = OrganizeFilesUiState.Error.Kind.Loading,
                    message = "Couldn't load book details."
                )
            }
        }
    }

    fun onLibrarySelected(libraryId: Long) {
        val ready = _state.value as? OrganizeFilesUiState.Ready ?: return
        val lib = loadedLibraries.firstOrNull { it.id == libraryId } ?: return
        val pathId = lib.paths.firstOrNull()?.id
        _state.value = ready.copy(
            selectedLibraryId = libraryId,
            selectedPathId = pathId,
            previews = buildPreviews(loadedLibraries, loadedBooks, libraryId, pathId)
        )
    }

    fun onPathSelected(pathId: Long) {
        val ready = _state.value as? OrganizeFilesUiState.Ready ?: return
        _state.value = ready.copy(
            selectedPathId = pathId,
            previews = buildPreviews(loadedLibraries, loadedBooks, ready.selectedLibraryId, pathId)
        )
    }

    fun onConfirm() {
        val ready = _state.value as? OrganizeFilesUiState.Ready ?: return
        val libId = ready.selectedLibraryId ?: return
        val pathId = ready.selectedPathId ?: return
        val targetLibrary = loadedLibraries.firstOrNull { it.id == libId } ?: return
        if (!ready.anythingToMove) return

        _state.value = ready.copy(submitting = true)
        submitJob = viewModelScope.launch {
            val request = FileMoveRequest(
                bookIds = bookIds.toSet(),
                moves = bookIds.map { FileMoveItem(it, libId, pathId) }
            )
            val result = appClient.moveFiles(baseUrl, serverId, request)
            result.fold(
                onSuccess = {
                    _state.value = OrganizeFilesUiState.Success(
                        movedCount = bookIds.size,
                        targetLibraryName = targetLibrary.name
                    )
                },
                onFailure = { e ->
                    val msg = e.message.orEmpty()
                    // Classify by HTTP status substring left by withAuth's error() calls.
                    // "403" → permission; any 5xx → server; else network/unknown.
                    _state.value = when {
                        "403" in msg -> OrganizeFilesUiState.Error(
                            OrganizeFilesUiState.Error.Kind.Permission,
                            "You don't have permission to organize files on this server. " +
                                "Ask your Grimmory admin to grant Manage Library permission."
                        )
                        Regex("""\b5\d\d\b""").containsMatchIn(msg) -> OrganizeFilesUiState.Error(
                            OrganizeFilesUiState.Error.Kind.Server,
                            "Grimmory couldn't complete the move. Try again in a moment."
                        )
                        else -> OrganizeFilesUiState.Error(
                            OrganizeFilesUiState.Error.Kind.Network,
                            "No connection to Grimmory."
                        )
                    }
                    // If we hit 403, refresh the stored permission flag so the action
                    // hides on the next render without requiring an app restart.
                    if ("403" in msg) {
                        runCatching { serverRepository.refreshGrimmoryPermissions(serverId) }
                            .onFailure { Timber.w(it, "Permission refresh after 403 failed") }
                    }
                }
            )
        }
    }

    /**
     * Cancels any in-flight work and releases the scope. Call this from the sheet's
     * dismissal path (onDismissRequest or a DisposableEffect).
     */
    fun onDispose() {
        viewModelScope.cancel()
    }

    private fun buildPreviews(
        libraries: List<GrimmoryLibraryFull>,
        books: List<GrimmoryFullBook>,
        targetLibraryId: Long?,
        targetPathId: Long?
    ): List<BookMovePreview> {
        val targetLibrary = libraries.firstOrNull { it.id == targetLibraryId }
        val targetPath = targetLibrary?.paths?.firstOrNull { it.id == targetPathId }

        return books.map { book ->
            val fileName = book.primaryFile?.fileName.orEmpty()
            val currentLibraryPath = book.libraryPath?.path.orEmpty()
            val currentSubPath = book.primaryFile?.fileSubPath
            val relativeCurrent = buildRelativePath(currentSubPath, fileName)
            val currentFullPath = joinPath(currentLibraryPath, relativeCurrent)

            val newRelative = if (targetLibrary != null && fileName.isNotEmpty()) {
                buildNewRelativePath(book, fileName, targetLibrary.fileNamingPattern)
            } else {
                relativeCurrent
            }
            val newFullPath = when {
                targetPath != null && newRelative.isNotEmpty() -> joinPath(targetPath.path, newRelative)
                else -> newRelative
            }

            val isNoChange = targetLibrary != null &&
                book.libraryId == targetLibrary.id &&
                book.libraryPath?.id == targetPathId

            BookMovePreview(
                bookId = book.id,
                title = book.title,
                currentPath = currentFullPath.ifBlank { "(no file)" },
                newPath = newFullPath.ifBlank { "(no file)" },
                isNoChange = isNoChange
            )
        }
    }

    private fun buildRelativePath(subPath: String?, fileName: String): String {
        val trimmed = subPath?.trim('/')?.takeIf { it.isNotBlank() }
        return when {
            trimmed != null && fileName.isNotEmpty() -> "$trimmed/$fileName"
            trimmed != null -> trimmed
            else -> fileName
        }
    }

    private fun buildNewRelativePath(
        book: GrimmoryFullBook,
        fileName: String,
        pattern: String?
    ): String {
        if (pattern.isNullOrBlank()) return fileName
        val meta = book.metadata
        val values = mapOf(
            "authors" to FileNamingPatternResolver.sanitize(
                meta?.authors?.joinToString(", ")?.ifEmpty { "Unknown Author" } ?: "Unknown Author"
            ),
            "title" to FileNamingPatternResolver.sanitize(
                (meta?.title ?: book.title).ifEmpty { "Untitled" }
            ),
            "subtitle" to FileNamingPatternResolver.sanitize(meta?.subtitle.orEmpty()),
            "year" to FileNamingPatternResolver.formatYear(meta?.publishedDate),
            "series" to FileNamingPatternResolver.sanitize(meta?.seriesName.orEmpty()),
            "seriesIndex" to FileNamingPatternResolver.formatSeriesIndex(meta?.seriesNumber),
            "language" to FileNamingPatternResolver.sanitize(meta?.language.orEmpty()),
            "publisher" to FileNamingPatternResolver.sanitize(meta?.publisher.orEmpty()),
            "isbn" to FileNamingPatternResolver.sanitize(meta?.isbn13 ?: meta?.isbn10 ?: ""),
            "currentFilename" to FileNamingPatternResolver.sanitize(fileName)
        )
        val resolved = FileNamingPatternResolver.resolve(pattern, values)
        val extension = Regex("""\.[^.]+$""").find(fileName)?.value.orEmpty()
        return if (extension.isNotEmpty() && !resolved.endsWith(extension)) resolved + extension else resolved
    }

    private fun joinPath(base: String, relative: String): String {
        if (base.isBlank()) return relative
        if (relative.isBlank()) return base
        val combined = base.trimEnd('/') + "/" + relative.trimStart('/')
        return combined.replace(Regex("/+"), "/")
    }

    /**
     * Constructs [OrganizeFilesViewModel] with injected dependencies. Held by the
     * parent screen's Hilt ViewModel and used by the sheet composable to remember
     * a fresh VM per sheet invocation.
     */
    class Factory @Inject constructor(
        private val appClient: GrimmoryAppClient,
        private val serverRepository: ServerRepository
    ) {
        fun create(
            baseUrl: String,
            serverId: Long,
            bookIds: List<Long>,
            scope: CoroutineScope = MainScope()
        ): OrganizeFilesViewModel = OrganizeFilesViewModel(
            appClient = appClient,
            serverRepository = serverRepository,
            baseUrl = baseUrl,
            serverId = serverId,
            bookIds = bookIds,
            scope = scope
        )
    }
}
