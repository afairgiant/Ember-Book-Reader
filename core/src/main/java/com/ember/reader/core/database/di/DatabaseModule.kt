package com.ember.reader.core.database.di

import android.content.Context
import androidx.room.Room
import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.dao.BookReaderPreferencesDao
import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.dao.CatalogEntryPreferenceDao
import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.dao.ReadingProgressDao
import com.ember.reader.core.database.dao.ReadingSessionDao
import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.dao.SyncStatusDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EmberDatabase = Room.databaseBuilder(
        context,
        EmberDatabase::class.java,
        "ember.db"
    )
        .addMigrations(EmberDatabase.MIGRATION_1_2, EmberDatabase.MIGRATION_2_3, EmberDatabase.MIGRATION_3_4, EmberDatabase.MIGRATION_4_5, EmberDatabase.MIGRATION_5_6, EmberDatabase.MIGRATION_6_7, EmberDatabase.MIGRATION_7_8, EmberDatabase.MIGRATION_8_9, EmberDatabase.MIGRATION_9_10, EmberDatabase.MIGRATION_10_11, EmberDatabase.MIGRATION_11_12, EmberDatabase.MIGRATION_12_13, EmberDatabase.MIGRATION_13_14)
        .build()

    @Provides
    fun provideServerDao(database: EmberDatabase): ServerDao = database.serverDao()

    @Provides
    fun provideBookDao(database: EmberDatabase): BookDao = database.bookDao()

    @Provides
    fun provideReadingProgressDao(database: EmberDatabase): ReadingProgressDao =
        database.readingProgressDao()

    @Provides
    fun provideBookmarkDao(database: EmberDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideHighlightDao(database: EmberDatabase): HighlightDao = database.highlightDao()

    @Provides
    fun provideReadingSessionDao(database: EmberDatabase): ReadingSessionDao =
        database.readingSessionDao()

    @Provides
    fun provideBookReaderPreferencesDao(database: EmberDatabase): BookReaderPreferencesDao =
        database.bookReaderPreferencesDao()

    @Provides
    fun provideCatalogEntryPreferenceDao(database: EmberDatabase): CatalogEntryPreferenceDao =
        database.catalogEntryPreferenceDao()

    @Provides
    fun provideSyncStatusDao(database: EmberDatabase): SyncStatusDao = database.syncStatusDao()
}
