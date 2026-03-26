package com.ember.reader.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ember.reader.core.repository.BookRepository
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
    private val bookRepository: BookRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker: starting progress sync")

        val servers = serverRepository.getAll()
        if (servers.isEmpty()) return Result.success()

        for (server in servers) {
            if (server.kosyncUsername.isBlank()) continue

            // Push local changes first
            readingProgressRepository.syncUnsyncedProgress(server) { bookId ->
                bookRepository.getById(bookId)?.fileHash
            }

            // Pull remote progress for all downloaded books
            val downloadedBooks = bookRepository.getDownloadedBooksForServer(server.id)
            if (downloadedBooks.isNotEmpty()) {
                val bookHashPairs = downloadedBooks.mapNotNull { book ->
                    val hash = book.fileHash ?: return@mapNotNull null
                    book.id to hash
                }
                readingProgressRepository.pullProgressForAllBooks(server, bookHashPairs)
                Timber.d("SyncWorker: pulled progress for ${bookHashPairs.size} books on ${server.name}")
            }
        }

        Timber.d("SyncWorker: sync complete")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "ember_progress_sync"
    }
}
