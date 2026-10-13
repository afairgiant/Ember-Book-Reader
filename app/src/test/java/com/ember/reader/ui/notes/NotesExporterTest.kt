package com.ember.reader.ui.notes

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.BookMarkKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotesExporterTest {

    private val exporter = NotesExporter(zone = ZoneOffset.UTC, locale = Locale.UK)
    private val today = LocalDate.of(2026, 10, 13)
    private val made = Instant.parse("2026-10-11T09:00:00Z")

    private val dune = NotesBookGroup(
        book = Book(id = "dune", title = "Dune", author = "Frank Herbert", format = BookFormat.EPUB),
        marks = listOf(
            BookMarkItem("h1", BookMarkKind.NOTE, "Fear is the\nmind-killer", "the litany", made, "Book One"),
            BookMarkItem("h2", BookMarkKind.HIGHLIGHT, "The spice must flow", null, made),
            BookMarkItem("b1", BookMarkKind.BOOKMARK, null, null, made, "Arrakis")
        )
    )
    private val emma = NotesBookGroup(
        book = Book(id = "emma", title = "Emma", format = BookFormat.EPUB),
        marks = listOf(BookMarkItem("b2", BookMarkKind.BOOKMARK, "Start", null, made))
    )

    @Test
    fun `markdown has a heading per book, quotes for highlights and labelled notes and bookmarks`() {
        val expected = """
            # Notes & highlights

            Exported from Ember on 13 Oct 2026.

            ## Dune

            *Frank Herbert*

            > Fear is the
            > mind-killer

            **Note:** the litany

            *Book One · 11 Oct 2026*

            > The spice must flow

            *11 Oct 2026*

            **Bookmark:** Untitled

            *Arrakis · 11 Oct 2026*

            ## Emma

            **Bookmark:** Start

            *11 Oct 2026*

        """.trimIndent()

        assertEquals(expected, exporter.export(listOf(dune, emma), NotesExportFormat.MARKDOWN, today).unixLines())
    }

    @Test
    fun `plain text carries the same entries without any markup`() {
        val expected = """
            NOTES & HIGHLIGHTS
            Exported from Ember on 13 Oct 2026.


            Emma
            ----

            Bookmark: Start
            11 Oct 2026


            Dune
            Frank Herbert
            ----

            "Fear is the
            mind-killer"
            Note: the litany
            Book One · 11 Oct 2026

            "The spice must flow"
            11 Oct 2026

            Bookmark: Untitled
            Arrakis · 11 Oct 2026

        """.trimIndent()

        assertEquals(expected, exporter.export(listOf(emma, dune), NotesExportFormat.PLAIN_TEXT, today).unixLines())
    }

    @Test
    fun `the file is named for the one book it holds, or just the day for several`() {
        val awkward = dune.copy(book = dune.book.copy(title = "Dune: Part 1/2 — \"Arrakis\"?"))

        assertEquals("ember-notes-Dune-Part-1-2-Arrakis-2026-10-13.md", exporter.fileName(listOf(awkward), NotesExportFormat.MARKDOWN, today))
        assertEquals("ember-notes-2026-10-13.txt", exporter.fileName(listOf(dune, emma), NotesExportFormat.PLAIN_TEXT, today))
    }

    private fun String.unixLines(): String = replace("\r\n", "\n")
}
