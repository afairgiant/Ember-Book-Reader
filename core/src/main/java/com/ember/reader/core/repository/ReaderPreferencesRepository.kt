package com.ember.reader.core.repository

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ember.reader.core.model.FontFamily
import com.ember.reader.core.model.OrientationLock
import com.ember.reader.core.model.ReaderPreferences
import com.ember.reader.core.model.ReaderTheme
import com.ember.reader.core.model.TapZoneBehavior
import com.ember.reader.core.model.TextAlign
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val MARGINS_MIGRATED_V1 = booleanPreferencesKey("margins_migrated_v1")

private val marginResetMigration = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[MARGINS_MIGRATED_V1] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mutable = currentData.toMutablePreferences()
        mutable.remove(intPreferencesKey("margin_top"))
        mutable.remove(intPreferencesKey("margin_bottom"))
        mutable[MARGINS_MIGRATED_V1] = true
        return mutable.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

private val Context.readerPreferencesDataStore: DataStore<Preferences>
    by preferencesDataStore(
        name = "reader_preferences",
        produceMigrations = { listOf(marginResetMigration) },
    )

@Singleton
class ReaderPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val MARGIN_HORIZONTAL = intPreferencesKey("margin_horizontal")
        val MARGIN_TOP = intPreferencesKey("margin_top")
        val MARGIN_BOTTOM = intPreferencesKey("margin_bottom")
        val THEME = stringPreferencesKey("theme")
        val IS_PAGINATED = booleanPreferencesKey("is_paginated")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
        val TEXT_ALIGN = stringPreferencesKey("text_align")
        val PUBLISHER_STYLES = booleanPreferencesKey("publisher_styles")
        val PAGE_MARGINS = floatPreferencesKey("page_margins")
        val WORD_SPACING = floatPreferencesKey("word_spacing")
        val LETTER_SPACING = floatPreferencesKey("letter_spacing")
        val HYPHENATE = booleanPreferencesKey("hyphenate")
        val TOP_TAP_ZONE = stringPreferencesKey("top_tap_zone")
        val LEFT_TAP_ZONE = stringPreferencesKey("left_tap_zone")
        val CENTER_TAP_ZONE = stringPreferencesKey("center_tap_zone")
        val RIGHT_TAP_ZONE = stringPreferencesKey("right_tap_zone")
        val TOP_ZONE_HEIGHT = floatPreferencesKey("top_zone_height")
        val LEFT_ZONE_WIDTH = floatPreferencesKey("left_zone_width")
        val RIGHT_ZONE_WIDTH = floatPreferencesKey("right_zone_width")
        val VOLUME_PAGE_TURN = booleanPreferencesKey("volume_page_turn")
        val PDF_FIT_MODE = stringPreferencesKey("pdf_fit_mode")
        val PDF_PAGE_SPACING = floatPreferencesKey("pdf_page_spacing")
        val SHOW_PROGRESS_INDICATOR = booleanPreferencesKey("show_progress_indicator")
    }

    val preferencesFlow: Flow<ReaderPreferences> =
        context.readerPreferencesDataStore.data.map { prefs ->
            ReaderPreferences(
                fontFamily = prefs[Keys.FONT_FAMILY]?.let { FontFamily.valueOf(it) }
                    ?: FontFamily.SYSTEM,
                fontSize = prefs[Keys.FONT_SIZE] ?: 16f,
                lineHeight = prefs[Keys.LINE_HEIGHT] ?: 1.5f,
                marginHorizontal = prefs[Keys.MARGIN_HORIZONTAL] ?: 16,
                marginTop = prefs[Keys.MARGIN_TOP] ?: 0,
                marginBottom = prefs[Keys.MARGIN_BOTTOM] ?: 0,
                theme = prefs[Keys.THEME]?.let { ReaderTheme.valueOf(it) }
                    ?: ReaderTheme.SYSTEM,
                isPaginated = prefs[Keys.IS_PAGINATED] ?: true,
                brightness = prefs[Keys.BRIGHTNESS] ?: -1f,
                orientationLock = prefs[Keys.ORIENTATION_LOCK]?.let {
                    runCatching { OrientationLock.valueOf(it) }.getOrNull()
                } ?: OrientationLock.AUTO,
                textAlign = prefs[Keys.TEXT_ALIGN]?.let {
                    runCatching { TextAlign.valueOf(it) }.getOrNull()
                } ?: TextAlign.START,
                publisherStyles = prefs[Keys.PUBLISHER_STYLES] ?: true,
                pageMargins = prefs[Keys.PAGE_MARGINS] ?: 1.0f,
                wordSpacing = prefs[Keys.WORD_SPACING] ?: 0f,
                letterSpacing = prefs[Keys.LETTER_SPACING] ?: 0f,
                hyphenate = prefs[Keys.HYPHENATE] ?: true,
                topTapZone = prefs[Keys.TOP_TAP_ZONE]?.let {
                    runCatching { TapZoneBehavior.valueOf(it) }.getOrNull()
                } ?: TapZoneBehavior.TOGGLE_CHROME,
                leftTapZone = prefs[Keys.LEFT_TAP_ZONE]?.let {
                    runCatching { TapZoneBehavior.valueOf(it) }.getOrNull()
                } ?: TapZoneBehavior.PREVIOUS_PAGE,
                centerTapZone = prefs[Keys.CENTER_TAP_ZONE]?.let {
                    runCatching { TapZoneBehavior.valueOf(it) }.getOrNull()
                } ?: TapZoneBehavior.NOTHING,
                rightTapZone = prefs[Keys.RIGHT_TAP_ZONE]?.let {
                    runCatching { TapZoneBehavior.valueOf(it) }.getOrNull()
                } ?: TapZoneBehavior.NEXT_PAGE,
                topZoneHeight = prefs[Keys.TOP_ZONE_HEIGHT] ?: 0.15f,
                leftZoneWidth = prefs[Keys.LEFT_ZONE_WIDTH] ?: 0.33f,
                rightZoneWidth = prefs[Keys.RIGHT_ZONE_WIDTH] ?: 0.33f,
                volumePageTurn = prefs[Keys.VOLUME_PAGE_TURN] ?: false,
                pdfFitMode = prefs[Keys.PDF_FIT_MODE]?.let {
                    runCatching { com.ember.reader.core.model.PdfFitMode.valueOf(it) }.getOrNull()
                } ?: com.ember.reader.core.model.PdfFitMode.WIDTH,
                pdfPageSpacing = prefs[Keys.PDF_PAGE_SPACING] ?: 8f,
                showProgressIndicator = prefs[Keys.SHOW_PROGRESS_INDICATOR] ?: true,
            )
        }

    suspend fun updatePreferences(preferences: ReaderPreferences) {
        context.readerPreferencesDataStore.edit { prefs ->
            prefs[Keys.FONT_FAMILY] = preferences.fontFamily.name
            prefs[Keys.FONT_SIZE] = preferences.fontSize
            prefs[Keys.LINE_HEIGHT] = preferences.lineHeight
            prefs[Keys.MARGIN_HORIZONTAL] = preferences.marginHorizontal
            prefs[Keys.MARGIN_TOP] = preferences.marginTop
            prefs[Keys.MARGIN_BOTTOM] = preferences.marginBottom
            prefs[Keys.THEME] = preferences.theme.name
            prefs[Keys.IS_PAGINATED] = preferences.isPaginated
            prefs[Keys.BRIGHTNESS] = preferences.brightness
            prefs[Keys.ORIENTATION_LOCK] = preferences.orientationLock.name
            prefs[Keys.TEXT_ALIGN] = preferences.textAlign.name
            prefs[Keys.PUBLISHER_STYLES] = preferences.publisherStyles
            prefs[Keys.PAGE_MARGINS] = preferences.pageMargins
            prefs[Keys.WORD_SPACING] = preferences.wordSpacing
            prefs[Keys.LETTER_SPACING] = preferences.letterSpacing
            prefs[Keys.HYPHENATE] = preferences.hyphenate
            prefs[Keys.TOP_TAP_ZONE] = preferences.topTapZone.name
            prefs[Keys.LEFT_TAP_ZONE] = preferences.leftTapZone.name
            prefs[Keys.CENTER_TAP_ZONE] = preferences.centerTapZone.name
            prefs[Keys.RIGHT_TAP_ZONE] = preferences.rightTapZone.name
            prefs[Keys.TOP_ZONE_HEIGHT] = preferences.topZoneHeight
            prefs[Keys.LEFT_ZONE_WIDTH] = preferences.leftZoneWidth
            prefs[Keys.RIGHT_ZONE_WIDTH] = preferences.rightZoneWidth
            prefs[Keys.VOLUME_PAGE_TURN] = preferences.volumePageTurn
            prefs[Keys.PDF_FIT_MODE] = preferences.pdfFitMode.name
            prefs[Keys.PDF_PAGE_SPACING] = preferences.pdfPageSpacing
            prefs[Keys.SHOW_PROGRESS_INDICATOR] = preferences.showProgressIndicator
        }
    }
}
