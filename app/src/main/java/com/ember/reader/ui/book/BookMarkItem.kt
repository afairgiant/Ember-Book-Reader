package com.ember.reader.ui.book

import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.sync.CfiLocatorConverter
import java.time.Instant

enum class BookMarkKind(val label: String) {
    BOOKMARK("Bookmarks"),
    HIGHLIGHT("Highlights"),
    NOTE("Notes"),
}

/** A bookmark, highlight or note as the book page lists it. */
data class BookMarkItem(
    val key: String,
    val kind: BookMarkKind,
    /** The bookmark's title or the highlighted text; null when there is neither. */
    val text: String?,
    val note: String?,
    val createdAt: Instant,
    /** The chapter it is in, when its position says. */
    val chapter: String? = null
)

/**
 * A book's bookmarks and highlights as one list, newest first. A highlight that has a note
 * counts as a note.
 */
fun bookMarkItemsOf(bookmarks: List<Bookmark>, highlights: List<Highlight>): List<BookMarkItem> {
    val fromBookmarks = bookmarks.map { bookmark ->
        BookMarkItem(
            key = "bookmark-${bookmark.id}",
            kind = BookMarkKind.BOOKMARK,
            text = bookmark.title?.takeIf { it.isNotBlank() },
            note = null,
            createdAt = bookmark.createdAt,
            chapter = CfiLocatorConverter.chapterTitle(bookmark.locatorJson)
        )
    }
    val fromHighlights = highlights.map { highlight ->
        val note = highlight.annotation?.takeIf { it.isNotBlank() }
        BookMarkItem(
            key = "highlight-${highlight.id}",
            kind = if (note != null) BookMarkKind.NOTE else BookMarkKind.HIGHLIGHT,
            text = highlight.selectedText?.takeIf { it.isNotBlank() },
            note = note,
            createdAt = highlight.createdAt,
            chapter = CfiLocatorConverter.chapterTitle(highlight.locatorJson)
        )
    }
    return (fromBookmarks + fromHighlights).sortedByDescending { it.createdAt }
}
