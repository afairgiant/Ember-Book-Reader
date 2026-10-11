package com.ember.reader.core.fonts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomFontTest {

    @Test
    fun `a font file becomes a font named after it`() {
        assertEquals(
            CustomFont(name = "Literata-Regular", fileName = "Literata-Regular.ttf"),
            CustomFont.fromDisplayName("Literata-Regular.ttf")
        )
        assertEquals("otf", CustomFont.fromDisplayName("Garamond.OTF")!!.fileName.substringAfterLast('.'))
    }

    @Test
    fun `characters that would break a CSS name or a URL are replaced`() {
        val font = CustomFont.fromDisplayName("""My "Best" Font (v2).ttf""")!!

        assertEquals("My Best Font v2", font.name)
        assertEquals("My__Best__Font__v2_.ttf", font.fileName)
    }

    @Test
    fun `other files are not fonts`() {
        assertNull(CustomFont.fromDisplayName("book.epub"))
        assertNull(CustomFont.fromDisplayName("font.woff2"))
        assertNull(CustomFont.fromDisplayName("noextension"))
        assertNull(CustomFont.fromDisplayName("***.ttf"))
    }

    @Test
    fun `font files are recognised by their first bytes`() {
        assertTrue(CustomFont.isFontHeader(byteArrayOf(0, 1, 0, 0, 0, 12)))
        assertTrue(CustomFont.isFontHeader("OTTO....".toByteArray()))
        assertTrue(CustomFont.isFontHeader("true....".toByteArray()))
        assertFalse(CustomFont.isFontHeader("PK\u0003\u0004".toByteArray()))
        assertFalse(CustomFont.isFontHeader(byteArrayOf(0, 1)))
    }
}
