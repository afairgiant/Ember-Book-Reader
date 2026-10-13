package com.ember.reader.ui.notes

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.BookMarkKind
import com.ember.reader.ui.book.bookMarkItemsOf

/** One book's bookmarks, highlights and notes as the notes screen lists them, newest first. */
data class NotesBookGroup(
    val book: Book,
    val marks: List<BookMarkItem>
)

/**
 * Every book that has a bookmark or highlight, with them; the book marked most recently
 * comes first. A mark whose book is no longer in the library is left out.
 */
fun notesGroupsOf(books: List<Book>, bookmarks: List<Bookmark>, highlights: List<Highlight>): List<NotesBookGroup> {
    val bookmarksByBook = bookmarks.groupBy { it.bookId }
    val highlightsByBook = highlights.groupBy { it.bookId }
    return books
        .map { book ->
            NotesBookGroup(
                book = book,
                marks = bookMarkItemsOf(
                    bookmarksByBook[book.id].orEmpty(),
                    highlightsByBook[book.id].orEmpty()
                )
            )
        }
        .filter { it.marks.isNotEmpty() }
        .sortedByDescending { it.marks.first().createdAt }
}

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
