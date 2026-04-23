package com.ember.reader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ReaderPreferences(
    val fontFamily: FontFamily = FontFamily.SYSTEM,
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val marginHorizontal: Int = 16,
    val marginTop: Int = 0,
    val marginBottom: Int = 0,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val isPaginated: Boolean = true,
    val brightness: Float = -1f,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val textAlign: TextAlign = TextAlign.START,
    val publisherStyles: Boolean = true,
    val pageMargins: Float = 1.0f,
    val wordSpacing: Float = 0f,
    val letterSpacing: Float = 0f,
    val hyphenate: Boolean = true,
    val topTapZone: TapZoneBehavior = TapZoneBehavior.TOGGLE_CHROME,
    val leftTapZone: TapZoneBehavior = TapZoneBehavior.PREVIOUS_PAGE,
    val centerTapZone: TapZoneBehavior = TapZoneBehavior.NOTHING,
    val rightTapZone: TapZoneBehavior = TapZoneBehavior.NEXT_PAGE,
    val topZoneHeight: Float = 0.15f,
    val leftZoneWidth: Float = 0.33f,
    val rightZoneWidth: Float = 0.33f,
    val volumePageTurn: Boolean = false,
    val pdfFitMode: PdfFitMode = PdfFitMode.WIDTH,
    val pdfPageSpacing: Float = 8f,
    val showProgressIndicator: Boolean = true,
)

@Serializable
enum class PdfFitMode(val displayName: String) {
    WIDTH("Fit Width"),
    CONTAIN("Fit Page")
}

@Serializable
enum class TapZoneBehavior(val displayName: String) {
    PREVIOUS_PAGE("Previous Page"),
    NEXT_PAGE("Next Page"),
    TOGGLE_CHROME("Toggle Menu"),
    NOTHING("Nothing")
}

@Serializable
enum class TextAlign(val displayName: String) {
    START("Left"),
    JUSTIFY("Justify"),
    CENTER("Center")
}

@Serializable
enum class OrientationLock(val displayName: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

@Serializable
enum class FontFamily(val displayName: String, val cssValue: String?) {
    SYSTEM("System Default", null),
    SERIF("Serif", "serif"),
    SANS_SERIF("Sans Serif", "sans-serif"),
    MONOSPACE("Monospace", "monospace"),
    OPEN_DYSLEXIC("OpenDyslexic", "OpenDyslexic")
}

/**
 * Reader color themes. The first four use Readium's built-in Theme enum.
 * Custom themes pass [foregroundColor] and [backgroundColor] directly to
 * Readium's EpubPreferences as textColor/backgroundColor.
 *
 * Color values sourced from Grimmory's web reader theme palette.
 */
@Serializable
enum class ReaderTheme(
    val displayName: String,
    val foregroundColor: Long,
    val backgroundColor: Long,
    val isBuiltIn: Boolean = false
) {
    // Built-in Readium themes (colors here are for UI preview only)
    SYSTEM("System", 0xFF000000, 0xFFFFFFFF, isBuiltIn = true),
    LIGHT("Light", 0xFF000000, 0xFFFFFFFF, isBuiltIn = true),
    DARK("Dark", 0xFFE0E0E0, 0xFF222222, isBuiltIn = true),
    SEPIA("Sepia", 0xFF5B4636, 0xFFF1E8D0, isBuiltIn = true),

    // Custom color themes
    AMOLED("AMOLED", 0xFFFFFFFF, 0xFF000000),
    EMBER("Ember", 0xFFEBDBB2, 0xFF282828),
    AURORA("Aurora", 0xFFD8DEE9, 0xFF2E3440),
    OCEAN("Ocean", 0xFFB2DFDB, 0xFF263238),
    MIST("Mist", 0xFFC7B6DD, 0xFF3A3150),
    DAWNLIGHT("Dawnlight", 0xFF93A1A1, 0xFF002B36),
    ROSEWOOD("Rosewood", 0xFFE5C4C8, 0xFF462F32),
    MEADOW("Meadow", 0xFFD8DEBA, 0xFF333627),
    CRIMSON("Crimson", 0xFFDEE2E6, 0xFF343A40)
}
