package com.ember.reader.core.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "app_preferences")

enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Singleton
class AppPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_CLEANUP = booleanPreferencesKey("auto_cleanup")
        val AUTO_DOWNLOAD_READING = booleanPreferencesKey("auto_download_reading")
        val HAS_SEEN_TAP_ZONE_HINT = booleanPreferencesKey("has_seen_tap_zone_hint")
    }

    val themeModeFlow: Flow<ThemeMode> = context.appPreferencesDataStore.data
        .map { prefs ->
            prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM
        }

    val keepScreenOnFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.KEEP_SCREEN_ON] ?: false }

    val autoCleanupFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.AUTO_CLEANUP] ?: false }

    val autoDownloadReadingFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.AUTO_DOWNLOAD_READING] ?: false }

    suspend fun getAutoDownloadReading(): Boolean =
        context.appPreferencesDataStore.data.map { it[Keys.AUTO_DOWNLOAD_READING] ?: false }
            .first()

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun updateKeepScreenOn(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun updateAutoCleanup(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.AUTO_CLEANUP] = enabled
        }
    }

    suspend fun updateAutoDownloadReading(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.AUTO_DOWNLOAD_READING] = enabled
        }
    }

    suspend fun hasSeenTapZoneHint(): Boolean =
        context.appPreferencesDataStore.data.map { it[Keys.HAS_SEEN_TAP_ZONE_HINT] ?: false }
            .first()

    suspend fun markTapZoneHintSeen() {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.HAS_SEEN_TAP_ZONE_HINT] = true
        }
    }
}
