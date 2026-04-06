package com.ember.reader.core.dictionary.di

import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.dao.DictionaryDao
import com.ember.reader.core.dictionary.DictionaryProvider
import com.ember.reader.core.dictionary.FreeDictionaryApiProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * To swap dictionary sources, change the @Binds binding below.
 * For example, bind an OfflineDictionaryProvider instead of FreeDictionaryApiProvider.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DictionaryModule {

    @Binds
    abstract fun bindDictionaryProvider(impl: FreeDictionaryApiProvider): DictionaryProvider

    companion object {
        @Provides
        fun provideDictionaryDao(database: EmberDatabase): DictionaryDao = database.dictionaryDao()
    }
}
