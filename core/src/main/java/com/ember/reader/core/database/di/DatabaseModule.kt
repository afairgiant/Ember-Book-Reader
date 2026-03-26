package com.ember.reader.core.database.di

import android.content.Context
import androidx.room.Room
import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.dao.BookmarkDao
import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.dao.ReadingProgressDao
import com.ember.reader.core.database.dao.ServerDao
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
    fun provideDatabase(@ApplicationContext context: Context): EmberDatabase =
        Room.databaseBuilder(
            context,
            EmberDatabase::class.java,
            "ember.db",
        )
            .addMigrations(EmberDatabase.MIGRATION_1_2)
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
}
