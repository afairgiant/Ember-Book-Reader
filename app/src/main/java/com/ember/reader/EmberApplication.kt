package com.ember.reader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ember.reader.core.network.CoverAuthInterceptor
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.repository.SyncPreferencesRepository
import com.ember.reader.core.sync.worker.SyncScheduler
import okhttp3.OkHttpClient
import com.ember.reader.ui.common.DevLog
import com.ember.reader.ui.common.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

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

    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var coverAuthInterceptor: CoverAuthInterceptor

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
        observeServersForCoverAuth()
        refreshGrimmoryPermissions()
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(coverAuthInterceptor)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
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

    private fun observeServersForCoverAuth() {
        applicationScope.launch {
            serverRepository.observeAll().collect { servers ->
                coverAuthInterceptor.updateServers(servers)
            }
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

    /**
     * Refresh per-user permission flags (e.g. [ServerRepository.refreshGrimmoryPermissions])
     * for every Grimmory server. Runs in the background so it doesn't delay startup, and
     * silently tolerates any network failure — the cached flag value is kept on error.
     */
    private fun refreshGrimmoryPermissions() {
        applicationScope.launch {
            runCatching {
                val servers = serverRepository.getAll()
                servers.filter { it.isGrimmory }.forEach { server ->
                    serverRepository.refreshGrimmoryPermissions(server.id)
                }
            }.onFailure { e ->
                Timber.w(e, "Failed to refresh Grimmory permissions on startup")
            }
        }
    }
}
