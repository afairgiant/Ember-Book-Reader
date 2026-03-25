package com.ember.reader.core.opds.di

import com.ember.reader.core.opds.OpdsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OpdsModule {

    @Provides
    @Singleton
    fun provideOpdsClient(httpClient: HttpClient): OpdsClient =
        OpdsClient(httpClient)
}
