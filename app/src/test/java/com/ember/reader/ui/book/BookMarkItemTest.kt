package com.ember.reader.ui.book

import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BookMarkItemTest {

    private fun at(second: Long): Instant = Instant.ofEpochSecond(second)

    private fun bookmark(id: Long, title: String?, second: Long) =
        Bookmark(id = id, bookId = "b", locatorJson = "{}", title = title, createdAt = at(second))

    private fun highlight(id: Long, text: String?, note: String?, second: Long) = Highlight(
        id = id,
        bookId = "b",
        locatorJson = "{}",
        selectedText = text,
        annotation = note,
        createdAt = at(second)
    )

    @Test
    fun `bookmarks and highlights are merged newest first`() {
        val items = bookMarkItemsOf(
            bookmarks = listOf(bookmark(1, "Chapter 2", second = 20)),
            highlights = listOf(highlight(1, "old", null, second = 10), highlight(2, "new", null, second = 30))
        )

        assertEquals(listOf("new", "Chapter 2", "old"), items.map { it.text })
    }

    @Test
    fun `a highlight with a note is a note and one without is a highlight`() {
        val items = bookMarkItemsOf(
            bookmarks = emptyList(),
            highlights = listOf(highlight(1, "a", "my note", second = 2), highlight(2, "b", "  ", second = 1))
        )

        assertEquals(listOf(BookMarkKind.NOTE, BookMarkKind.HIGHLIGHT), items.map { it.kind })
        assertEquals("my note", items[0].note)
        assertNull(items[1].note)
    }

    @Test
    fun `a bookmark and a highlight with the same id get different keys`() {
        val items = bookMarkItemsOf(
            bookmarks = listOf(bookmark(7, null, second = 1)),
            highlights = listOf(highlight(7, "text", null, second = 2))
        )

        assertEquals(2, items.map { it.key }.distinct().size)
    }

    @Test
    fun `a blank title or selection becomes null`() {
        val items = bookMarkItemsOf(
            bookmarks = listOf(bookmark(1, " ", second = 2)),
            highlights = listOf(highlight(1, "", null, second = 1))
        )

        assertEquals(listOf(null, null), items.map { it.text })
    }
}
