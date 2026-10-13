package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryBookSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NewBooksTest {

    private fun book(id: Long, addedOn: String?) = GrimmoryBookSummary(id = id, title = "Book $id", addedOn = addedOn)

    private val monday = "2026-10-12T09:00:00Z"
    private val tuesday = "2026-10-13T09:00:00Z"
    private val wednesday = "2026-10-14T09:00:00Z"

    @Test
    fun `nothing is new the first time`() {
        assertTrue(newBooksSince(null, listOf(book(1, monday), book(2, tuesday))).isEmpty())
    }

    @Test
    fun `a book added since the last look is new`() {
        val seen = seenAfter(null, listOf(book(1, monday), book(2, tuesday)))

        val new = newBooksSince(seen, listOf(book(3, wednesday), book(2, tuesday), book(1, monday)))

        assertEquals(listOf(3L), new.map { it.id })
    }

    @Test
    fun `an older book that moves into the list when one is removed is not new`() {
        val seen = seenAfter(null, listOf(book(2, tuesday), book(3, wednesday)))

        // Book 3 was removed from the server, so book 1 is listed in its place.
        val new = newBooksSince(seen, listOf(book(2, tuesday), book(1, monday)))

        assertTrue(new.isEmpty())
    }

    @Test
    fun `the latest time is kept when the list gets older`() {
        val seen = seenAfter(null, listOf(book(3, wednesday)))

        val after = seenAfter(seen, listOf(book(1, monday)))

        assertEquals(wednesday, after.newestAddedOn)
        assertEquals(setOf(1L), after.ids)
    }

    @Test
    fun `what was seen survives being stored`() {
        val seen = SeenBooks(newestAddedOn = tuesday, ids = setOf(4L, 9L))

        assertEquals(seen, SeenBooks.decode(seen.encode()))
        assertEquals(SeenBooks(null, emptySet()), SeenBooks.decode(SeenBooks(null, emptySet()).encode()))
        assertNull(SeenBooks.decode(null))
        assertNull(SeenBooks.decode("nonsense"))
    }

    @Test
    fun `the heading counts the books`() {
        assertEquals("New book on Home", newBooksTitle(1, "Home"))
        assertEquals("3 new books on Home", newBooksTitle(3, "Home"))
    }
}
