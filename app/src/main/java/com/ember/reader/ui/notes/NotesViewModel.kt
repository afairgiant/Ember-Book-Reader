package com.ember.reader.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.GrimmoryNotebook
import com.ember.reader.core.repository.GrimmoryNotebookRepository
import com.ember.reader.core.repository.HighlightRepository
import com.ember.reader.ui.book.BookMarkKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

/** Bookmarks, highlights and notes from every book on the device, and from Grimmory when asked. */
@HiltViewModel
class NotesViewModel @Inject constructor(
    bookmarkRepository: BookmarkRepository,
    highlightRepository: HighlightRepository,
    private val bookRepository: BookRepository,
    private val notebookRepository: GrimmoryNotebookRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {

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

    fun includeGrimmory(included: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateNotesIncludeGrimmory(included) }
    }
}
