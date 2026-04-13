package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ReadingProgressDao
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.sync.DeviceIdentity
import com.ember.reader.core.sync.KosyncClient
import com.ember.reader.core.sync.KosyncProgressRequest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val readingProgressDao: ReadingProgressDao,
    private val kosyncClient: KosyncClient,
    private val deviceIdentity: DeviceIdentity
) {

    fun observeAll(): Flow<List<ReadingProgress>> =
        readingProgressDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeByBookId(bookId: String): Flow<ReadingProgress?> =
        readingProgressDao.observeByBookId(bookId).map { it?.toDomain() }

    suspend fun getByBookId(bookId: String): ReadingProgress? =
        readingProgressDao.getByBookId(bookId)?.toDomain()

    suspend fun updateProgress(
        bookId: String,
        serverId: Long?,
        percentage: Float,
        locatorJson: String?
    ) {
        val progress = ReadingProgress(
            bookId = bookId,
            serverId = serverId,
            percentage = percentage,
            locatorJson = locatorJson,
            kosyncProgress = locatorJson,
            lastReadAt = Instant.now(),
            needsSync = serverId != null
        )
        readingProgressDao.upsert(progress.toEntity())
    }

    suspend fun pushKosyncProgress(
        server: Server,
        bookId: String,
        documentHash: String
    ): Result<Unit> {
        val progress = getByBookId(bookId) ?: return Result.success(Unit)
        val request = KosyncProgressRequest(
            document = documentHash,
            positionData = progress.locatorJson ?: "",
            percentage = progress.percentage,
            device = deviceIdentity.deviceName,
            deviceId = deviceIdentity.deviceId
        )
        return kosyncClient.pushProgress(
            baseUrl = server.url,
            username = server.kosyncUsername,
            password = server.kosyncPassword,
            request = request
        ).onSuccess {
            readingProgressDao.markSynced(bookId, Instant.now())
        }
    }

    suspend fun markSynced(bookId: String) {
        readingProgressDao.markSynced(bookId, Instant.now())
    }

    data class KosyncProgressResult(
        val progress: ReadingProgress,
        val deviceName: String?
    )

    suspend fun pullKosyncProgress(
        server: Server,
        bookId: String,
        documentHash: String
    ): Result<KosyncProgressResult?> = runCatching {
        val remote = kosyncClient.pullProgress(
            baseUrl = server.url,
            username = server.kosyncUsername,
            password = server.kosyncPassword,
            documentHash = documentHash
        ).getOrNull() ?: return@runCatching null

        val remotePercentage = remote.percentage ?: return@runCatching null

        KosyncProgressResult(
            progress = ReadingProgress(
                bookId = bookId,
                serverId = server.id,
                percentage = remotePercentage,
                locatorJson = remote.positionData,
                kosyncProgress = remote.positionData,
                lastReadAt = Instant.now(),
                syncedAt = Instant.now(),
                needsSync = false
            ),
            deviceName = remote.device
        )
    }

    suspend fun applyRemoteProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    /**
     * Pulls remote progress for all downloaded books on a server.
     * Only updates local progress if the remote is newer (higher percentage).
     */
    suspend fun pullKosyncProgressForAllBooks(server: Server, books: List<Pair<String, String>>) {
        for ((bookId, fileHash) in books) {
            runCatching {
                val result = pullKosyncProgress(server, bookId, fileHash)
                val remote = result.getOrNull() ?: return@runCatching

                val local = getByBookId(bookId)
                val localPercentage = local?.percentage ?: 0f

                if (remote.progress.percentage > localPercentage) {
                    applyRemoteProgress(remote.progress)
                    Timber.d("Pulled progress for $bookId: ${(remote.progress.percentage * 100).toInt()}%")
                }
            }.onFailure {
                Timber.w(it, "Failed to pull progress for book $bookId")
            }
        }
    }

    suspend fun pushUnsyncedKosyncProgress(
        server: Server,
        getDocumentHash: suspend (String) -> String?
    ) {
        val unsynced = readingProgressDao.getUnsyncedProgress(server.id)
        for (entity in unsynced) {
            val hash = getDocumentHash(entity.bookId) ?: continue
            pushKosyncProgress(server, entity.bookId, hash).onFailure {
                Timber.w(it, "Failed to sync progress for ${entity.bookId}")
            }
        }
    }
}
