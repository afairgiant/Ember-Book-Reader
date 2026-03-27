package com.ember.reader.core.repository

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerPreferencesDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "reader_preferences")

@Singleton
class ReaderPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val MARGIN_HORIZONTAL = intPreferencesKey("margin_horizontal")
        val MARGIN_VERTICAL = intPreferencesKey("margin_vertical")
        val THEME = stringPreferencesKey("theme")
        val IS_PAGINATED = booleanPreferencesKey("is_paginated")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
    }

    val preferencesFlow: Flow<ReaderPreferences> =
        context.readerPreferencesDataStore.data.map { prefs ->
            ReaderPreferences(
                fontFamily = prefs[Keys.FONT_FAMILY]?.let { FontFamily.valueOf(it) }
                    ?: FontFamily.SYSTEM,
                fontSize = prefs[Keys.FONT_SIZE] ?: 16f,
                lineHeight = prefs[Keys.LINE_HEIGHT] ?: 1.5f,
                marginHorizontal = prefs[Keys.MARGIN_HORIZONTAL] ?: 16,
                marginVertical = prefs[Keys.MARGIN_VERTICAL] ?: 16,
                theme = prefs[Keys.THEME]?.let { ReaderTheme.valueOf(it) }
                    ?: ReaderTheme.SYSTEM,
                isPaginated = prefs[Keys.IS_PAGINATED] ?: true,
                brightness = prefs[Keys.BRIGHTNESS] ?: -1f,
                orientationLock = prefs[Keys.ORIENTATION_LOCK]?.let {
                    runCatching { OrientationLock.valueOf(it) }.getOrNull()
                } ?: OrientationLock.AUTO,
            )
        }

    suspend fun updatePreferences(preferences: ReaderPreferences) {
        context.readerPreferencesDataStore.edit { prefs ->
            prefs[Keys.FONT_FAMILY] = preferences.fontFamily.name
            prefs[Keys.FONT_SIZE] = preferences.fontSize
            prefs[Keys.LINE_HEIGHT] = preferences.lineHeight
            prefs[Keys.MARGIN_HORIZONTAL] = preferences.marginHorizontal
            prefs[Keys.MARGIN_VERTICAL] = preferences.marginVertical
            prefs[Keys.THEME] = preferences.theme.name
            prefs[Keys.IS_PAGINATED] = preferences.isPaginated
            prefs[Keys.BRIGHTNESS] = preferences.brightness
            prefs[Keys.ORIENTATION_LOCK] = preferences.orientationLock.name
        }
    }
}
