package com.ember.reader.ui.reader.common

import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
object PreferencesMapper {

    fun toEpubPreferences(preferences: ReaderPreferences): EpubPreferences =
        EpubPreferences(
            fontFamily = mapFontFamily(preferences.fontFamily),
            fontSize = preferences.fontSize.toDouble(),
            lineHeight = preferences.lineHeight.toDouble(),
            scroll = !preferences.isPaginated,
            theme = mapTheme(preferences.theme),
        )

    private fun mapFontFamily(fontFamily: FontFamily): org.readium.r2.navigator.preferences.FontFamily? =
        fontFamily.cssValue?.let { org.readium.r2.navigator.preferences.FontFamily(it) }

    private fun mapTheme(theme: ReaderTheme): org.readium.r2.navigator.epub.EpubTheme? =
        when (theme) {
            ReaderTheme.SYSTEM -> null
            ReaderTheme.LIGHT -> org.readium.r2.navigator.epub.EpubTheme.LIGHT
            ReaderTheme.DARK -> org.readium.r2.navigator.epub.EpubTheme.DARK
            ReaderTheme.SEPIA -> org.readium.r2.navigator.epub.EpubTheme.SEPIA
        }
}
