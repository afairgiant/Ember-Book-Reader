package com.ember.reader.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.BookmarkRepository
import com.ember.reader.core.repository.HighlightRepository
import com.ember.reader.ui.book.BookMarkKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface NotesUiState {
    data object Loading : NotesUiState

    /**
     * @param groups what the filter and search leave, book by book.
     * @param hasAny whether there is anything at all before filtering, to tell "nothing
     * yet" from "nothing matches".
     */
    data class Ready(val groups: List<NotesBookGroup>, val hasAny: Boolean) : NotesUiState
}

/** Bookmarks, highlights and notes from every book on the device. */
@HiltViewModel
class NotesViewModel @Inject constructor(
    bookmarkRepository: BookmarkRepository,
    highlightRepository: HighlightRepository,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _kind = MutableStateFlow<BookMarkKind?>(null)

    /** The kind of mark being shown; null shows all of them. */
    val kind: StateFlow<BookMarkKind?> = _kind.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val allGroups = combine(
        bookmarkRepository.observeAll(),
        highlightRepository.observeAll()
    ) { bookmarks, highlights ->
        val bookIds = (bookmarks.map { it.bookId } + highlights.map { it.bookId }).toSet()
        notesGroupsOf(bookRepository.getByIds(bookIds), bookmarks, highlights)
    }

    val uiState: StateFlow<NotesUiState> = combine(allGroups, _kind, _query) { groups, kind, query ->
        NotesUiState.Ready(groups = groups.narrowedTo(kind, query), hasAny = groups.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesUiState.Loading)

    fun showKind(kind: BookMarkKind?) {
        _kind.value = kind
    }

    fun search(query: String) {
        _query.value = query
    }
}
