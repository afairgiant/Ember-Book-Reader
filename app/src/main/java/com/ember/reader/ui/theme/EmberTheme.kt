package com.ember.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.ember.reader.core.repository.AppAccent

private val EmberOrange = Color(0xFFE65100)
private val EmberOrangeLight = Color(0xFFFF833A)
private val EmberOrangeDark = Color(0xFFAC1900)

// Warm tinted surfaces for the ember aesthetic
val EmberCardLight = Color(0xFFFFF0E8)
val EmberCardDark = Color(0xFF2E2220)

// Per-server accent palette. Chosen to read against both light (#FFF0E8) and dark (#2E2220)
// surfaceVariant, mutually distinct, and clear of the EmberOrange primary family.
// Index is persisted in Server.accentColorSlot.
// Order: blue, teal, green, purple, pink, amber.
val ServerAccentPalette: List<Color> = listOf(
    Color(0xFF4A90D9),
    Color(0xFF2AA198),
    Color(0xFF4E9A3E),
    Color(0xFF9061C2),
    Color(0xFFD65D9B),
    Color(0xFFCFA13B)
)

// Neutral grey for local-only books. Not part of the rotation.
val LocalAccentColor: Color = Color(0xFF6E6A68)

/**
 * Resolves a server accent slot index to its palette [Color], wrapping past the palette
 * size. The single source of truth for slot-to-color lookup across card badges, list
 * pills, group headers, and anywhere else servers need to render their identity.
 */
fun serverAccentColor(slot: Int): Color = ServerAccentPalette[slot.mod(ServerAccentPalette.size)]

private val LightColorScheme = lightColorScheme(
    primary = EmberOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0A00),
    secondary = Color(0xFF77574D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0D6),
    onSecondaryContainer = Color(0xFF2C160E),
    tertiary = Color(0xFF6A5E2F),
    tertiaryContainer = Color(0xFFF3E2A7),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFFFF0E8),
    onSurfaceVariant = Color(0xFF534340),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEAE2),
    surfaceContainerHigh = Color(0xFFF6E4DC),
    surfaceContainerHighest = Color(0xFFF0DED6),
    background = Color(0xFFFFF8F5),
    outline = Color(0xFFD4A898),
    outlineVariant = Color(0xFFE3CFC7)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmberOrangeLight,
    onPrimary = Color(0xFF5F1500),
    primaryContainer = EmberOrangeDark,
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BEAF),
    onSecondary = Color(0xFF442A21),
    secondaryContainer = Color(0xFF5D3F36),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD6C68D),
    tertiaryContainer = Color(0xFF51461A),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFEDE0DB),
    surfaceVariant = Color(0xFF2E2220),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFF140E0C),
    surfaceContainerLow = Color(0xFF221917),
    surfaceContainer = Color(0xFF261D1B),
    surfaceContainerHigh = Color(0xFF312725),
    surfaceContainerHighest = Color(0xFF3C3230),
    background = Color(0xFF1A1210),
    outline = Color(0xFF7D5D53),
    outlineVariant = Color(0xFF53433F)
)

/** The colour an accent is shown as in its swatch, and the one its scheme is built from. */
val AppAccent.seed: Color
    get() = when (this) {
        AppAccent.EMBER -> EmberOrange
        AppAccent.RED -> Color(0xFFE0503E)
        AppAccent.AMBER -> Color(0xFFE0A100)
        AppAccent.BLUE -> Color(0xFF3F8FE0)
        AppAccent.TEAL -> Color(0xFF2FB5C8)
        AppAccent.GREEN -> Color(0xFF43B85C)
        AppAccent.PURPLE -> Color(0xFF9B5DE0)
    }

/**
 * The app's colours for the chosen theme. [oledBlack] only applies to the dark theme, where
 * it makes the background pure black. [AppAccent.EMBER] leaves the built-in scheme as it is.
 */
fun emberColorScheme(darkTheme: Boolean, oledBlack: Boolean, accent: AppAccent): ColorScheme {
    var scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    if (darkTheme && oledBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF12100F),
            surfaceContainer = Color(0xFF171413),
            surfaceContainerHigh = Color(0xFF1F1B1A),
            surfaceContainerHighest = Color(0xFF292423),
            outlineVariant = Color(0xFF3A322F)
        )
    }
    if (accent != AppAccent.EMBER) {
        // Darkened on a light background so text and icons in the accent stay readable.
        val primary = if (darkTheme) accent.seed else lerp(accent.seed, Color.Black, 0.25f)
        // The secondary colours follow too. Material draws the selected tab's pill,
        // selected chips and segments, and tonal buttons in them, and left as they were
        // those stayed ember-brown under every accent.
        scheme = scheme.copy(
            primary = primary,
            onPrimary = if (primary.luminance() > 0.4f) Color(0xFF1A1210) else Color.White,
            primaryContainer = lerp(scheme.surface, primary, 0.3f),
            onPrimaryContainer = scheme.onSurface,
            secondary = lerp(scheme.onSurfaceVariant, primary, 0.5f),
            onSecondary = scheme.surface,
            secondaryContainer = lerp(scheme.surface, primary, 0.22f),
            onSecondaryContainer = scheme.onSurface
        )
    }
    return scheme
}

@Composable
fun EmberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    oledBlack: Boolean = false,
    accent: AppAccent = AppAccent.EMBER,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> emberColorScheme(darkTheme, oledBlack, accent)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
