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
)

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
