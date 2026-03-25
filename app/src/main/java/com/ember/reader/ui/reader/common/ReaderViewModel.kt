package com.ember.reader.ui.reader.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.readium.LocatorSerializer
import com.ember.reader.core.readium.toJsonString
import com.ember.reader.core.readium.toLocator
import com.ember.reader.core.readium.toPercentage
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.ReaderPreferencesRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookOpener: BookOpener,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readerPreferencesRepository: ReaderPreferencesRepository,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    val preferences: StateFlow<ReaderPreferences> =
        readerPreferencesRepository.preferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderPreferences())

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    private var publication: Publication? = null
    private var book: Book? = null
    private var progressSaveJob: Job? = null

    init {
        viewModelScope.launch {
            loadBook()
        }
        viewModelScope.launch {
            bookmarkRepository.observeByBookId(bookId).collect { _bookmarks.value = it }
        }
    }

    private suspend fun loadBook() {
        val loadedBook = bookRepository.getById(bookId)
        if (loadedBook == null) {
            _uiState.value = ReaderUiState.Error("Book not found")
            return
        }
        book = loadedBook

        val localPath = loadedBook.localPath
        if (localPath == null) {
            _uiState.value = ReaderUiState.Error("Book not downloaded")
            return
        }

        val file = File(localPath)
        if (!file.exists()) {
            _uiState.value = ReaderUiState.Error("Book file missing")
            return
        }

        val pub = bookOpener.open(file).getOrElse { error ->
            _uiState.value = ReaderUiState.Error("Failed to open: ${error.message}")
            return
        }
        publication = pub

        val progress = readingProgressRepository.getByBookId(bookId)
        val initialLocator = progress?.locatorJson?.toLocator()

        _uiState.value = ReaderUiState.Ready(
            publication = pub,
            initialLocator = initialLocator,
            book = loadedBook,
        )
    }

    fun onLocatorChanged(locator: Locator) {
        _currentLocator.value = locator
        scheduleSaveProgress(locator)
    }

    fun toggleChrome() {
        _chromeVisible.update { !it }
    }

    fun addBookmark() {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            bookmarkRepository.addBookmark(
                bookId = bookId,
                locatorJson = locator.toJsonString(),
                title = locator.title,
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id)
        }
    }

    fun updatePreferences(preferences: ReaderPreferences) {
        viewModelScope.launch {
            readerPreferencesRepository.updatePreferences(preferences)
        }
    }

    private fun scheduleSaveProgress(locator: Locator) {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveProgress(locator)
        }
    }

    private suspend fun saveProgress(locator: Locator) {
        readingProgressRepository.updateProgress(
            bookId = bookId,
            serverId = book?.serverId,
            percentage = locator.toPercentage(),
            locatorJson = locator.toJsonString(),
        )
    }

    override fun onCleared() {
        super.onCleared()
        _currentLocator.value?.let { locator ->
            viewModelScope.launch {
                saveProgress(locator)
            }
        }
        publication?.close()
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 5000L
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val publication: Publication,
        val initialLocator: Locator?,
        val book: Book,
    ) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}
