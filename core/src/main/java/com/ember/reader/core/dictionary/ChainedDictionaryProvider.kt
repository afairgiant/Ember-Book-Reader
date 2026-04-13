package com.ember.reader.core.dictionary

import timber.log.Timber

/**
 * Tries each provider in order until one returns a successful result.
 * Used to fall back from a fast/clean source to a higher-coverage one.
 */
class ChainedDictionaryProvider(
    private val providers: List<DictionaryProvider>
) : DictionaryProvider {

    override suspend fun lookup(word: String): Result<DictionaryResult> {
        var lastFailure: Throwable? = null
        for ((index, provider) in providers.withIndex()) {
            val result = provider.lookup(word)
            if (result.isSuccess) {
                if (index > 0) {
                    Timber.d("Dictionary: provider #$index (${provider::class.simpleName}) resolved '$word'")
                }
                return result
            }
            lastFailure = result.exceptionOrNull()
        }
        return Result.failure(lastFailure ?: IllegalStateException("No definition found"))
    }
}
