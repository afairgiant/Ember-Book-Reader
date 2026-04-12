package com.ember.reader.ui.organize

import com.ember.reader.core.grimmory.GrimmoryLibraryFull
import com.ember.reader.core.grimmory.GrimmoryLibraryPath

/**
 * One row in the Organize Files preview — shown for every selected book.
 *
 * @property currentPath absolute path of the book's primary file before the move,
 *   as best we can reconstruct it from the `/api/v1/books/{id}` response
 * @property newPath absolute path after the move, computed by running the target
 *   library's `fileNamingPattern` against the book's metadata
 * @property isNoChange true when the selected target library and path are the same
 *   as the book's current location; used to grey the row out and disable confirm
 *   when every selected book is a no-op
 */
data class BookMovePreview(
    val bookId: Long,
    val title: String,
    val currentPath: String,
    val newPath: String,
    val isNoChange: Boolean,
)

sealed interface OrganizeFilesUiState {
    data object Loading : OrganizeFilesUiState

    data class Ready(
        val libraries: List<GrimmoryLibraryFull>,
        val selectedLibraryId: Long?,
        val selectedPathId: Long?,
        val previews: List<BookMovePreview>,
        val submitting: Boolean = false,
    ) : OrganizeFilesUiState {
        val selectedLibrary: GrimmoryLibraryFull?
            get() = libraries.firstOrNull { it.id == selectedLibraryId }

        val selectedPath: GrimmoryLibraryPath?
            get() = selectedLibrary?.paths?.firstOrNull { it.id == selectedPathId }

        val showPathPicker: Boolean
            get() = (selectedLibrary?.paths?.size ?: 0) > 1

        val anythingToMove: Boolean
            get() = previews.any { !it.isNoChange }
    }

    data class Error(
        val kind: Kind,
        val message: String,
    ) : OrganizeFilesUiState {
        enum class Kind { Loading, Permission, Server, Network }
    }

    data class Success(
        val movedCount: Int,
        val targetLibraryName: String,
    ) : OrganizeFilesUiState
}
