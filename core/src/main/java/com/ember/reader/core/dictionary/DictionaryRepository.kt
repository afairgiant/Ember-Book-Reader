package com.ember.reader.core.dictionary

import com.ember.reader.core.database.dao.DictionaryDao
import com.ember.reader.core.database.entity.DictionaryEntryEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates dictionary lookups: checks local cache first, then delegates
 * to the configured DictionaryProvider. Results are cached for offline access.
 *
 * To swap the dictionary source, change the DictionaryProvider binding in DictionaryModule.
 */
@Singleton
class DictionaryRepository @Inject constructor(
    private val dictionaryDao: DictionaryDao,
    private val provider: DictionaryProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(word: String): Result<DictionaryResult> {
        val trimmed = word.trim().lowercase()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Empty word"))

        // Check local cache first
        val cached = dictionaryDao.findByWord(trimmed)
        if (cached != null) {
            return runCatching { fromEntity(cached) }
        }

        // Query provider
        val result = provider.lookup(trimmed)
        result.onSuccess { dictResult ->
            // Cache for offline access
            val entity = toEntity(dictResult)
            dictionaryDao.insert(entity)
            Timber.d("Dictionary: cached '${dictResult.word}' (${dictResult.definitions.size} definitions)")
        }
        return result
    }

    private fun toEntity(result: DictionaryResult): DictionaryEntryEntity {
        val defsJson = json.encodeToString(result.definitions.map { SerializableDefinition(it.partOfSpeech, it.meaning, it.example) })
        return DictionaryEntryEntity(
            word = result.word.lowercase(),
            phonetic = result.phonetic,
            definitions = defsJson,
        )
    }

    private fun fromEntity(entity: DictionaryEntryEntity): DictionaryResult {
        val defs = json.decodeFromString<List<SerializableDefinition>>(entity.definitions)
        return DictionaryResult(
            word = entity.word,
            phonetic = entity.phonetic,
            definitions = defs.map { Definition(it.partOfSpeech, it.meaning, it.example) },
        )
    }

    @kotlinx.serialization.Serializable
    private data class SerializableDefinition(
        val partOfSpeech: String,
        val meaning: String,
        val example: String? = null,
    )
}
