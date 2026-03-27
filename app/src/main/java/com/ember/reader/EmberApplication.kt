package com.ember.reader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.ui.common.NotificationHelper
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.SyncPreferencesRepository
import com.ember.reader.core.sync.worker.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ember.reader.ui.common.DevLog
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class EmberApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var syncPreferencesRepository: SyncPreferencesRepository

    @Inject
    lateinit var appPreferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var bookRepository: BookRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(DevLog.tree)
        NotificationHelper.createChannels(this)
        initializeSync()
        runAutoCleanup()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("cover_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // Always cache, even if server says not to
            .build()
    }

    private fun initializeSync() {
        applicationScope.launch {
            val frequency = syncPreferencesRepository.syncFrequencyFlow.first()
            syncScheduler.applyFrequency(frequency)
        }
    }

    private fun runAutoCleanup() {
        applicationScope.launch {
            val enabled = appPreferencesRepository.autoCleanupFlow.first()
            if (enabled) {
                bookRepository.cleanupOldDownloads()
            }
        }
    }
}
