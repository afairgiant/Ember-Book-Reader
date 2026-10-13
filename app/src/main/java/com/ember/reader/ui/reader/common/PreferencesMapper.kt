package com.ember.reader.ui.reader.common

import com.ember.reader.core.fonts.CustomFont
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.core.model.TextAlign
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.preferences.Theme

/**
 * Ember's reader preferences as the preferences Readium's EPUB navigator takes.
 *
 * Readium applies line height, alignment, hyphenation and the four spacing settings only
 * while [ReaderPreferences.publisherStyles] is off; with it on, the book's own styles win.
 *
 * @param customFonts the fonts declared to the reader; a selected one that is gone is ignored.
 */
fun ReaderPreferences.toEpubPreferences(customFonts: List<CustomFont> = emptyList()): EpubPreferences {
    // Built-in themes use Readium's Theme enum; custom themes pass colors directly
    val readiumTheme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.DARK -> Theme.DARK
        ReaderTheme.SEPIA -> Theme.SEPIA
        ReaderTheme.SYSTEM -> null
        else -> null // Custom themes don't use the Theme enum
    }
    val bgColor = if (!theme.isBuiltIn) ReadiumColor(theme.backgroundColor.toInt()) else null
    val fgColor = if (!theme.isBuiltIn) ReadiumColor(theme.foregroundColor.toInt()) else null

    return EpubPreferences(
        fontFamily = (customFont?.takeIf { name -> customFonts.any { it.name == name } } ?: fontFamily.cssValue)
            ?.let { ReadiumFontFamily(it) },
        fontSize = fontSize.toDouble() / 16.0,
        lineHeight = lineHeight.toDouble(),
        scroll = !isPaginated,
        theme = readiumTheme,
        backgroundColor = bgColor,
        textColor = fgColor,
        hyphens = hyphenate,
        textAlign = when (textAlign) {
            TextAlign.START -> ReadiumTextAlign.START
            TextAlign.JUSTIFY -> ReadiumTextAlign.JUSTIFY
            TextAlign.CENTER -> ReadiumTextAlign.CENTER
        },
        publisherStyles = publisherStyles,
        pageMargins = pageMargins.toDouble(),
        wordSpacing = wordSpacing.toDouble(),
        letterSpacing = letterSpacing.toDouble(),
        // Null leaves the book's own paragraph spacing and indent in place.
        paragraphSpacing = paragraphSpacing?.toDouble(),
        paragraphIndent = paragraphIndent?.toDouble()
    )
}
