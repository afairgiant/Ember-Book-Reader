package com.ember.reader.core.repository

import com.ember.reader.core.coroutine.runCatchingCancellable
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
        locatorJson: String?,
        /** The real CFI of this same position. Null clears any CFI saved for an earlier one. */
        cfi: String? = null,
    ) {
        val progress = ReadingProgress(
            bookId = bookId,
            serverId = serverId,
            percentage = percentage,
            locatorJson = locatorJson,
            kosyncProgress = locatorJson,
            cfi = cfi,
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
    ): Result<KosyncProgressResult?> = runCatchingCancellable {
        val remote = kosyncClient.pullProgress(
            baseUrl = server.url,
            username = server.kosyncUsername,
            password = server.kosyncPassword,
            documentHash = documentHash
        ).getOrThrow() ?: return@runCatchingCancellable null

        val remotePercentage = remote.percentage ?: return@runCatchingCancellable null

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
}
