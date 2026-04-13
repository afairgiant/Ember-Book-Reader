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

@Singleton
class BookdropClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager,
) {

    suspend fun getNotification(
        baseUrl: String,
        serverId: Long,
    ): Result<BookdropNotification> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/bookdrop/notification") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Bookdrop notification failed: ${response.status}")
        response.body<BookdropNotification>()
    }

    suspend fun getFiles(
        baseUrl: String,
        serverId: Long,
        status: String? = "pending",
        page: Int = 0,
        size: Int = 50,
    ): Result<BookdropPage<BookdropFile>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/bookdrop/files") {
            header("Authorization", "Bearer $token")
            status?.let { parameter("status", it) }
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Bookdrop files failed: ${response.status}")
        response.body<BookdropPage<BookdropFile>>()
    }

    suspend fun finalizeImport(
        baseUrl: String,
        serverId: Long,
        request: BookdropFinalizeRequest,
    ): Result<BookdropFinalizeResult> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookdrop/imports/finalize") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Bookdrop finalize failed: ${response.status}")
        response.body<BookdropFinalizeResult>()
    }

    suspend fun discardFiles(
        baseUrl: String,
        serverId: Long,
        request: BookdropDiscardRequest,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookdrop/files/discard") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Bookdrop discard failed: ${response.status}")
    }

    suspend fun rescan(
        baseUrl: String,
        serverId: Long,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/bookdrop/rescan") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Bookdrop rescan failed: ${response.status}")
    }

    suspend fun getLibrariesWithPaths(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryAppLibraryWithPaths>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/libraries") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Libraries failed: ${response.status}")
        response.body<List<GrimmoryAppLibraryWithPaths>>()
    }

}
