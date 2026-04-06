package com.ember.reader.core.dictionary

/**
 * Abstraction for dictionary data sources. Implementations can be:
 * - API-based (FreeDictionaryApiProvider)
 * - Bundled offline database
 * - Downloadable dictionary packs
 *
 * Swap the implementation in DictionaryModule without touching UI or repository.
 */
interface DictionaryProvider {
    suspend fun lookup(word: String): Result<DictionaryResult>
}
