package com.ember.reader.ui.widget

import androidx.compose.ui.graphics.toArgb
import com.ember.reader.core.repository.AppAccent
import com.ember.reader.core.repository.ThemeMode
import com.ember.reader.ui.theme.emberColorScheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class WidgetColorsTest {

    private val accent = AppAccent.entries.first { it != AppAccent.EMBER }

    @Test
    fun `following the device gives the light scheme by day and the dark one by night`() {
        val palette = widgetPaletteOf(ThemeMode.SYSTEM, oledBlack = false, accent = AppAccent.EMBER)

        assertEquals(widgetColorsOf(emberColorScheme(false, false, AppAccent.EMBER)), palette.day)
        assertEquals(widgetColorsOf(emberColorScheme(true, false, AppAccent.EMBER)), palette.night)
        assertNotEquals(palette.day, palette.night)
    }

    @Test
    fun `an app set to stay light or dark is that way whatever the device does`() {
        val light = widgetPaletteOf(ThemeMode.LIGHT, oledBlack = false, accent = AppAccent.EMBER)
        val dark = widgetPaletteOf(ThemeMode.DARK, oledBlack = false, accent = AppAccent.EMBER)

        assertEquals(light.day, light.night)
        assertEquals(dark.day, dark.night)
        assertEquals(dark.night, dark.forDevice(deviceIsDark = false))
        assertNotEquals(light.day, dark.day)
    }

    @Test
    fun `the progress bar takes the chosen accent`() {
        val ember = widgetPaletteOf(ThemeMode.SYSTEM, oledBlack = false, accent = AppAccent.EMBER)
        val other = widgetPaletteOf(ThemeMode.SYSTEM, oledBlack = false, accent = accent)

        assertEquals(emberColorScheme(true, false, accent).primary.toArgb(), other.night.accent)
        assertNotEquals(ember.night.accent, other.night.accent)
        assertNotEquals(ember.day.accent, other.day.accent)
    }

    @Test
    fun `pure black reaches the dark widget only`() {
        val plain = widgetPaletteOf(ThemeMode.SYSTEM, oledBlack = false, accent = AppAccent.EMBER)
        val black = widgetPaletteOf(ThemeMode.SYSTEM, oledBlack = true, accent = AppAccent.EMBER)

        assertEquals(plain.day, black.day)
        assertNotEquals(plain.night.background, black.night.background)
    }
}
