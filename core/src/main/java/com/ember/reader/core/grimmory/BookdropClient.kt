package com.ember.reader.core.grimmory

import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class BookdropClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager,
) {

    suspend fun getNotification(
        baseUrl: String,
        serverId: Long,
    ): Result<BookdropNotification> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/bookdrop/notification") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Bookdrop notification failed: ${response.status}")
        response.body<BookdropNotification>()
    }

    suspend fun getFiles(
        baseUrl: String,
        serverId: Long,
        status: String? = "PENDING_REVIEW",
        page: Int = 0,
        size: Int = 50,
    ): Result<BookdropPage<BookdropFile>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/bookdrop/files") {
            header("Authorization", "Bearer $token")
            status?.let { parameter("status", it) }
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) error("Bookdrop files failed: ${response.status}")
        response.body<BookdropPage<BookdropFile>>()
    }

    suspend fun finalizeImport(
        baseUrl: String,
        serverId: Long,
        request: BookdropFinalizeRequest,
    ): Result<BookdropFinalizeResult> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookdrop/imports/finalize") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Bookdrop finalize failed: ${response.status}")
        response.body<BookdropFinalizeResult>()
    }

    suspend fun discardFiles(
        baseUrl: String,
        serverId: Long,
        request: BookdropDiscardRequest,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookdrop/files/discard") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) error("Bookdrop discard failed: ${response.status}")
    }

    suspend fun rescan(
        baseUrl: String,
        serverId: Long,
    ): Result<Unit> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookdrop/rescan") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Bookdrop rescan failed: ${response.status}")
    }

    suspend fun getLibrariesWithPaths(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryAppLibraryWithPaths>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/libraries") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Libraries failed: ${response.status}")
        response.body<List<GrimmoryAppLibraryWithPaths>>()
    }

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
                Timber.d("Bookdrop: token expired, refreshing...")
                val refresh = tokenManager.getRefreshToken(serverId)
                    ?: error("No refresh token")
                val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/auth/refresh") {
                    header("Content-Type", "application/json")
                    setBody(GrimmoryRefreshRequest(refresh))
                }
                if (!response.status.isSuccess()) error("Token refresh failed")
                val newTokens = response.body<GrimmoryTokens>()
                tokenManager.storeTokens(serverId, newTokens)
                block(newTokens.accessToken)
            } else {
                throw e
            }
        }
    }
}
