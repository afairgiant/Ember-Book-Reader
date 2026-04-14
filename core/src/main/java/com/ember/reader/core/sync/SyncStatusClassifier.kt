package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryHttpException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.time.Instant

/**
 * Maps a terminal sync [Throwable] to a [SyncStatus]. Pure — extracted so
 * [SyncStatusRepository] stays a thin state holder and the classification
 * rules can be unit-tested in isolation.
 */
internal object SyncStatusClassifier {

    fun classify(error: Throwable, at: Instant): SyncStatus = when (error) {
        is GrimmoryAuthExpiredException -> SyncStatus.AuthExpired(at)
        is GrimmoryHttpException -> SyncStatus.ServerError(
            lastAttemptAt = at,
            statusCode = error.statusCode,
            detail = error.message
        )
        is HttpRequestTimeoutException -> SyncStatus.NetworkError(at, "timeout")
        is IOException -> SyncStatus.NetworkError(at, error.message)
        else -> SyncStatus.ServerError(at, statusCode = null, detail = error.message)
    }
}
