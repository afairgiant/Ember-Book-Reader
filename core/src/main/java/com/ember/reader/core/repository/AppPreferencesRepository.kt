package com.ember.reader.core.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

/** The colour buttons, icons and selections are drawn in. EMBER is the app's own orange. */
enum class AppAccent(val displayName: String) {
    EMBER("Ember"),
    RED("Red"),
    AMBER("Amber"),
    BLUE("Blue"),
    TEAL("Teal"),
    GREEN("Green"),
    PURPLE("Purple")
}

@Singleton
class AppPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OLED_BLACK = booleanPreferencesKey("oled_black")
        val ACCENT = stringPreferencesKey("accent")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_SCROLL_SPEED = floatPreferencesKey("auto_scroll_speed")
        val NOTES_INCLUDE_GRIMMORY = booleanPreferencesKey("notes_include_grimmory")
        val AUTO_CLEANUP = booleanPreferencesKey("auto_cleanup")
        val AUTO_DOWNLOAD_READING = booleanPreferencesKey("auto_download_reading")
        val HAS_SEEN_TAP_ZONE_HINT = booleanPreferencesKey("has_seen_tap_zone_hint")
        val SYNC_NOTIFICATIONS = booleanPreferencesKey("sync_notifications")
        val NEW_BOOK_NOTIFICATIONS = booleanPreferencesKey("new_book_notifications")

        /** The newest books last seen on a server; see `SeenBooks`. */
        fun seenNewBooks(serverId: Long) = stringPreferencesKey("seen_new_books_$serverId")
        val SYNC_HIGHLIGHTS = booleanPreferencesKey("sync_highlights")
        val SYNC_BOOKMARKS = booleanPreferencesKey("sync_bookmarks")
        val BOOK_PUBLISHER_STYLES_RESET = booleanPreferencesKey("book_publisher_styles_reset_v1")
    }

    val themeModeFlow: Flow<ThemeMode> = context.appPreferencesDataStore.data
        .map { prefs ->
            prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM
        }

    val oledBlackFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.OLED_BLACK] ?: false }

    val accentFlow: Flow<AppAccent> = context.appPreferencesDataStore.data
        .map { prefs ->
            prefs[Keys.ACCENT]?.let {
                runCatching { AppAccent.valueOf(it) }.getOrNull()
            } ?: AppAccent.EMBER
        }

    val keepScreenOnFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.KEEP_SCREEN_ON] ?: false }

    /** The reader's auto-scroll speed in CSS pixels a second; null until one has been chosen. */
    val autoScrollSpeedFlow: Flow<Float?> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.AUTO_SCROLL_SPEED] }

    /** Whether the notes screen also lists what Grimmory holds. */
    val notesIncludeGrimmoryFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.NOTES_INCLUDE_GRIMMORY] ?: false }

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

    suspend fun updateOledBlack(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.OLED_BLACK] = enabled
        }
    }

    suspend fun updateAccent(accent: AppAccent) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.ACCENT] = accent.name
        }
    }

    suspend fun updateKeepScreenOn(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun updateAutoScrollSpeed(speed: Float) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.AUTO_SCROLL_SPEED] = speed
        }
    }

    suspend fun updateNotesIncludeGrimmory(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.NOTES_INCLUDE_GRIMMORY] = enabled
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

    val syncNotificationsFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.SYNC_NOTIFICATIONS] ?: true }

    suspend fun getSyncNotifications(): Boolean =
        context.appPreferencesDataStore.data.map { it[Keys.SYNC_NOTIFICATIONS] ?: true }.first()

    suspend fun updateSyncNotifications(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.SYNC_NOTIFICATIONS] = enabled
        }
    }

    /** Whether a background sync says so when books have been added to a server. */
    val newBookNotificationsFlow: Flow<Boolean> = context.appPreferencesDataStore.data
        .map { prefs -> prefs[Keys.NEW_BOOK_NOTIFICATIONS] ?: true }

    suspend fun getNewBookNotifications(): Boolean = newBookNotificationsFlow.first()

    suspend fun updateNewBookNotifications(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.NEW_BOOK_NOTIFICATIONS] = enabled
        }
    }

    suspend fun getSeenNewBooks(serverId: Long): String? =
        context.appPreferencesDataStore.data.map { it[Keys.seenNewBooks(serverId)] }.first()

    suspend fun updateSeenNewBooks(serverId: Long, encoded: String) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[Keys.seenNewBooks(serverId)] = encoded
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

    val syncHighlightsFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { it[Keys.SYNC_HIGHLIGHTS] ?: false }

    suspend fun getSyncHighlights(): Boolean =
        context.appPreferencesDataStore.data.first()[Keys.SYNC_HIGHLIGHTS] ?: false

    suspend fun updateSyncHighlights(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[Keys.SYNC_HIGHLIGHTS] = enabled }
    }

    val syncBookmarksFlow: Flow<Boolean> =
        context.appPreferencesDataStore.data.map { it[Keys.SYNC_BOOKMARKS] ?: false }

    suspend fun getSyncBookmarks(): Boolean =
        context.appPreferencesDataStore.data.first()[Keys.SYNC_BOOKMARKS] ?: false

    suspend fun updateSyncBookmarks(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[Keys.SYNC_BOOKMARKS] = enabled }
    }

    suspend fun hasMigratedBookPublisherStyles(): Boolean =
        context.appPreferencesDataStore.data.first()[Keys.BOOK_PUBLISHER_STYLES_RESET] ?: false

    suspend fun markBookPublisherStylesMigrated() {
        context.appPreferencesDataStore.edit { it[Keys.BOOK_PUBLISHER_STYLES_RESET] = true }
    }
}
