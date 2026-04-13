package com.ember.reader.core.dictionary.di

import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.dao.DictionaryDao
import com.ember.reader.core.dictionary.ChainedDictionaryProvider
import com.ember.reader.core.dictionary.DictionaryProvider
import com.ember.reader.core.dictionary.FreeDictionaryApiProvider
import com.ember.reader.core.dictionary.WiktionaryProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires up the dictionary provider chain. Wiktionary is tried first for its
 * much broader coverage; Free Dictionary API is the fallback for the rarer
 * cases where Wiktionary misses but also brings phonetics to the table.
 */
@Module
@InstallIn(SingletonComponent::class)
object DictionaryModule {

    @Provides
    @Singleton
    fun provideDictionaryProvider(
        wiktionary: WiktionaryProvider,
        freeDictionary: FreeDictionaryApiProvider
    ): DictionaryProvider = ChainedDictionaryProvider(
        providers = listOf(wiktionary, freeDictionary)
    )

    @Provides
    fun provideDictionaryDao(database: EmberDatabase): DictionaryDao = database.dictionaryDao()
}
