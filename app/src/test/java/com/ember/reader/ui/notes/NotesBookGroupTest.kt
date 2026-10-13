package com.ember.reader.ui.notes

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.ui.book.BookMarkKind
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotesBookGroupTest {

    private fun book(id: String, title: String, author: String? = null) =
        Book(id = id, title = title, author = author, format = BookFormat.EPUB)

    private fun at(second: Long) = Instant.ofEpochSecond(second)

    private val dune = book("dune", "Dune", "Frank Herbert")
    private val emma = book("emma", "Emma", "Jane Austen")

    private val bookmarks = listOf(
        Bookmark(
            id = 1,
            bookId = "dune",
            locatorJson = """{"href":"c1.xhtml","title":"Arrakis"}""",
            title = "Start",
            createdAt = at(10)
        )
    )
    private val highlights = listOf(
        Highlight(id = 1, bookId = "dune", locatorJson = "{}", selectedText = "Fear is the mind-killer", createdAt = at(20)),
        Highlight(
            id = 2,
            bookId = "emma",
            locatorJson = "{}",
            selectedText = "handsome, clever, and rich",
            annotation = "opening line",
            createdAt = at(30)
        ),
        Highlight(id = 3, bookId = "gone", locatorJson = "{}", selectedText = "from a deleted book", createdAt = at(40))
    )

    private val groups = notesGroupsOf(listOf(dune, emma), bookmarks, highlights)

    @Test
    fun `books come most recently marked first, each with its marks newest first`() {
        assertEquals(listOf("emma", "dune"), groups.map { it.book.id })
        assertEquals(listOf("Fear is the mind-killer", "Start"), groups[1].marks.map { it.text })
    }

    @Test
    fun `a mark whose book is gone is left out, and so is a book with no marks`() {
        val withExtra = notesGroupsOf(listOf(dune, emma, book("empty", "Empty")), bookmarks, highlights)

        assertEquals(listOf("emma", "dune"), withExtra.map { it.book.id })
        assertTrue(withExtra.flatMap { it.marks }.none { it.text == "from a deleted book" })
    }

    @Test
    fun `a mark carries the chapter its position names`() {
        assertEquals("Arrakis", groups[1].marks.single { it.kind == BookMarkKind.BOOKMARK }.chapter)
    }

    @Test
    fun `narrowing to a kind keeps only that kind and drops books left empty`() {
        val notes = groups.narrowedTo(BookMarkKind.NOTE, "")

        assertEquals(listOf("emma"), notes.map { it.book.id })
        assertEquals(listOf("opening line"), notes.single().marks.map { it.note })
    }

    @Test
    fun `a search matches a mark's text, note or chapter, whatever the case`() {
        assertEquals(listOf("Fear is the mind-killer"), groups.narrowedTo(null, "MIND").single().marks.map { it.text })
        assertEquals(listOf("emma"), groups.narrowedTo(null, "opening").map { it.book.id })
        assertEquals(listOf("Start"), groups.narrowedTo(null, "arrakis").single().marks.map { it.text })
    }

    @Test
    fun `a search that matches the book keeps all of its marks`() {
        assertEquals(2, groups.narrowedTo(null, "herbert").single().marks.size)
        assertEquals(2, groups.narrowedTo(null, " dune ").single().marks.size)
    }

    @Test
    fun `a search with no match leaves nothing`() {
        assertTrue(groups.narrowedTo(null, "zebra").isEmpty())
    }
}
