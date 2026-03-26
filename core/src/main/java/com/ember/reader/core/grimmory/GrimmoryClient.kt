package com.ember.reader.core.grimmory

import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrimmoryClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager,
) {

    suspend fun checkHealth(baseUrl: String): Boolean = runCatching {
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/healthcheck")
        response.status.isSuccess()
    }.getOrDefault(false)

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<GrimmoryTokens> = runCatching {
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(GrimmoryLoginRequest(username, password))
        }
        if (!response.status.isSuccess()) {
            error("Login failed: ${response.status}")
        }
        response.body<GrimmoryTokens>()
    }

    suspend fun refreshToken(
        baseUrl: String,
        refreshToken: String,
    ): Result<GrimmoryTokens> = runCatching {
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(GrimmoryRefreshRequest(refreshToken))
        }
        if (!response.status.isSuccess()) {
            error("Token refresh failed: ${response.status}")
        }
        response.body<GrimmoryTokens>()
    }

    suspend fun getContinueReading(
        baseUrl: String,
        serverId: Long,
        limit: Int = 10,
    ): Result<List<GrimmoryBookSummary>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books/continue-reading") {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) {
            error("Continue reading failed: ${response.status}")
        }
        response.body<List<GrimmoryBookSummary>>()
    }

    suspend fun getBookDetail(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
    ): Result<GrimmoryBookDetail> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books/$bookId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            error("Book detail failed: ${response.status}")
        }
        response.body<GrimmoryBookDetail>()
    }

    suspend fun pushProgress(
        baseUrl: String,
        serverId: Long,
        request: GrimmoryProgressRequest,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/books/progress") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Push progress failed: ${response.status}")
        }
    }

    suspend fun updateReadStatus(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
        status: ReadStatus,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.put("${serverOrigin(baseUrl)}/api/v1/app/books/$bookId/status") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(GrimmoryStatusRequest(status))
        }
        if (!response.status.isSuccess()) {
            error("Status update failed: ${response.status}")
        }
    }

    suspend fun recordReadingSession(
        baseUrl: String,
        serverId: Long,
        request: GrimmoryReadingSessionRequest,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/reading-sessions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Reading session failed: ${response.status}")
        }
    }

    /**
     * Executes a block with a valid access token.
     * If the token is expired (401), refreshes and retries once.
     */
    private suspend fun <T> withAuth(
        baseUrl: String,
        serverId: Long,
        block: suspend (token: String) -> T,
    ): Result<T> = runCatching {
        val token = tokenManager.getAccessToken(serverId)
            ?: error("Not logged in to Grimmory")

        try {
            block(token)
        } catch (e: Exception) {
            if (e.message?.contains("401") == true) {
                Timber.d("Grimmory: access token expired, refreshing...")
                val refreshToken = tokenManager.getRefreshToken(serverId)
                    ?: error("No refresh token available")
                val newTokens = refreshToken(baseUrl, refreshToken).getOrThrow()
                tokenManager.storeTokens(serverId, newTokens)
                block(newTokens.accessToken)
            } else {
                throw e
            }
        }
    }
}
