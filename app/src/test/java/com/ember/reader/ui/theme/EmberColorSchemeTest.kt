package com.ember.reader.ui.theme

import androidx.compose.ui.graphics.Color
import com.ember.reader.core.repository.AppAccent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmberColorSchemeTest {

    @Test
    fun `oled black makes the dark background pure black`() {
        val scheme = emberColorScheme(darkTheme = true, oledBlack = true, accent = AppAccent.EMBER)

        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    @Test
    fun `oled black leaves the light theme alone`() {
        val plain = emberColorScheme(darkTheme = false, oledBlack = false, accent = AppAccent.EMBER)
        val oled = emberColorScheme(darkTheme = false, oledBlack = true, accent = AppAccent.EMBER)

        assertEquals(plain.background, oled.background)
        assertEquals(plain.surface, oled.surface)
    }

    @Test
    fun `an accent replaces the primary colour and nothing about the background`() {
        val ember = emberColorScheme(darkTheme = true, oledBlack = false, accent = AppAccent.EMBER)
        val blue = emberColorScheme(darkTheme = true, oledBlack = false, accent = AppAccent.BLUE)

        assertEquals(AppAccent.BLUE.seed, blue.primary)
        assertNotEquals(ember.primary, blue.primary)
        assertEquals(ember.background, blue.background)
    }

    @Test
    fun `an accent tints the secondary colours that selected tabs and chips are drawn in`() {
        for (dark in listOf(true, false)) {
            val ember = emberColorScheme(darkTheme = dark, oledBlack = false, accent = AppAccent.EMBER)
            val blue = emberColorScheme(darkTheme = dark, oledBlack = false, accent = AppAccent.BLUE)

            assertNotEquals(ember.secondaryContainer, blue.secondaryContainer)
            assertNotEquals(ember.secondary, blue.secondary)
            // Tinted towards blue: more blue than red, where ember's is the reverse.
            assertTrue(blue.secondaryContainer.blue > blue.secondaryContainer.red)
            assertTrue(ember.secondaryContainer.red > ember.secondaryContainer.blue)
        }
    }

    @Test
    fun `the default accent keeps the built-in secondary colours`() {
        val scheme = emberColorScheme(darkTheme = true, oledBlack = false, accent = AppAccent.EMBER)

        assertEquals(Color(0xFF5D3F36), scheme.secondaryContainer)
    }

    @Test
    fun `every accent has its own swatch colour`() {
        val seeds = AppAccent.entries.map { it.seed }

        assertEquals(seeds.size, seeds.toSet().size)
    }
}
