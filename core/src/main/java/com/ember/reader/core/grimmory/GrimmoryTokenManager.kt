package com.ember.reader.core.grimmory

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
import timber.log.Timber

@Singleton
class GrimmoryTokenManager @Inject constructor(
    private val credentialEncryption: CredentialEncryption,
    private val httpClient: HttpClient
) {

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
     * `/api/v1/auth/refresh` and the block is retried once.
     */
    suspend fun <T> withAuth(
        baseUrl: String,
        serverId: Long,
        block: suspend (token: String) -> T
    ): Result<T> = runCatching {
        val token = getAccessToken(serverId)
            ?: error("Not logged in to Grimmory")

        try {
            block(token)
        } catch (e: GrimmoryHttpException) {
            if (e.statusCode == 401) {
                Timber.d("Grimmory: access token expired, refreshing...")
                val refresh = getRefreshToken(serverId)
                    ?: error("No refresh token available")
                val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(GrimmoryRefreshRequest(refresh))
                }
                if (!response.status.isSuccess()) {
                    error("Token refresh failed: ${response.status}")
                }
                val newTokens = response.body<GrimmoryTokens>()
                storeTokens(serverId, newTokens)
                block(newTokens.accessToken)
            } else {
                throw e
            }
        }
    }

    companion object {
        private fun accessTokenKey(serverId: Long) = "grimmory_access_$serverId"
        private fun refreshTokenKey(serverId: Long) = "grimmory_refresh_$serverId"
    }
}
