package com.ember.reader.ui.book

import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.sync.CfiLocatorConverter
import com.ember.reader.ui.reader.common.ReaderTarget
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
    val chapter: String? = null,
    /** Where the reader opens for it; null for one this device does not hold. */
    val target: ReaderTarget? = null,
    /** Set for one that Grimmory holds and this device does not. */
    val remote: RemoteMark? = null
) {
    val remoteOnly: Boolean get() = remote != null
}

/** Something Grimmory holds, by the id the server knows it by; ids are per [kind]. */
data class RemoteMark(val kind: Kind, val id: Long) {
    enum class Kind { BOOKMARK, ANNOTATION, BOOK_NOTE }
}

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
            chapter = CfiLocatorConverter.chapterTitle(bookmark.locatorJson),
            target = ReaderTarget.Bookmark(bookmark.id)
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
            chapter = CfiLocatorConverter.chapterTitle(highlight.locatorJson),
            target = ReaderTarget.Highlight(highlight.id)
        )
    }
    return (fromBookmarks + fromHighlights).sortedByDescending { it.createdAt }
}
