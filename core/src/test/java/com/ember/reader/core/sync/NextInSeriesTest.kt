package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAppBook
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NextInSeriesTest {

    private fun book(id: String, series: String?, index: Float?, format: BookFormat = BookFormat.EPUB) =
        Book(id = id, title = "Book $id", format = format, series = series, seriesIndex = index)

    private fun serverBook(id: Long, number: Float?) =
        GrimmoryAppBook(id = id, title = "Server $id", seriesNumber = number)

    @Test
    fun `a book near its end is followed, one only half read is not`() {
        val books = listOf(book("a", "Saga", 1f), book("b", "Other", 1f))
        val progress = mapOf("a" to 0.8f, "b" to 0.5f)

        assertEquals(listOf("a"), seriesFrontiers(books, progress::get).map { it.id })
    }

    @Test
    fun `the furthest book under way stands for its series`() {
        val books = listOf(book("one", "Saga", 1f), book("two", "Saga", 2f), book("three", "Saga", 3f))
        // Three is on the device but not yet begun.
        val progress = mapOf("one" to 1f, "two" to 0.85f, "three" to 0f)

        assertEquals(listOf("two"), seriesFrontiers(books, progress::get).map { it.id })
    }

    @Test
    fun `a book outside a series, or with no number in one, is not followed`() {
        val books = listOf(book("a", null, null), book("b", "Saga", null))

        assertTrue(seriesFrontiers(books) { 0.9f }.isEmpty())
    }

    @Test
    fun `the ebooks and audiobooks of a series are followed separately`() {
        val books = listOf(book("e", "Saga", 2f), book("a", "Saga", 1f, BookFormat.AUDIOBOOK))

        assertEquals(setOf("e", "a"), seriesFrontiers(books) { 0.9f }.map { it.id }.toSet())
    }

    @Test
    fun `what follows is in series order, whatever order the server gives`() {
        val series = listOf(serverBook(30, 3f), serverBook(10, 1f), serverBook(25, 2.5f), serverBook(20, 2f))

        assertEquals(listOf(25L, 30L), nextInSeries(after = 2f, series).map { it.id })
    }

    @Test
    fun `nothing follows the last book, and a book with no number is passed over`() {
        val series = listOf(serverBook(10, 1f), serverBook(20, 2f), serverBook(99, null))

        assertTrue(nextInSeries(after = 2f, series).isEmpty())
    }
}
