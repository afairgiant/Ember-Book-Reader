package com.ember.reader.ui.notes

import com.ember.reader.core.grimmory.GrimmoryNotebookEntry
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.repository.GrimmoryNotebook
import com.ember.reader.core.repository.parseServerTimestampOrNull
import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.BookMarkKind
import com.ember.reader.ui.book.bookMarkItemsOf
import java.time.Instant

/** One book's bookmarks, highlights and notes as the notes screen lists them, newest first. */
data class NotesBookGroup(
    val book: Book,
    val marks: List<BookMarkItem>
)

/**
 * Every book that has a bookmark, highlight or note, with them; the book marked most
 * recently comes first. A mark whose book is no longer in the library is left out.
 *
 * @param notebooks what Grimmory holds, when it is being included. An entry the device
 * already has (it carries the server's id for it) is listed once, as the device's own.
 */
fun notesGroupsOf(
    books: List<Book>,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    notebooks: List<GrimmoryNotebook> = emptyList()
): List<NotesBookGroup> {
    val bookmarksByBook = bookmarks.groupBy { it.bookId }
    val highlightsByBook = highlights.groupBy { it.bookId }
    val entriesByBook = notebooks.groupBy({ it.localBookId }, { it.entries }).mapValues { it.value.flatten() }
    return books
        .map { book ->
            val ownBookmarks = bookmarksByBook[book.id].orEmpty()
            val ownHighlights = highlightsByBook[book.id].orEmpty()
            val heldBookmarks = ownBookmarks.mapNotNullTo(mutableSetOf()) { it.remoteId }
            val heldHighlights = ownHighlights.mapNotNullTo(mutableSetOf()) { it.remoteId }
            val fromServer = entriesByBook[book.id].orEmpty().mapNotNull { entry ->
                when (entry.type) {
                    ENTRY_BOOKMARK -> entry.takeIf { it.id !in heldBookmarks }?.toMark(BookMarkKind.BOOKMARK)
                    ENTRY_HIGHLIGHT -> entry.takeIf { it.id !in heldHighlights }?.toMark(
                        if (entry.note.isNullOrBlank()) BookMarkKind.HIGHLIGHT else BookMarkKind.NOTE
                    )
                    // Grimmory's free-standing book notes; Ember keeps no copy of these.
                    ENTRY_NOTE -> entry.toMark(BookMarkKind.NOTE)
                    else -> null
                }
            }
            NotesBookGroup(
                book = book,
                marks = (bookMarkItemsOf(ownBookmarks, ownHighlights) + fromServer).sortedByDescending { it.createdAt }
            )
        }
        .filter { it.marks.isNotEmpty() }
        .sortedByDescending { it.marks.first().createdAt }
}

private fun GrimmoryNotebookEntry.toMark(kind: BookMarkKind) = BookMarkItem(
    key = "grimmory-$type-$id",
    kind = kind,
    text = text?.takeIf { it.isNotBlank() },
    note = note?.takeIf { it.isNotBlank() },
    createdAt = parseServerTimestampOrNull(createdAt) ?: Instant.EPOCH,
    chapter = chapterTitle?.takeIf { it.isNotBlank() },
    remoteOnly = true
)

private const val ENTRY_BOOKMARK = "BOOKMARK"
private const val ENTRY_HIGHLIGHT = "HIGHLIGHT"
private const val ENTRY_NOTE = "NOTE"

/**
 * These groups narrowed to one [kind] (null for all) and to [query]. A query that matches a
 * book's title or author keeps all of that book's marks; otherwise it has to match the
 * mark's own text, note or chapter. Books left with nothing are dropped.
 */
fun List<NotesBookGroup>.narrowedTo(kind: BookMarkKind?, query: String): List<NotesBookGroup> {
    val words = query.trim()
    return mapNotNull { group ->
        val bookMatches = words.isEmpty() ||
            group.book.title.contains(words, ignoreCase = true) ||
            group.book.author?.contains(words, ignoreCase = true) == true
        val marks = group.marks.filter { mark ->
            (kind == null || mark.kind == kind) && (bookMatches || mark.mentions(words))
        }
        if (marks.isEmpty()) null else group.copy(marks = marks)
    }
}

private fun BookMarkItem.mentions(words: String): Boolean =
    listOfNotNull(text, note, chapter).any { it.contains(words, ignoreCase = true) }
