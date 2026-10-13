package com.ember.reader.ui.reader.common

import com.ember.reader.core.fonts.CustomFont
import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.core.model.TextAlign
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.preferences.Theme

class PreferencesMapperTest {

    @Test
    fun `spacing values are passed through`() {
        val epub = ReaderPreferences(
            wordSpacing = 0.3f,
            letterSpacing = 0.1f,
            paragraphSpacing = 1.5f,
            paragraphIndent = 2f
        ).toEpubPreferences()

        assertEquals(0.3, epub.wordSpacing!!, 0.0001)
        assertEquals(0.1, epub.letterSpacing!!, 0.0001)
        assertEquals(1.5, epub.paragraphSpacing!!, 0.0001)
        assertEquals(2.0, epub.paragraphIndent!!, 0.0001)
    }

    @Test
    fun `paragraph spacing and indent are left to the book by default`() {
        val epub = ReaderPreferences().toEpubPreferences()

        assertNull(epub.paragraphSpacing)
        assertNull(epub.paragraphIndent)
    }

    @Test
    fun `a zero paragraph indent is sent as zero, not left to the book`() {
        val epub = ReaderPreferences(paragraphIndent = 0f).toEpubPreferences()

        assertEquals(0.0, epub.paragraphIndent!!, 0.0001)
    }

    @Test
    fun `font size is sent as a scale of the 16 point base`() {
        assertEquals(1.25, ReaderPreferences(fontSize = 20f).toEpubPreferences().fontSize!!, 0.0001)
    }

    @Test
    fun `scroll is the opposite of paginated`() {
        assertEquals(false, ReaderPreferences(isPaginated = true).toEpubPreferences().scroll)
        assertEquals(true, ReaderPreferences(isPaginated = false).toEpubPreferences().scroll)
    }

    @Test
    fun `built-in themes use Readium's theme and no colours`() {
        val epub = ReaderPreferences(theme = ReaderTheme.SEPIA).toEpubPreferences()

        assertEquals(Theme.SEPIA, epub.theme)
        assertNull(epub.backgroundColor)
        assertNull(epub.textColor)
    }

    @Test
    fun `the system theme sets neither a theme nor colours`() {
        val epub = ReaderPreferences(theme = ReaderTheme.SYSTEM).toEpubPreferences()

        assertNull(epub.theme)
        assertNull(epub.backgroundColor)
    }

    @Test
    fun `a custom theme sends its colours and no Readium theme`() {
        val custom = ReaderTheme.entries.first { !it.isBuiltIn }
        val epub = ReaderPreferences(theme = custom).toEpubPreferences()

        assertNull(epub.theme)
        assertEquals(custom.backgroundColor.toInt(), epub.backgroundColor!!.int)
        assertEquals(custom.foregroundColor.toInt(), epub.textColor!!.int)
    }

    @Test
    fun `text alignment maps to Readium's`() {
        assertEquals(
            ReadiumTextAlign.JUSTIFY,
            ReaderPreferences(textAlign = TextAlign.JUSTIFY).toEpubPreferences().textAlign
        )
    }

    @Test
    fun `a custom font that still exists is used in place of the font family`() {
        val prefs = ReaderPreferences(fontFamily = FontFamily.SERIF, customFont = "Bookerly")
        val fonts = listOf(CustomFont(name = "Bookerly", fileName = "bookerly.ttf"))

        assertEquals("Bookerly", prefs.toEpubPreferences(fonts).fontFamily?.name)
    }

    @Test
    fun `a custom font that is gone falls back to the font family`() {
        val prefs = ReaderPreferences(fontFamily = FontFamily.SERIF, customFont = "Bookerly")

        assertEquals("serif", prefs.toEpubPreferences(emptyList()).fontFamily?.name)
    }

    @Test
    fun `the system font sends no font family`() {
        assertNull(ReaderPreferences(fontFamily = FontFamily.SYSTEM).toEpubPreferences().fontFamily)
    }
}
