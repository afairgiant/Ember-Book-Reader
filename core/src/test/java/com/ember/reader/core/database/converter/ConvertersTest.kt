package com.ember.reader.core.database.converter

import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.HighlightColor
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConvertersTest {

    private val converters = Converters()

    // --- Instant round-trip ---

    @Test
    fun `fromInstant returns epoch millis`() {
        val instant = Instant.ofEpochMilli(1700000000000L)
        assertEquals(1700000000000L, converters.fromInstant(instant))
    }

    @Test
    fun `toInstant returns Instant from epoch millis`() {
        val instant = Instant.ofEpochMilli(1700000000000L)
        assertEquals(instant, converters.toInstant(1700000000000L))
    }

    @Test
    fun `Instant round-trip preserves value`() {
        val original = Instant.ofEpochMilli(1234567890123L)
        val restored = converters.toInstant(converters.fromInstant(original))
        assertEquals(original, restored)
    }

    @Test
    fun `fromInstant null returns null`() {
        assertNull(converters.fromInstant(null))
    }

    @Test
    fun `toInstant null returns null`() {
        assertNull(converters.toInstant(null))
    }

    // --- BookFormat round-trip ---

    @Test
    fun `fromBookFormat returns name string`() {
        assertEquals("EPUB", converters.fromBookFormat(BookFormat.EPUB))
        assertEquals("PDF", converters.fromBookFormat(BookFormat.PDF))
        assertEquals("AUDIOBOOK", converters.fromBookFormat(BookFormat.AUDIOBOOK))
    }

    @Test
    fun `toBookFormat parses name string`() {
        assertEquals(BookFormat.EPUB, converters.toBookFormat("EPUB"))
        assertEquals(BookFormat.PDF, converters.toBookFormat("PDF"))
        assertEquals(BookFormat.AUDIOBOOK, converters.toBookFormat("AUDIOBOOK"))
    }

    @Test
    fun `BookFormat round-trip preserves all values`() {
        BookFormat.entries.forEach { format ->
            assertEquals(format, converters.toBookFormat(converters.fromBookFormat(format)))
        }
    }

    @Test
    fun `toBookFormat throws on invalid string`() {
        assertThrows<IllegalArgumentException> {
            converters.toBookFormat("INVALID")
        }
    }

    // --- HighlightColor round-trip ---

    @Test
    fun `fromHighlightColor returns name string`() {
        assertEquals("YELLOW", converters.fromHighlightColor(HighlightColor.YELLOW))
        assertEquals("PINK", converters.fromHighlightColor(HighlightColor.PINK))
    }

    @Test
    fun `toHighlightColor parses name string`() {
        assertEquals(HighlightColor.YELLOW, converters.toHighlightColor("YELLOW"))
        assertEquals(HighlightColor.BLUE, converters.toHighlightColor("BLUE"))
    }

    @Test
    fun `HighlightColor round-trip preserves all values`() {
        HighlightColor.entries.forEach { color ->
            assertEquals(color, converters.toHighlightColor(converters.fromHighlightColor(color)))
        }
    }

    @Test
    fun `toHighlightColor throws on invalid string`() {
        assertThrows<IllegalArgumentException> {
            converters.toHighlightColor("INVALID")
        }
    }
}
