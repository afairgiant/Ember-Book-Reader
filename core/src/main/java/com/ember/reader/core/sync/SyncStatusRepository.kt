package com.ember.reader.core.sync

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Per-server sync health, reported by sync entry points and observed by UI.
 *
 * The repository is a thin state holder: it does not trigger sync itself,
 * it only records outcomes. Callers are responsible for calling
 * [reportSuccess] or [reportFailure] at the terminal edge of each sync
 * attempt so the state reflects the most recent result.
 *
 * Status is keyed by `serverId` (the local Ember row id), not by URL —
 * deleting and re-adding the same server should start fresh.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    private val clock: Clock
) {

    private val _statuses = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())

    val statuses: StateFlow<Map<Long, SyncStatus>> = _statuses.asStateFlow()

    fun observe(serverId: Long): Flow<SyncStatus> = statuses
        .map { it[serverId] ?: SyncStatus.Unknown }
        .distinctUntilChanged()

    fun get(serverId: Long): SyncStatus = _statuses.value[serverId] ?: SyncStatus.Unknown

    fun reportSuccess(serverId: Long) {
        val now = Instant.now(clock)
        _statuses.update { it + (serverId to SyncStatus.Ok(now)) }
    }

    fun reportFailure(serverId: Long, error: Throwable) {
        val status = SyncStatusClassifier.classify(error, Instant.now(clock))
        _statuses.update { it + (serverId to status) }
    }

    /** Drop tracked state for a server — e.g. on delete or explicit logout. */
    fun clear(serverId: Long) {
        _statuses.update { it - serverId }
    }
}
