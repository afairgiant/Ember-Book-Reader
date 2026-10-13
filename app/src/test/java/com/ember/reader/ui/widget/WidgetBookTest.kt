package com.ember.reader.ui.widget

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WidgetBookTest {

    private fun book(id: String, format: BookFormat = BookFormat.EPUB) =
        Book(id = id, title = "Title $id", author = "Author", format = format)

    @Test
    fun `nothing in progress is no book`() {
        assertNull(widgetBookOf(emptyList()) { 0.5f })
    }

    @Test
    fun `the first book is shown with its progress as a whole percent`() {
        val shown = widgetBookOf(listOf(book("a"), book("b"))) { id -> if (id == "a") 0.766f else 0.1f }

        assertEquals("a", shown?.bookId)
        assertEquals(77, shown?.percent)
    }

    @Test
    fun `a book with no stored progress shows zero, and progress stays within 0 to 100`() {
        assertEquals(0, widgetBookOf(listOf(book("a"))) { null }?.percent)
        assertEquals(100, widgetBookOf(listOf(book("a"))) { 1.4f }?.percent)
    }

    @Test
    fun `tapping opens the reader for the book's format`() {
        assertEquals("reader/epub/a", widgetBookOf(listOf(book("a"))) { 0f }?.readerRoute)
        assertEquals("reader/pdf/a", widgetBookOf(listOf(book("a", BookFormat.PDF))) { 0f }?.readerRoute)
        assertEquals(
            "reader/audiobook/a",
            widgetBookOf(listOf(book("a", BookFormat.AUDIOBOOK))) { 0f }?.readerRoute
        )
    }
}
