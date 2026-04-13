package com.ember.reader.core.model

import com.ember.reader.core.testutil.TestFixtures.book
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookTest {

    @Test
    fun `grimmoryBookId parses from urn format`() {
        val book = book(opdsEntryId = "urn:booklore:book:123")
        assertEquals(123L, book.grimmoryBookId)
    }

    @Test
    fun `grimmoryBookId parses large id`() {
        val book = book(opdsEntryId = "urn:booklore:book:99999")
        assertEquals(99999L, book.grimmoryBookId)
    }

    @Test
    fun `grimmoryBookId returns null for non-urn format`() {
        val book = book(opdsEntryId = "some-other-format")
        assertNull(book.grimmoryBookId)
    }

    @Test
    fun `grimmoryBookId returns null when opdsEntryId is null`() {
        val book = book(opdsEntryId = null)
        assertNull(book.grimmoryBookId)
    }

    @Test
    fun `grimmoryBookId returns null for non-numeric suffix`() {
        val book = book(opdsEntryId = "urn:booklore:book:abc")
        assertNull(book.grimmoryBookId)
    }

    @Test
    fun `isDownloaded reflects localPath presence`() {
        assertTrue(book(localPath = "/path/to/book.epub").isDownloaded)
        assertFalse(book(localPath = null).isDownloaded)
    }

    @Test
    fun `isLocal reflects serverId absence`() {
        assertTrue(book(serverId = null).isLocal)
        assertFalse(book(serverId = 1L).isLocal)
    }
}
