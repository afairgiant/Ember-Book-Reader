package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.BookReaderPreferencesDao
import com.ember.reader.core.model.ReaderPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * One-time migration that disables `publisherStyles` on every existing per-book
 * preference override. Older builds defaulted publisher styles to `true`, which
 * made Readium ignore the user's text alignment, line height, and hyphenation.
 * Because the in-reader settings sheet always writes a per-book override, these
 * rows shadow the global default and must be rewritten directly.
 *
 * All other per-book settings (font, theme, margins, tap zones, ...) are
 * preserved. Guarded by [AppPreferencesRepository.hasMigratedBookPublisherStyles]
 * so it runs at most once.
 */
@Singleton
class BookReaderPreferencesPublisherStylesMigration @Inject constructor(
    private val dao: BookReaderPreferencesDao,
    private val appPreferencesRepository: AppPreferencesRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun migrateIfNeeded() {
        if (appPreferencesRepository.hasMigratedBookPublisherStyles()) return
        dao.getAll().forEach { entity ->
            val migrated = disablePublisherStyles(entity.preferencesJson)
            if (migrated != null && migrated != entity.preferencesJson) {
                dao.upsert(entity.copy(preferencesJson = migrated))
            }
        }
        appPreferencesRepository.markBookPublisherStylesMigrated()
    }

    /**
     * Returns [preferencesJson] re-serialized with `publisherStyles = false`, or
     * `null` if it cannot be parsed (leave the row untouched in that case).
     */
    internal fun disablePublisherStyles(preferencesJson: String): String? =
        runCatching {
            val prefs = json.decodeFromString<ReaderPreferences>(preferencesJson)
            json.encodeToString(prefs.copy(publisherStyles = false))
        }.onFailure {
            Timber.w(it, "Failed to migrate per-book publisher styles; leaving row unchanged")
        }.getOrNull()
}
