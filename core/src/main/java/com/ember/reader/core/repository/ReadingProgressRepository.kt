package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ReadingProgressDao
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.sync.DeviceIdentity
import com.ember.reader.core.sync.KosyncClient
import com.ember.reader.core.sync.KosyncProgressRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val readingProgressDao: ReadingProgressDao,
    private val kosyncClient: KosyncClient,
    private val deviceIdentity: DeviceIdentity,
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
        locatorJson: String?,
    ) {
        val progress = ReadingProgress(
            bookId = bookId,
            serverId = serverId,
            percentage = percentage,
            locatorJson = locatorJson,
            kosyncProgress = locatorJson,
            lastReadAt = Instant.now(),
            needsSync = serverId != null,
        )
        readingProgressDao.upsert(progress.toEntity())
    }

    suspend fun pushProgress(
        server: Server,
        bookId: String,
        documentHash: String,
    ): Result<Unit> {
        val progress = getByBookId(bookId) ?: return Result.success(Unit)
        val request = KosyncProgressRequest(
            document = documentHash,
            progress = progress.locatorJson ?: "",
            percentage = progress.percentage,
            device = deviceIdentity.deviceName,
            deviceId = deviceIdentity.deviceId,
        )
        return kosyncClient.pushProgress(
            baseUrl = server.url,
            username = server.kosyncUsername,
            password = server.kosyncPassword,
            request = request,
        ).onSuccess {
            readingProgressDao.markSynced(bookId, Instant.now())
        }
    }

    data class RemoteProgressResult(
        val progress: ReadingProgress,
        val deviceName: String?,
    )

    suspend fun pullProgress(
        server: Server,
        bookId: String,
        documentHash: String,
    ): Result<RemoteProgressResult?> = runCatching {
        val remote = kosyncClient.pullProgress(
            baseUrl = server.url,
            username = server.kosyncUsername,
            password = server.kosyncPassword,
            documentHash = documentHash,
        ).getOrNull() ?: return@runCatching null

        val remotePercentage = remote.percentage ?: return@runCatching null

        RemoteProgressResult(
            progress = ReadingProgress(
                bookId = bookId,
                serverId = server.id,
                percentage = remotePercentage,
                locatorJson = remote.progress,
                kosyncProgress = remote.progress,
                lastReadAt = Instant.now(),
                syncedAt = Instant.now(),
                needsSync = false,
            ),
            deviceName = remote.device,
        )
    }

    suspend fun applyRemoteProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    suspend fun syncUnsyncedProgress(server: Server, getDocumentHash: suspend (String) -> String?) {
        val unsynced = readingProgressDao.getUnsyncedProgress(server.id)
        for (entity in unsynced) {
            val hash = getDocumentHash(entity.bookId) ?: continue
            pushProgress(server, entity.bookId, hash).onFailure {
                Timber.w(it, "Failed to sync progress for ${entity.bookId}")
            }
        }
    }
}
