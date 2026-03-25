package com.ember.reader.core.readium.di

import android.content.Context
import com.ember.reader.core.readium.BookOpener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReadiumModule {

    @Provides
    @Singleton
    fun provideBookOpener(@ApplicationContext context: Context): BookOpener =
        BookOpener(context)
}
