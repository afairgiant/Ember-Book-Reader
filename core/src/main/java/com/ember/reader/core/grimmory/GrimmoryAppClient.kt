package com.ember.reader.core.grimmory

import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrimmoryAppClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager,
) {

    suspend fun getBooks(
        baseUrl: String,
        serverId: Long,
        page: Int = 0,
        size: Int = 50,
        sort: String = "addedOn",
        dir: String = "desc",
        libraryId: Long? = null,
        shelfId: Long? = null,
        status: String? = null,
        search: String? = null,
        fileType: String? = null,
    ): Result<GrimmoryAppPage<GrimmoryAppBook>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books") {
            header("Authorization", "Bearer $token")
            parameter("page", page)
            parameter("size", size)
            parameter("sort", sort)
            parameter("dir", dir)
            libraryId?.let { parameter("libraryId", it) }
            shelfId?.let { parameter("shelfId", it) }
            status?.let { parameter("status", it) }
            search?.let { parameter("search", it) }
            fileType?.let { parameter("fileType", it) }
        }
        if (!response.status.isSuccess()) error("Get books failed: ${response.status}")
        response.body<GrimmoryAppPage<GrimmoryAppBook>>()
    }

    suspend fun searchBooks(
        baseUrl: String,
        serverId: Long,
        query: String,
        page: Int = 0,
        size: Int = 20,
    ): Result<GrimmoryAppPage<GrimmoryAppBook>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books/search") {
            header("Authorization", "Bearer $token")
            parameter("q", query)
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) error("Search failed: ${response.status}")
        response.body<GrimmoryAppPage<GrimmoryAppBook>>()
    }

    suspend fun getLibraries(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryAppLibrary>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/libraries") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Get libraries failed: ${response.status}")
        response.body<List<GrimmoryAppLibrary>>()
    }

    suspend fun getShelves(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryAppShelf>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/shelves") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Get shelves failed: ${response.status}")
        response.body<List<GrimmoryAppShelf>>()
    }

    suspend fun getMagicShelves(
        baseUrl: String,
        serverId: Long,
    ): Result<List<GrimmoryAppMagicShelf>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/shelves/magic") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) error("Get magic shelves failed: ${response.status}")
        response.body<List<GrimmoryAppMagicShelf>>()
    }

    suspend fun getSeries(
        baseUrl: String,
        serverId: Long,
        page: Int = 0,
        size: Int = 20,
        libraryId: Long? = null,
        search: String? = null,
    ): Result<GrimmoryAppPage<GrimmoryAppSeries>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/series") {
            header("Authorization", "Bearer $token")
            parameter("page", page)
            parameter("size", size)
            libraryId?.let { parameter("libraryId", it) }
            search?.let { parameter("search", it) }
        }
        if (!response.status.isSuccess()) error("Get series failed: ${response.status}")
        response.body<GrimmoryAppPage<GrimmoryAppSeries>>()
    }

    suspend fun getSeriesBooks(
        baseUrl: String,
        serverId: Long,
        seriesName: String,
        page: Int = 0,
        size: Int = 50,
    ): Result<GrimmoryAppPage<GrimmoryAppBook>> = withAuth(baseUrl, serverId) { token ->
        val encodedName = java.net.URLEncoder.encode(seriesName, "UTF-8")
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/series/$encodedName/books") {
            header("Authorization", "Bearer $token")
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) error("Get series books failed: ${response.status}")
        response.body<GrimmoryAppPage<GrimmoryAppBook>>()
    }

    suspend fun getAuthors(
        baseUrl: String,
        serverId: Long,
        page: Int = 0,
        size: Int = 30,
        search: String? = null,
    ): Result<GrimmoryAppPage<GrimmoryAppAuthor>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/authors") {
            header("Authorization", "Bearer $token")
            parameter("page", page)
            parameter("size", size)
            search?.let { parameter("search", it) }
        }
        if (!response.status.isSuccess()) error("Get authors failed: ${response.status}")
        response.body<GrimmoryAppPage<GrimmoryAppAuthor>>()
    }

    suspend fun getRecentlyAdded(
        baseUrl: String,
        serverId: Long,
        limit: Int = 10,
    ): Result<List<GrimmoryBookSummary>> = withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books/recently-added") {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) error("Get recently added failed: ${response.status}")
        response.body<List<GrimmoryBookSummary>>()
    }

    fun coverUrl(baseUrl: String, grimmoryBookId: Long): String =
        "${serverOrigin(baseUrl)}/api/v1/media/book/$grimmoryBookId/cover"

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
                Timber.d("GrimmoryApp: token expired, refreshing...")
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
