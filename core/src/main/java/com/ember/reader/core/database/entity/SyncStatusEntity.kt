package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stored per-server sync health so the UI can surface the most recent
 * outcome across app restarts. One row per server; the row is overwritten
 * on each success/failure report and deleted when the server is removed.
 *
 * [type] is the serialized [com.ember.reader.core.sync.SyncStatus] subtype
 * name ("Ok", "AuthExpired", "NetworkError", "ServerError"). The domain
 * `Unknown` state is represented by the absence of a row.
 */
@Entity(tableName = "sync_status")
data class SyncStatusEntity(
    @PrimaryKey
    val serverId: Long,
    val type: String,
    val lastAttemptAt: Long,
    val statusCode: Int? = null,
    val detail: String? = null
)
