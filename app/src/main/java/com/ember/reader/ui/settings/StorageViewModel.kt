package com.ember.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val bookRepository: BookRepository,
) : ViewModel() {

    val uiState: StateFlow<StorageUiState> = bookRepository.observeDownloadedBooks()
        .map { books ->
            val items = books.map { book ->
                val fileSize = book.localPath?.let { File(it).length() } ?: 0L
                DownloadedBookItem(book = book, fileSize = fileSize)
            }
            StorageUiState(
                downloadedBooks = items,
                totalSize = items.sumOf { it.fileSize },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageUiState())

    private val _recoveryResult = MutableStateFlow<String?>(null)
    val recoveryResult: StateFlow<String?> = _recoveryResult.asStateFlow()

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
}

data class StorageUiState(
    val downloadedBooks: List<DownloadedBookItem> = emptyList(),
    val totalSize: Long = 0L,
)

data class DownloadedBookItem(
    val book: Book,
    val fileSize: Long,
)

fun Long.toReadableSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024 * 1024 * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    else -> "%.2f GB".format(this / (1024.0 * 1024 * 1024))
}
