package com.ember.reader.core.grimmory

import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class GrimmoryClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager
) {

    suspend fun checkHealth(baseUrl: String): Boolean = runCatching {
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/healthcheck")
        response.status.isSuccess()
    }.getOrDefault(false)

    suspend fun login(baseUrl: String, username: String, password: String): Result<GrimmoryTokens> =
        runCatching {
            val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(GrimmoryLoginRequest(username, password))
            }
            if (!response.status.isSuccess()) {
                error("Login failed: ${response.status}")
            }
            response.body<GrimmoryTokens>()
        }

    suspend fun refreshToken(baseUrl: String, refreshToken: String): Result<GrimmoryTokens> =
        runCatching {
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
        limit: Int = 10
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
        bookId: Long
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
        request: GrimmoryProgressRequest
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
        status: ReadStatus
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

    suspend fun downloadBook(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long,
        destination: File,
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        httpClient.prepareGet("${serverOrigin(baseUrl)}/api/v1/books/$grimmoryBookId/download") {
            header("Authorization", "Bearer $token")
            timeout {
                requestTimeoutMillis = 600_000
                socketTimeoutMillis = 120_000
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("Download failed: ${response.status}")
            }
            val totalBytes = response.contentLength()
            val channel = response.bodyAsChannel()
            withContext(Dispatchers.IO) {
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    while (!channel.isClosedForRead) {
                        val bytes = channel.readAvailable(buffer)
                        if (bytes > 0) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            onProgress?.invoke(downloaded, totalBytes)
                        }
                    }
                }
            }
        }
    }

    suspend fun recordReadingSession(
        baseUrl: String,
        serverId: Long,
        request: GrimmoryReadingSessionRequest
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

    suspend fun getAudiobookInfo(
        baseUrl: String,
        serverId: Long,
        bookId: Long
    ): Result<AudiobookInfo> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/audiobooks/$bookId/info") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            error("Audiobook info failed: ${response.status}")
        }
        response.body<AudiobookInfo>()
    }

    /**
     * Builds a streaming URL for an audiobook with JWT auth query param.
     * ExoPlayer will use this URL directly for HTTP streaming with Range support.
     */
    suspend fun audiobookStreamUrl(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
        trackIndex: Int? = null
    ): String? {
        val token = tokenManager.getAccessToken(serverId) ?: return null
        val origin = serverOrigin(baseUrl)
        return if (trackIndex != null) {
            "$origin/api/v1/audiobooks/$bookId/track/$trackIndex/stream?token=$token"
        } else {
            "$origin/api/v1/audiobooks/$bookId/stream?token=$token"
        }
    }

    suspend fun downloadFromUrl(
        url: String,
        destination: File,
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
    ): Result<Unit> = runCatching {
        httpClient.prepareGet(url) {
            timeout {
                requestTimeoutMillis = 600_000
                socketTimeoutMillis = 120_000
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("Download failed: ${response.status}")
            }
            val totalBytes = response.contentLength()
            val channel = response.bodyAsChannel()
            withContext(Dispatchers.IO) {
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    while (!channel.isClosedForRead) {
                        val bytes = channel.readAvailable(buffer)
                        if (bytes > 0) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            onProgress?.invoke(downloaded, totalBytes)
                        }
                    }
                }
            }
        }
    }

    fun audiobookCoverUrl(baseUrl: String, bookId: Long): String =
        "${serverOrigin(baseUrl)}/api/v1/audiobooks/$bookId/cover"

    /**
     * Executes a block with a valid access token.
     * If the token is expired (401), refreshes and retries once.
     */
    private suspend fun <T> withAuth(
        baseUrl: String,
        serverId: Long,
        block: suspend (token: String) -> T
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
