package com.ember.reader.ui.settings

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.database.dao.DictionaryDao
import com.ember.reader.core.model.Book
import com.ember.reader.core.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StorageSortMode(val displayName: String) {
    LATEST_FIRST("Latest"),
    LARGEST_FIRST("Largest"),
    ALPHABETICAL("A-Z")
}

@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val dictionaryDao: DictionaryDao,
) : ViewModel() {

    private val _sortMode = MutableStateFlow(StorageSortMode.LATEST_FIRST)
    val sortMode: StateFlow<StorageSortMode> = _sortMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val uiState: StateFlow<StorageUiState> = combine(
        bookRepository.observeServerDownloads().map { books ->
            books.map { book ->
                val fileSize = book.localPath?.let { sizeOf(File(it)) } ?: 0L
                DownloadedBookItem(book = book, fileSize = fileSize)
            }
        },
        _sortMode
    ) { items, sort ->
        val sorted = when (sort) {
            StorageSortMode.LATEST_FIRST -> items.sortedByDescending { it.book.downloadedAt }
            StorageSortMode.LARGEST_FIRST -> items.sortedByDescending { it.fileSize }
            StorageSortMode.ALPHABETICAL -> items.sortedBy { it.book.title.lowercase() }
        }
        val stat = StatFs(context.filesDir.path)
        StorageUiState(
            downloadedBooks = sorted,
            totalSize = sorted.sumOf { it.fileSize },
            deviceTotalBytes = stat.totalBytes,
            deviceAvailableBytes = stat.availableBytes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageUiState())

    private val _recoveryResult = MutableStateFlow<String?>(null)
    val recoveryResult: StateFlow<String?> = _recoveryResult.asStateFlow()

    fun updateSortMode(mode: StorageSortMode) {
        _sortMode.value = mode
    }

    fun toggleSelection(bookId: String) {
        _selectedIds.update { ids ->
            if (bookId in ids) ids - bookId else ids + bookId
        }
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.downloadedBooks.map { it.book.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        _selectedIds.value = emptySet()
        viewModelScope.launch {
            for (id in ids) {
                bookRepository.removeDownload(id)
            }
        }
    }

    fun removeDownload(bookId: String) {
        viewModelScope.launch { bookRepository.removeDownload(bookId) }
    }

    fun recoverOrphanedBooks() {
        viewModelScope.launch {
            val count = bookRepository.recoverOrphanedFiles()
            _recoveryResult.value = if (count > 0) "Recovered $count book(s)" else "No orphaned files found"
        }
    }

    fun dismissRecoveryResult() {
        _recoveryResult.value = null
    }

    fun clearDictionaryCache() {
        viewModelScope.launch {
            val before = dictionaryDao.count()
            dictionaryDao.clearAll()
            _recoveryResult.value = "Cleared $before dictionary entries"
        }
    }
}

data class StorageUiState(
    val downloadedBooks: List<DownloadedBookItem> = emptyList(),
    val totalSize: Long = 0L,
    val deviceTotalBytes: Long = 0L,
    val deviceAvailableBytes: Long = 0L
)

data class DownloadedBookItem(
    val book: Book,
    val fileSize: Long
)

/**
 * Returns the size of a file, or the recursive size of all files inside a
 * directory. Folder-based audiobooks store each track as a separate file under
 * an `audiobook_<id>/` directory, so a plain `File.length()` returns 0 for them.
 */
private fun sizeOf(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

fun Long.toReadableSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024 * 1024 * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    else -> "%.2f GB".format(this / (1024.0 * 1024 * 1024))
}
