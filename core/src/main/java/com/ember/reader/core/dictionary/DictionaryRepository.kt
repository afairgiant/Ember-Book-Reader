package com.ember.reader.core.dictionary

import com.ember.reader.core.database.dao.DictionaryDao
import com.ember.reader.core.database.entity.DictionaryEntryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Coordinates dictionary lookups: checks local cache first, then delegates
 * to the configured DictionaryProvider. Results are cached for offline access.
 */
@Singleton
class DictionaryRepository @Inject constructor(
    private val dictionaryDao: DictionaryDao,
    private val provider: DictionaryProvider
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(word: String): Result<DictionaryResult> {
        val trimmed = word.trim().lowercase()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Empty word"))

        // Build candidates: original first, then base-form fallbacks for inflections.
        val candidates = buildCandidates(trimmed)

        for (candidate in candidates) {
            // Check local cache first
            val cached = dictionaryDao.findByWord(candidate)
            if (cached != null) {
                Timber.d("Dictionary: cache hit '$candidate' (queried '$trimmed')")
                return runCatching { fromEntity(cached) }
            }
        }

        // No cache hit — try the provider for each candidate in order.
        var lastFailure: Throwable? = null
        for (candidate in candidates) {
            val result = provider.lookup(candidate)
            if (result.isSuccess) {
                val resolved = followFormOf(result.getOrNull()!!, depth = 0)
                dictionaryDao.insert(toEntity(resolved))
                Timber.d("Dictionary: cached '${resolved.word}' from query '$trimmed' (${resolved.definitions.size} definitions)")
                return Result.success(resolved)
            }
            lastFailure = result.exceptionOrNull()
        }
        return Result.failure(lastFailure ?: IllegalStateException("No definition found"))
    }

    /**
     * Many dictionaries return stub entries for inflected forms whose only
     * definitions are pointers like "plural of underestimate" or "past tense
     * of go". When every definition is such a pointer to the same base word,
     * follow the pointer once to get the real entry. Bounded recursion guards
     * against pathological loops.
     */
    private suspend fun followFormOf(result: DictionaryResult, depth: Int): DictionaryResult {
        if (depth >= 2 || result.definitions.isEmpty()) return result

        val realDefs = result.definitions.filter { extractFormOfBase(it.meaning) == null }

        // Some real definitions alongside stubs — drop the stubs and keep the rest.
        if (realDefs.isNotEmpty() && realDefs.size != result.definitions.size) {
            return result.copy(definitions = realDefs)
        }

        // All definitions are form-of stubs — follow the pointer once.
        if (realDefs.isEmpty()) {
            val bases = result.definitions.mapNotNull { extractFormOfBase(it.meaning) }.distinct()
            if (bases.size != 1) return result
            val base = bases.single()
            if (base.equals(result.word, ignoreCase = true)) return result

            val cached = dictionaryDao.findByWord(base.lowercase())
            if (cached != null) return runCatching { fromEntity(cached) }.getOrDefault(result)

            val resolved = provider.lookup(base).getOrNull() ?: return result
            return followFormOf(resolved, depth + 1)
        }

        return result
    }

    /**
     * Detects Wiktionary-style "form of" glosses and returns the base word, or
     * null if the meaning isn't a pure pointer.
     *
     * Examples that match:
     *   "plural of underestimate"
     *   "third-person singular simple present indicative of underestimate"
     *   "simple past tense and past participle of run"
     *   "present participle of make"
     */
    private fun extractFormOfBase(meaning: String): String? {
        val cleaned = meaning.trim().trimEnd('.', ',', ';').lowercase()
        // Must contain a form-of indicator AND end with "of <word>".
        if (FORM_INDICATORS.none { it in cleaned }) return null
        val match = TRAILING_OF_WORD.find(cleaned) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() && it != "the" && it != "a" }
    }

    companion object {
        private val FORM_INDICATORS = listOf(
            "plural", "singular", "past tense", "past participle", "present participle",
            "gerund", "comparative", "superlative", "inflection", "third-person", "third person",
            "simple past", "alternative form", "alternative spelling", "obsolete form"
        )
        private val TRAILING_OF_WORD = Regex("\\bof\\s+([a-z][a-z'\\-]*)$")
    }

    /**
     * Returns the original word followed by plausible base forms produced by
     * stripping common English inflectional suffixes (e.g. possessed -> possess,
     * running -> run, tried -> try, boxes -> box, bigger -> big). Order matters:
     * earlier candidates are preferred. Heuristic only — irregular forms are
     * not handled.
     */
    private fun buildCandidates(word: String): List<String> {
        val out = linkedSetOf(word)

        fun add(stem: String) {
            if (stem.length >= 3 && stem != word) out.add(stem)
        }

        // For suffixes like -ing/-ed/-er/-est, also try restoring a dropped 'e'
        // (liked -> like) and collapsing a doubled consonant (stopped -> stop).
        fun addStemVariants(stem: String) {
            add(stem)
            add(stem + "e")
            if (stem.length >= 2 && stem[stem.length - 1] == stem[stem.length - 2]) {
                add(stem.dropLast(1))
            }
        }

        when {
            word.endsWith("ied") && word.length > 4 -> add(word.dropLast(3) + "y")
            word.endsWith("ies") && word.length > 4 -> add(word.dropLast(3) + "y")
            word.endsWith("ing") && word.length > 4 -> addStemVariants(word.dropLast(3))
            word.endsWith("est") && word.length > 5 -> addStemVariants(word.dropLast(3))
            word.endsWith("ed") && word.length > 3 -> addStemVariants(word.dropLast(2))
            word.endsWith("er") && word.length > 4 -> addStemVariants(word.dropLast(2))
            word.endsWith("es") && word.length > 3 -> {
                // wishes -> wish, but also estimates -> estimate
                add(word.dropLast(2))
                add(word.dropLast(1))
            }
            word.endsWith("s") && !word.endsWith("ss") && word.length > 3 -> add(word.dropLast(1))
        }

        return out.toList()
    }

    private fun toEntity(result: DictionaryResult): DictionaryEntryEntity {
        val defsJson = json.encodeToString(
            result.definitions.map {
                SerializableDefinition(it.partOfSpeech, it.meaning, it.example)
            }
        )
        return DictionaryEntryEntity(
            word = result.word.lowercase(),
            phonetic = result.phonetic,
            definitions = defsJson
        )
    }

    private fun fromEntity(entity: DictionaryEntryEntity): DictionaryResult {
        val defs = json.decodeFromString<List<SerializableDefinition>>(entity.definitions)
        return DictionaryResult(
            word = entity.word,
            phonetic = entity.phonetic,
            definitions = defs.map { Definition(it.partOfSpeech, it.meaning, it.example) }
        )
    }

    @kotlinx.serialization.Serializable
    private data class SerializableDefinition(
        val partOfSpeech: String,
        val meaning: String,
        val example: String? = null
    )
}
