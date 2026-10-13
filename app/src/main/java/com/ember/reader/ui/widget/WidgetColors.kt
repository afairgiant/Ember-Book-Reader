package com.ember.reader.ui.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.ember.reader.core.repository.AppAccent
import com.ember.reader.core.repository.ThemeMode
import com.ember.reader.ui.theme.emberColorScheme

/** The widget's colours, as ARGB ints, taken from the app's colour scheme. */
data class WidgetColors(
    val background: Int,
    val outline: Int,
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    /** The unfilled part of the progress bar, which the bar's own drawable makes faint. */
    val track: Int
)

/**
 * The widget's colours for when the device is in its light theme and when it is in its dark
 * one. They are the same when the app is set to stay light or stay dark.
 */
data class WidgetPalette(val day: WidgetColors, val night: WidgetColors) {
    fun forDevice(deviceIsDark: Boolean): WidgetColors = if (deviceIsDark) night else day
}

internal fun widgetColorsOf(scheme: ColorScheme) = WidgetColors(
    background = scheme.surfaceContainerLow.toArgb(),
    outline = scheme.outlineVariant.toArgb(),
    text = scheme.onSurface.toArgb(),
    textSecondary = scheme.onSurfaceVariant.toArgb(),
    accent = scheme.primary.toArgb(),
    track = scheme.outline.toArgb()
)

/** The widget's colours for the app's theme settings, so the widget looks like the app. */
internal fun widgetPaletteOf(themeMode: ThemeMode, oledBlack: Boolean, accent: AppAccent): WidgetPalette {
    val light = widgetColorsOf(emberColorScheme(darkTheme = false, oledBlack = oledBlack, accent = accent))
    val dark = widgetColorsOf(emberColorScheme(darkTheme = true, oledBlack = oledBlack, accent = accent))
    return when (themeMode) {
        ThemeMode.LIGHT -> WidgetPalette(day = light, night = light)
        ThemeMode.DARK -> WidgetPalette(day = dark, night = dark)
        ThemeMode.SYSTEM -> WidgetPalette(day = light, night = dark)
    }
}
