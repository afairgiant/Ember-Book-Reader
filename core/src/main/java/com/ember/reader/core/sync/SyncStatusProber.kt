package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.repository.ServerRepository
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * Runs a lightweight authenticated ping against each configured server so
 * the UI has current health information without waiting for an actual
 * push/pull/background-sync event. Meant to be invoked from screens that
 * need fresh per-server status (today: the Browse screen on load).
 *
 * Strategy per server type:
 * - **Grimmory**: [GrimmoryAppClient.getCurrentUser] — flows through
 *   `withAuth`, so AuthExpired is surfaced cleanly.
 * - **OPDS-only**: [OpdsClient.testConnection] against the catalog URL
 *   using the server's stored Basic credentials.
 *
 * Each probe's outcome is reported to [SyncStatusRepository] via the same
 * classifier that real sync paths use, so the dot/banner reflects the
 * probe result identically to a real failure.
 */
@Singleton
class SyncStatusProber @Inject constructor(
    private val grimmoryAppClient: GrimmoryAppClient,
    private val opdsClient: OpdsClient,
    private val syncStatusRepository: SyncStatusRepository,
    // Lazy to break the ServerRepository ↔ (sync internals) dependency cycle Hilt
    // would otherwise see; permissions persistence only fires on successful probe.
    private val serverRepository: Lazy<ServerRepository>
) {

    suspend fun probeAll(servers: List<Server>): Unit = coroutineScope {
        servers.map { server -> async { probe(server) } }.awaitAll()
    }

    suspend fun probe(server: Server) {
        Timber.d("SyncStatusProber: probing server ${server.id} (${server.name})")
        val result: Result<Unit>? = if (server.isGrimmory) {
            grimmoryAppClient.getCurrentUser(server.url, server.id)
                .onSuccess { user ->
                    runCatching { serverRepository.get().persistGrimmoryPermissions(server.id, user) }
                        .onFailure { Timber.w(it, "SyncStatusProber: persist permissions failed for server ${server.id}") }
                }
                .map { }
        } else {
            probeOpds(server)
        }
        // A null result means we couldn't meaningfully probe (e.g. OPDS with
        // no stored credentials) — leave any previously recorded status alone
        // rather than flipping to a misleading Ok.
        result?.fold(
            onSuccess = { syncStatusRepository.reportSuccess(server.id) },
            onFailure = { syncStatusRepository.reportFailure(server.id, it) }
        )
    }

    private suspend fun probeOpds(server: Server): Result<Unit>? {
        if (server.opdsUsername.isBlank() || server.opdsPassword.isBlank()) return null
        return opdsClient.testConnection(server.url, server.opdsUsername, server.opdsPassword)
            .map { }
    }
}
