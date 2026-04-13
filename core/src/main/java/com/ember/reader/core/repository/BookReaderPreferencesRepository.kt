package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.BookReaderPreferencesDao
import com.ember.reader.core.database.entity.BookReaderPreferencesEntity
import com.ember.reader.core.model.ReaderPreferences
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Coordinates per-book reader preference overrides with the global defaults.
 *
 * Resolution rule: if a row exists in [BookReaderPreferencesDao] for the given
 * book, that row's deserialized [ReaderPreferences] is used. Otherwise the
 * book inherits the current global defaults from [ReaderPreferencesRepository].
 *
 * Writes from inside the reader go through [saveOverride], which forks the
 * book away from global defaults at that moment. [clearOverride] deletes the
 * row, restoring inheritance.
 */
@Singleton
class BookReaderPreferencesRepository @Inject constructor(
    private val dao: BookReaderPreferencesDao,
    private val globalRepo: ReaderPreferencesRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeEffective(bookId: String): Flow<ReaderPreferences> =
        combine(dao.observe(bookId), globalRepo.preferencesFlow) { override, global ->
            override?.toDomain() ?: global
        }

    fun observeHasOverride(bookId: String): Flow<Boolean> = dao.observe(bookId).map { it != null }

    suspend fun saveOverride(bookId: String, prefs: ReaderPreferences) {
        dao.upsert(
            BookReaderPreferencesEntity(
                bookId = bookId,
                preferencesJson = json.encodeToString(prefs),
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun clearOverride(bookId: String) {
        dao.delete(bookId)
    }

    private fun BookReaderPreferencesEntity.toDomain(): ReaderPreferences =
        runCatching { json.decodeFromString<ReaderPreferences>(preferencesJson) }
            .onFailure {
                Timber.w(it, "Failed to decode book preferences for $bookId; falling back to defaults")
            }
            .getOrDefault(ReaderPreferences())
}
