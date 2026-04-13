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
import io.ktor.client.request.delete
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
                throw GrimmoryHttpException(response.status.value, "Login failed: ${response.status}")
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
                throw GrimmoryHttpException(response.status.value, "Token refresh failed: ${response.status}")
            }
            response.body<GrimmoryTokens>()
        }

    suspend fun getContinueReading(
        baseUrl: String,
        serverId: Long,
        limit: Int = 10
    ): Result<List<GrimmoryBookSummary>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books/continue-reading") {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Continue reading failed: ${response.status}")
        }
        response.body<List<GrimmoryBookSummary>>()
    }

    suspend fun getBookDetail(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long
    ): Result<GrimmoryBookDetail> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books/$grimmoryBookId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Book detail failed: ${response.status}")
        }
        response.body<GrimmoryBookDetail>()
    }

    suspend fun pushProgress(
        baseUrl: String,
        serverId: Long,
        request: GrimmoryProgressRequest
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/books/progress") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Push progress failed: ${response.status}")
        }
    }

    suspend fun updateReadStatus(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long,
        status: ReadStatus
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.put("${serverOrigin(baseUrl)}/api/v1/app/books/$grimmoryBookId/status") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(GrimmoryStatusRequest(status))
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Status update failed: ${response.status}")
        }
    }

    suspend fun downloadBook(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long,
        destination: File,
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        httpClient.prepareGet("${serverOrigin(baseUrl)}/api/v1/books/$grimmoryBookId/download") {
            header("Authorization", "Bearer $token")
            timeout {
                requestTimeoutMillis = 600_000
                socketTimeoutMillis = 120_000
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw GrimmoryHttpException(response.status.value, "Download failed: ${response.status}")
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
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/reading-sessions") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Reading session failed: ${response.status}")
        }
    }

    suspend fun getAudiobookInfo(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long
    ): Result<AudiobookInfo> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/audiobooks/$grimmoryBookId/info") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Audiobook info failed: ${response.status}")
        }
        response.body<AudiobookInfo>()
    }

    // --- Annotation/Highlight sync ---

    suspend fun getAnnotations(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
    ): Result<List<GrimmoryAnnotation>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/annotations/book/$bookId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get annotations failed: ${response.status}")
        response.body<List<GrimmoryAnnotation>>()
    }

    suspend fun createAnnotation(
        baseUrl: String,
        serverId: Long,
        request: CreateAnnotationRequest,
    ): Result<GrimmoryAnnotation> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/annotations") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Create annotation failed: ${response.status}")
        response.body<GrimmoryAnnotation>()
    }

    suspend fun updateAnnotation(
        baseUrl: String,
        serverId: Long,
        annotationId: Long,
        request: UpdateAnnotationRequest,
    ): Result<GrimmoryAnnotation> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.put("${serverOrigin(baseUrl)}/api/v1/annotations/$annotationId") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Update annotation failed: ${response.status}")
        response.body<GrimmoryAnnotation>()
    }

    suspend fun deleteAnnotation(
        baseUrl: String,
        serverId: Long,
        annotationId: Long,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.delete("${serverOrigin(baseUrl)}/api/v1/annotations/$annotationId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Delete annotation failed: ${response.status}")
    }

    // --- Bookmark sync ---

    suspend fun getBookmarks(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
    ): Result<List<GrimmoryBookmark>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/bookmarks/book/$bookId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get bookmarks failed: ${response.status}")
        response.body<List<GrimmoryBookmark>>()
    }

    suspend fun createBookmark(
        baseUrl: String,
        serverId: Long,
        request: CreateBookmarkRequest,
    ): Result<GrimmoryBookmark> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookmarks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Create bookmark failed: ${response.status}")
        response.body<GrimmoryBookmark>()
    }

    suspend fun updateBookmark(
        baseUrl: String,
        serverId: Long,
        bookmarkId: Long,
        request: UpdateBookmarkRequest,
    ): Result<GrimmoryBookmark> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.put("${serverOrigin(baseUrl)}/api/v1/bookmarks/$bookmarkId") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Update bookmark failed: ${response.status}")
        response.body<GrimmoryBookmark>()
    }

    suspend fun deleteBookmark(
        baseUrl: String,
        serverId: Long,
        bookmarkId: Long,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.delete("${serverOrigin(baseUrl)}/api/v1/bookmarks/$bookmarkId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Delete bookmark failed: ${response.status}")
    }

    // --- Reading stats ---

    suspend fun getReadingStreak(
        baseUrl: String,
        serverId: Long,
    ): Result<GrimmoryStreakResponse> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/streak") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Reading streak failed: ${response.status}")
        response.body<GrimmoryStreakResponse>()
    }

    suspend fun getReadingHeatmap(
        baseUrl: String,
        serverId: Long,
        year: Int,
    ): Result<List<GrimmoryDateCount>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/heatmap") {
            header("Authorization", "Bearer $token")
            parameter("year", year)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Reading heatmap failed: ${response.status}")
        response.body<List<GrimmoryDateCount>>()
    }

    suspend fun getPeakHours(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryPeakHour>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/peak-hours") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Peak hours failed: ${response.status}")
        response.body<List<GrimmoryPeakHour>>()
    }

    suspend fun getFavoriteDays(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryFavoriteDay>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/favorite-days") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Favorite days failed: ${response.status}")
        response.body<List<GrimmoryFavoriteDay>>()
    }

    suspend fun getBookDistributions(
        baseUrl: String,
        serverId: Long,
    ): Result<GrimmoryBookDistributions> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/book-distributions") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Book distributions failed: ${response.status}")
        response.body<GrimmoryBookDistributions>()
    }

    suspend fun getGenreStats(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryGenreStat>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/genres") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Genre stats failed: ${response.status}")
        response.body<List<GrimmoryGenreStat>>()
    }

    suspend fun getReadingDates(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryDateCount>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/dates") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Reading dates failed: ${response.status}")
        response.body<List<GrimmoryDateCount>>()
    }

    suspend fun getReadingTimeline(
        baseUrl: String,
        serverId: Long,
        year: Int,
        week: Int,
    ): Result<List<GrimmoryTimelineEntry>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/timeline") {
            header("Authorization", "Bearer $token")
            parameter("year", year)
            parameter("week", week)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Reading timeline failed: ${response.status}")
        response.body<List<GrimmoryTimelineEntry>>()
    }

    suspend fun getPageTurnerScores(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryPageTurnerScore>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/page-turner-scores") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Page turner scores failed: ${response.status}")
        response.body<List<GrimmoryPageTurnerScore>>()
    }

    suspend fun getSessionScatter(
        baseUrl: String,
        serverId: Long,
        year: Int,
    ): Result<List<GrimmorySessionScatter>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/user-stats/reading/session-scatter") {
            header("Authorization", "Bearer $token")
            parameter("year", year)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Session scatter failed: ${response.status}")
        response.body<List<GrimmorySessionScatter>>()
    }

    /**
     * Builds a streaming URL for an audiobook with JWT auth query param.
     * ExoPlayer will use this URL directly for HTTP streaming with Range support.
     */
    suspend fun audiobookStreamUrl(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long,
        trackIndex: Int? = null
    ): String? {
        val token = tokenManager.getAccessToken(serverId) ?: return null
        val origin = serverOrigin(baseUrl)
        return if (trackIndex != null) {
            "$origin/api/v1/audiobooks/$grimmoryBookId/track/$trackIndex/stream?token=$token"
        } else {
            "$origin/api/v1/audiobooks/$grimmoryBookId/stream?token=$token"
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
                throw GrimmoryHttpException(response.status.value, "Download failed: ${response.status}")
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

    fun audiobookCoverUrl(baseUrl: String, grimmoryBookId: Long): String =
        "${serverOrigin(baseUrl)}/api/v1/audiobooks/$grimmoryBookId/cover"
}
