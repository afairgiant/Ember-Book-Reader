package com.ember.reader.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.GrimmoryNotebook
import com.ember.reader.core.repository.GrimmoryNotebookRepository
import com.ember.reader.core.repository.HighlightRepository
import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.BookMarkKind
import com.ember.reader.ui.book.RemoteMark
import com.ember.reader.ui.reader.common.ReaderTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NotesUiState {
    data object Loading : NotesUiState

    /**
     * @param groups what the filter and search leave, book by book.
     * @param hasAny whether there is anything at all before filtering, to tell "nothing
     * yet" from "nothing matches".
     * @param grimmory how including Grimmory stands; null when no server could be asked,
     * and then the choice is not offered.
     */
    data class Ready(
        val groups: List<NotesBookGroup>,
        val hasAny: Boolean,
        val grimmory: GrimmoryInclusion? = null
    ) : NotesUiState
}

/**
 * @param included whether the user has asked for Grimmory's entries.
 * @param loading true while they are being read.
 * @param failedServers the servers that could not be read the last time.
 */
data class GrimmoryInclusion(
    val included: Boolean,
    val loading: Boolean = false,
    val failedServers: List<String> = emptyList()
)

/** A tapped entry on its way to the reader, while that takes asking, waiting or telling. */
sealed interface NotesOpening {
    /** Its book is not on the device; the user is asked whether to download it. */
    data class ConfirmDownload(val book: Book, val mark: BookMarkItem) : NotesOpening

    data class Downloading(val book: Book) : NotesOpening

    /** The server is being asked where in the book the entry is. */
    data object Locating : NotesOpening

    data object DownloadFailed : NotesOpening

    /**
     * The reader can open.
     *
     * @param target where; null opens the book where it was left.
     * @param placeMissing true when the entry has a place and it could not be found.
     */
    data class Ready(
        val bookId: String,
        val format: BookFormat,
        val target: ReaderTarget?,
        val placeMissing: Boolean
    ) : NotesOpening
}

/** Bookmarks, highlights and notes from every book on the device, and from Grimmory when asked. */
@HiltViewModel
class NotesViewModel @Inject constructor(
    bookmarkRepository: BookmarkRepository,
    highlightRepository: HighlightRepository,
    private val bookRepository: BookRepository,
    private val notebookRepository: GrimmoryNotebookRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val entryOpener: NotesEntryOpener
) : ViewModel() {

    private val _opening = MutableStateFlow<NotesOpening?>(null)

    /** The entry being opened, when opening it has something to show; null otherwise. */
    val opening: StateFlow<NotesOpening?> = _opening.asStateFlow()

    private var openingJob: Job? = null

    private val _kind = MutableStateFlow<BookMarkKind?>(null)

    /** The kind of mark being shown; null shows all of them. */
    val kind: StateFlow<BookMarkKind?> = _kind.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val notebooks = MutableStateFlow<List<GrimmoryNotebook>>(emptyList())
    private val grimmory = MutableStateFlow<GrimmoryInclusion?>(null)

    private val allGroups = combine(
        bookmarkRepository.observeAll(),
        highlightRepository.observeAll(),
        notebooks
    ) { bookmarks, highlights, notebooks ->
        val bookIds = (bookmarks.map { it.bookId } + highlights.map { it.bookId } + notebooks.map { it.localBookId }).toSet()
        notesGroupsOf(bookRepository.getByIds(bookIds), bookmarks, highlights, notebooks)
    }

    val uiState: StateFlow<NotesUiState> = combine(allGroups, _kind, _query, grimmory) { groups, kind, query, grimmory ->
        NotesUiState.Ready(groups = groups.narrowedTo(kind, query), hasAny = groups.isNotEmpty(), grimmory = grimmory)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesUiState.Loading)

    init {
        viewModelScope.launch {
            if (!notebookRepository.isAvailable()) return@launch
            appPreferencesRepository.notesIncludeGrimmoryFlow.collectLatest { included ->
                if (!included) {
                    notebooks.value = emptyList()
                    grimmory.value = GrimmoryInclusion(included = false)
                    return@collectLatest
                }
                grimmory.value = GrimmoryInclusion(included = true, loading = true)
                val fetched = notebookRepository.fetch()
                notebooks.value = fetched.notebooks
                grimmory.value = GrimmoryInclusion(included = true, failedServers = fetched.failedServers)
            }
        }
    }

    fun showKind(kind: BookMarkKind?) {
        _kind.value = kind
    }

    fun search(query: String) {
        _query.value = query
    }

    /** Opens [mark] in the reader, asking first when [book] has to be downloaded for it. */
    fun open(book: Book, mark: BookMarkItem) {
        if (openingJob?.isActive == true) return
        openingJob = viewModelScope.launch {
            // The listed book can be out of date about having been downloaded since.
            val current = bookRepository.getById(book.id) ?: return@launch
            if (current.isDownloaded) {
                openDownloaded(current, mark)
            } else if (current.serverId != null) {
                _opening.value = NotesOpening.ConfirmDownload(current, mark)
            }
        }
    }

    /** The user agreed to the download asked about. */
    fun confirmDownload() {
        val asked = _opening.value as? NotesOpening.ConfirmDownload ?: return
        openingJob = viewModelScope.launch {
            _opening.value = NotesOpening.Downloading(asked.book)
            val downloaded = entryOpener.download(asked.book)
            if (downloaded == null) {
                _opening.value = NotesOpening.DownloadFailed
            } else {
                openDownloaded(downloaded, asked.mark)
            }
        }
    }

    /**
     * Drops what is being opened: the answer was no, the wait was given up, or the screen
     * has acted on it. A download already started carries on by itself.
     */
    fun closeOpening() {
        openingJob?.cancel()
        _opening.value = null
    }

    private suspend fun openDownloaded(book: Book, mark: BookMarkItem) {
        if (mark.remote != null) _opening.value = NotesOpening.Locating
        val target = entryOpener.targetFor(book, mark)
        _opening.value = NotesOpening.Ready(
            bookId = book.id,
            format = book.format,
            target = target,
            placeMissing = target == null && mark.remote?.kind != RemoteMark.Kind.BOOK_NOTE
        )
    }

    fun includeGrimmory(included: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateNotesIncludeGrimmory(included) }
    }
}
