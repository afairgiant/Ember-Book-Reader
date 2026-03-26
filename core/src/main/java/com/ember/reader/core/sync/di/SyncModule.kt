package com.ember.reader.core.sync.di

import android.content.Context
import com.ember.reader.core.sync.DeviceIdentity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideDeviceIdentity(@ApplicationContext context: Context): DeviceIdentity =
        DeviceIdentity(context)
}
