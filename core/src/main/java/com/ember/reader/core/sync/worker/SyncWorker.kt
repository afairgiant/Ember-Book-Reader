package com.ember.reader.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ServerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val serverRepository: ServerRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookDao: BookDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker: starting progress sync")

        val servers = serverRepository.getAll()
        if (servers.isEmpty()) return Result.success()

        for (server in servers) {
            if (server.kosyncUsername.isBlank()) continue

            readingProgressRepository.syncUnsyncedProgress(server) { bookId ->
                bookDao.getById(bookId)?.fileHash
            }
        }

        Timber.d("SyncWorker: sync complete")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ember_progress_sync"
    }
}
