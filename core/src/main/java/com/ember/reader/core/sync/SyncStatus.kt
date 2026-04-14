package com.ember.reader.core.sync

import java.time.Instant

/**
 * Per-server sync health, exposed to the UI via [SyncStatusRepository].
 *
 * - [Unknown]: no sync attempt recorded yet (e.g. freshly added server).
 * - [Ok]: the most recent sync attempt succeeded.
 * - [AuthExpired]: the user's credentials are dead and re-login is required;
 *   callers should already have cleared stored tokens before reporting this.
 * - [NetworkError]: a connectivity-shaped failure (offline, DNS, timeout).
 *   Typically transient — retry on next attempt.
 * - [ServerError]: the remote returned a non-success HTTP status or threw
 *   an unexpected exception. [statusCode] is null when the source wasn't
 *   HTTP (e.g. a serialization error).
 */
sealed interface SyncStatus {
    data object Unknown : SyncStatus

    data class Ok(val lastSuccessAt: Instant) : SyncStatus

    data class AuthExpired(val lastAttemptAt: Instant) : SyncStatus

    data class NetworkError(
        val lastAttemptAt: Instant,
        val detail: String? = null
    ) : SyncStatus

    data class ServerError(
        val lastAttemptAt: Instant,
        val statusCode: Int? = null,
        val detail: String? = null
    ) : SyncStatus
}

/**
 * True when the status represents an unhealthy server the user should know
 * about. [Unknown] and [Ok] are considered healthy; everything else is not.
 */
val SyncStatus.isUnhealthy: Boolean
    get() = when (this) {
        is SyncStatus.Unknown, is SyncStatus.Ok -> false
        is SyncStatus.AuthExpired,
        is SyncStatus.NetworkError,
        is SyncStatus.ServerError
        -> true
    }
