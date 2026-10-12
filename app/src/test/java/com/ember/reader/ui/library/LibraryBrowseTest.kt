package com.ember.reader.ui.library

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryBrowseTest {

    private fun book(
        title: String,
        author: String? = null,
        series: String? = null,
        seriesIndex: Float? = null
    ) = Book(
        id = title,
        title = title,
        author = author,
        series = series,
        seriesIndex = seriesIndex,
        format = BookFormat.EPUB
    )

    @Test
    fun `series tab lists each series once, by name, and leaves out books with none`() {
        val groups = libraryGroups(
            listOf(
                book("Words of Radiance", series = "Stormlight", seriesIndex = 2f),
                book("Dune", series = "dune", seriesIndex = 1f),
                book("The Way of Kings", series = "Stormlight", seriesIndex = 1f),
                book("Standalone"),
                book("Blank series", series = " ")
            ),
            LibraryTab.SERIES
        )

        assertEquals(listOf("dune", "Stormlight"), groups.map { it.name })
        assertEquals(listOf(1, 2), groups.map { it.count })
    }

    @Test
    fun `a series is represented by its first book`() {
        val groups = libraryGroups(
            listOf(
                book("Words of Radiance", series = "Stormlight", seriesIndex = 2f),
                book("Unnumbered", series = "Stormlight"),
                book("The Way of Kings", series = "Stormlight", seriesIndex = 1f)
            ),
            LibraryTab.SERIES
        )

        assertEquals("The Way of Kings", groups.single().cover.title)
    }

    @Test
    fun `authors tab keeps books with no author under one name`() {
        val groups = libraryGroups(
            listOf(
                book("A", author = "Ursula K. Le Guin"),
                book("B"),
                book("C", author = ""),
                book("D", author = "Ursula K. Le Guin")
            ),
            LibraryTab.AUTHORS
        )

        assertEquals(listOf(UNKNOWN_AUTHOR, "Ursula K. Le Guin"), groups.map { it.name })
        assertEquals(listOf(2, 2), groups.map { it.count })
    }

    @Test
    fun `books tab has no groups`() {
        assertEquals(emptyList<LibraryGroup>(), libraryGroups(listOf(book("A", author = "X")), LibraryTab.BOOKS))
        assertNull(book("A", author = "X", series = "S").groupName(LibraryTab.BOOKS))
    }

    @Test
    fun `series order is by position then title, unnumbered books last`() {
        val ordered = listOf(
            book("Zeta"),
            book("Third", seriesIndex = 3f),
            book("Alpha"),
            book("First", seriesIndex = 1f),
            book("Novella", seriesIndex = 1.5f)
        ).inSeriesOrder()

        assertEquals(listOf("First", "Novella", "Third", "Alpha", "Zeta"), ordered.map { it.title })
    }
}
