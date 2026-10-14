package com.ember.reader.ui.reader.audiobook

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UpNextTest {

    private fun audiobook(id: String, series: String? = null, index: Float? = null, format: BookFormat = BookFormat.AUDIOBOOK) =
        Book(id = id, title = "Book $id", format = format, series = series, seriesIndex = index, serverId = 1)

    private val current = audiobook("two", "Saga", 2f)

    @Test
    fun `a queued audiobook plays next, ahead of the series`() {
        val next = nextAudiobook(current, listOf(audiobook("q")), listOf(audiobook("three", "Saga", 3f))) { 0f }

        assertEquals("q" to true, next?.let { it.book.id to it.fromQueue })
    }

    @Test
    fun `the book playing, and anything that is not an audiobook, is passed over in the queue`() {
        val queue = listOf(current, audiobook("e", format = BookFormat.EPUB), audiobook("q"))

        assertEquals("q", nextAudiobook(current, queue, emptyList()) { 0f }?.book?.id)
    }

    @Test
    fun `with nothing queued the next unfinished audiobook of the series follows`() {
        val mates = listOf(
            audiobook("one", "Saga", 1f),
            audiobook("three", "Saga", 3f),
            audiobook("four", "Saga", 4f),
            audiobook("ebook", "Saga", 2.5f, BookFormat.EPUB),
            audiobook("other", "Elsewhere", 3f)
        )
        // Three has been heard already.
        val progress = mapOf("three" to 0.97f)

        val next = nextAudiobook(current, emptyList(), mates, progress::get)

        assertEquals("four" to false, next?.let { it.book.id to it.fromQueue })
    }

    @Test
    fun `nothing follows the last of a series, or a book in no series`() {
        assertNull(nextAudiobook(current, emptyList(), listOf(audiobook("one", "Saga", 1f))) { 0f })
        assertNull(nextAudiobook(audiobook("solo"), emptyList(), listOf(audiobook("three", "Saga", 3f))) { 0f })
    }

    @Test
    fun `a book with no file and no server cannot be played, so it is passed over`() {
        val orphan = audiobook("orphan").copy(serverId = null)

        assertEquals("q", nextAudiobook(current, listOf(orphan, audiobook("q")), emptyList()) { 0f }?.book?.id)
        assertNull(nextAudiobook(current, listOf(orphan), emptyList()) { 0f })
    }

    @Test
    fun `moving up swaps a book with the one before it`() {
        assertEquals(listOf("a", "c", "b"), movedUp(listOf("a", "b", "c"), "c"))
        assertEquals(listOf("a", "b"), movedUp(listOf("a", "b"), "a"))
        assertEquals(listOf("a", "b"), movedUp(listOf("a", "b"), "missing"))
    }
}
