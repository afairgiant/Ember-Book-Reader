package com.ember.reader.core.model

data class ReaderPreferences(
    val fontFamily: FontFamily = FontFamily.SYSTEM,
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val marginHorizontal: Int = 16,
    val marginVertical: Int = 16,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val isPaginated: Boolean = true,
    val brightness: Float = -1f,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val textAlign: TextAlign = TextAlign.START,
    val publisherStyles: Boolean = true,
    val pageMargins: Float = 1.0f,
    val wordSpacing: Float = 0f,
    val letterSpacing: Float = 0f,
    val topTapZone: TapZoneBehavior = TapZoneBehavior.TOGGLE_CHROME,
    val leftTapZone: TapZoneBehavior = TapZoneBehavior.PREVIOUS_PAGE,
    val centerTapZone: TapZoneBehavior = TapZoneBehavior.NOTHING,
    val rightTapZone: TapZoneBehavior = TapZoneBehavior.NEXT_PAGE,
    val topZoneHeight: Float = 0.15f,
    val leftZoneWidth: Float = 0.33f,
    val rightZoneWidth: Float = 0.33f,
)

enum class TapZoneBehavior(val displayName: String) {
    PREVIOUS_PAGE("Previous Page"),
    NEXT_PAGE("Next Page"),
    TOGGLE_CHROME("Toggle Menu"),
    NOTHING("Nothing"),
}

enum class TextAlign(val displayName: String) {
    START("Left"),
    JUSTIFY("Justify"),
    CENTER("Center"),
}

enum class OrientationLock(val displayName: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

enum class FontFamily(val displayName: String, val cssValue: String?) {
    SYSTEM("System Default", null),
    SERIF("Serif", "serif"),
    SANS_SERIF("Sans Serif", "sans-serif"),
    MONOSPACE("Monospace", "monospace"),
    OPEN_DYSLEXIC("OpenDyslexic", "OpenDyslexic"),
}

enum class ReaderTheme(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    SEPIA("Sepia"),
}
