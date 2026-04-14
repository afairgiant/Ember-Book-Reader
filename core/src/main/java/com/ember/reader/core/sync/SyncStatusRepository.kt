package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.SyncStatusDao
import com.ember.reader.core.database.entity.SyncStatusEntity
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Per-server sync health, reported by sync entry points and observed by UI.
 *
 * The repository is a thin state holder: it does not trigger sync itself,
 * it only records outcomes. Callers are responsible for calling
 * [reportSuccess] or [reportFailure] at the terminal edge of each sync
 * attempt so the state reflects the most recent result.
 *
 * Status is keyed by `serverId` (the local Ember row id), not by URL —
 * deleting and re-adding the same server should start fresh. State is
 * persisted in the `sync_status` Room table, so the UI sees the last
 * known outcome immediately on launch instead of starting empty.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    private val syncStatusDao: SyncStatusDao,
    private val clock: Clock
) {

    val statuses: Flow<Map<Long, SyncStatus>> = syncStatusDao.observeAll()
        .map { entities -> entities.associate { it.serverId to it.toDomain() } }
        .distinctUntilChanged()

    fun observe(serverId: Long): Flow<SyncStatus> = syncStatusDao.observeByServer(serverId)
        .map { it?.toDomain() ?: SyncStatus.Unknown }
        .distinctUntilChanged()

    suspend fun get(serverId: Long): SyncStatus =
        syncStatusDao.getByServer(serverId)?.toDomain() ?: SyncStatus.Unknown

    suspend fun reportSuccess(serverId: Long) {
        syncStatusDao.upsert(SyncStatus.Ok(Instant.now(clock)).toEntity(serverId))
    }

    suspend fun reportFailure(serverId: Long, error: Throwable) {
        val status = SyncStatusClassifier.classify(error, Instant.now(clock))
        syncStatusDao.upsert(status.toEntity(serverId))
    }

    /** Drop tracked state for a server — call from [com.ember.reader.core.repository.ServerRepository.delete]. */
    suspend fun clear(serverId: Long) {
        syncStatusDao.deleteByServer(serverId)
    }
}

private fun SyncStatus.toEntity(serverId: Long): SyncStatusEntity = when (this) {
    is SyncStatus.Ok -> SyncStatusEntity(serverId, TYPE_OK, lastSuccessAt.toEpochMilli())
    is SyncStatus.AuthExpired -> SyncStatusEntity(serverId, TYPE_AUTH_EXPIRED, lastAttemptAt.toEpochMilli())
    is SyncStatus.NetworkError -> SyncStatusEntity(
        serverId = serverId,
        type = TYPE_NETWORK_ERROR,
        lastAttemptAt = lastAttemptAt.toEpochMilli(),
        detail = detail
    )
    is SyncStatus.ServerError -> SyncStatusEntity(
        serverId = serverId,
        type = TYPE_SERVER_ERROR,
        lastAttemptAt = lastAttemptAt.toEpochMilli(),
        statusCode = statusCode,
        detail = detail
    )
    // Unknown is represented by the absence of a row and should never be persisted.
    SyncStatus.Unknown -> error("Cannot persist SyncStatus.Unknown — delete the row instead.")
}

private fun SyncStatusEntity.toDomain(): SyncStatus {
    val instant = Instant.ofEpochMilli(lastAttemptAt)
    return when (type) {
        TYPE_OK -> SyncStatus.Ok(instant)
        TYPE_AUTH_EXPIRED -> SyncStatus.AuthExpired(instant)
        TYPE_NETWORK_ERROR -> SyncStatus.NetworkError(instant, detail)
        TYPE_SERVER_ERROR -> SyncStatus.ServerError(instant, statusCode, detail)
        // Forward-compat: an unknown type string (e.g. from a newer app version
        // rolled back) shouldn't crash — degrade to generic ServerError.
        else -> SyncStatus.ServerError(instant, statusCode, detail)
    }
}

private const val TYPE_OK = "Ok"
private const val TYPE_AUTH_EXPIRED = "AuthExpired"
private const val TYPE_NETWORK_ERROR = "NetworkError"
private const val TYPE_SERVER_ERROR = "ServerError"
