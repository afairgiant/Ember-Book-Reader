package com.ember.reader.core.network.di

import android.content.Context
import com.ember.reader.core.network.CredentialEncryption
import com.ember.reader.core.network.HttpClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClientFactory.create()

    @Provides
    @Singleton
    fun provideCredentialEncryption(@ApplicationContext context: Context): CredentialEncryption =
        CredentialEncryption(context)
}
