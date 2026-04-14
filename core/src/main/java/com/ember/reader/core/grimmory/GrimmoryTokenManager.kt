package com.ember.reader.core.grimmory

import com.ember.reader.core.coroutine.runCatchingCancellable
import com.ember.reader.core.network.CredentialEncryption
import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class GrimmoryTokenManager @Inject constructor(
    private val credentialEncryption: CredentialEncryption,
    private val httpClient: HttpClient
) {

    private val refreshMutex = Mutex()

    fun getAccessToken(serverId: Long): String? =
        credentialEncryption.getPassword(accessTokenKey(serverId))

    fun getRefreshToken(serverId: Long): String? =
        credentialEncryption.getPassword(refreshTokenKey(serverId))

    fun storeTokens(serverId: Long, tokens: GrimmoryTokens) {
        credentialEncryption.storePassword(accessTokenKey(serverId), tokens.accessToken)
        credentialEncryption.storePassword(refreshTokenKey(serverId), tokens.refreshToken)
    }

    fun isLoggedIn(serverId: Long): Boolean = getAccessToken(serverId) != null

    fun logout(serverId: Long) {
        credentialEncryption.removePassword(accessTokenKey(serverId))
        credentialEncryption.removePassword(refreshTokenKey(serverId))
    }

    /**
     * Executes [block] with a valid access token. If the block throws a
     * [GrimmoryHttpException] with status 401, the token is refreshed via
     * `/api/v1/auth/refresh` and the block is retried once. Concurrent refresh
     * attempts are coalesced so Grimmory's rotating refresh tokens don't race.
     *
     * Fails with [GrimmoryAuthExpiredException] when the server is not logged
     * in, the refresh token is missing, or the refresh endpoint rejects our
     * credentials (400/401/403). Stored tokens are cleared before the
     * exception is thrown so the UI can route to the login screen.
     */
    suspend fun <T> withAuth(
        baseUrl: String,
        serverId: Long,
        block: suspend (token: String) -> T
    ): Result<T> = runCatchingCancellable {
        val initialToken = getAccessToken(serverId)
            ?: throw GrimmoryAuthExpiredException(serverId, "Not logged in to Grimmory")

        try {
            block(initialToken)
        } catch (e: GrimmoryHttpException) {
            if (e.statusCode != 401) throw e
            val freshToken = refreshAccessToken(baseUrl, serverId, expiredToken = initialToken)
            block(freshToken)
        }
    }

    /**
     * Returns a usable access token for [serverId], either by refreshing
     * [expiredToken] or by reusing one a concurrent caller already fetched.
     *
     * The mutex guards both steps so that only one HTTP refresh is ever in
     * flight per instance. Inside the lock we re-read the stored token: if a
     * peer rotated it while we were waiting, we skip the HTTP call entirely.
     */
    private suspend fun refreshAccessToken(
        baseUrl: String,
        serverId: Long,
        expiredToken: String
    ): String = refreshMutex.withLock {
        val currentToken = getAccessToken(serverId)
        if (currentToken != null && currentToken != expiredToken) {
            Timber.d("Grimmory: reusing token refreshed by concurrent caller")
            return@withLock currentToken
        }

        val refresh = getRefreshToken(serverId)
        if (refresh == null) {
            logout(serverId)
            throw GrimmoryAuthExpiredException(serverId, "No refresh token available")
        }

        Timber.d("Grimmory: refreshing access token for server $serverId")
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(GrimmoryRefreshRequest(refresh))
        }

        when {
            response.status.isSuccess() -> {
                val newTokens = response.body<GrimmoryTokens>()
                storeTokens(serverId, newTokens)
                newTokens.accessToken
            }
            response.status.value in AUTH_REJECT_STATUSES -> {
                // Grimmory returns 400 for revoked/expired refresh tokens. Clear
                // credentials so the UI can prompt for re-login instead of looping.
                Timber.w("Grimmory: refresh rejected (${response.status}), logging out server $serverId")
                logout(serverId)
                throw GrimmoryAuthExpiredException(
                    serverId,
                    "Grimmory refresh rejected: ${response.status}"
                )
            }
            else -> {
                // 5xx or unexpected status — likely transient. Keep tokens so
                // the next attempt can retry.
                throw GrimmoryHttpException(
                    response.status.value,
                    "Token refresh failed: ${response.status}"
                )
            }
        }
    }

    companion object {
        private val AUTH_REJECT_STATUSES = setOf(400, 401, 403)
        private fun accessTokenKey(serverId: Long) = "grimmory_access_$serverId"
        private fun refreshTokenKey(serverId: Long) = "grimmory_refresh_$serverId"
    }
}
