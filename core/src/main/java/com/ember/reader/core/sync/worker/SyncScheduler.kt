package com.ember.reader.core.sync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ember.reader.core.model.SyncFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun applyFrequency(frequency: SyncFrequency) {
        val interval = frequency.intervalMinutes
        if (interval != null) {
            schedulePeriodicSync(interval)
        } else {
            cancelPeriodicSync()
        }
    }

    fun syncNow() {
        val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueue(request)
    }

    private fun schedulePeriodicSync(intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
    }
}
