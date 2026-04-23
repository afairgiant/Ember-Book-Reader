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
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class GrimmoryAppClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager
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
        minRating: Int? = null,
        maxRating: Int? = null,
        authors: String? = null,
        language: String? = null
    ): Result<GrimmoryAppPage<GrimmoryAppBook>> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
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
                minRating?.let { parameter("minRating", it) }
                maxRating?.let { parameter("maxRating", it) }
                authors?.takeIf { it.isNotBlank() }?.let { parameter("authors", it) }
                language?.takeIf { it.isNotBlank() }?.let { parameter("language", it) }
            }
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get books failed: ${response.status}")
            response.body<GrimmoryAppPage<GrimmoryAppBook>>()
        }

    suspend fun searchBooks(
        baseUrl: String,
        serverId: Long,
        query: String,
        page: Int = 0,
        size: Int = 20
    ): Result<GrimmoryAppPage<GrimmoryAppBook>> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            val url = "${serverOrigin(baseUrl)}/api/v1/app/books/search"
            Timber.d("GrimmorySearch: url=$url q='$query' page=$page size=$size")
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $token")
                parameter("q", query)
                parameter("page", page)
                parameter("size", size)
            }
            Timber.d("GrimmorySearch: response status=${response.status}")
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Search failed: ${response.status}")
            val result = response.body<GrimmoryAppPage<GrimmoryAppBook>>()
            Timber.d("GrimmorySearch: got ${result.content.size} results for '$query'")
            result
        }

    suspend fun getShelves(baseUrl: String, serverId: Long): Result<List<GrimmoryAppShelf>> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/shelves") {
                header("Authorization", "Bearer $token")
            }
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get shelves failed: ${response.status}")
            response.body<List<GrimmoryAppShelf>>()
        }

    /**
     * Full book detail from the regular (non-app) endpoint `/api/v1/books/{id}`.
     * Unlike [GrimmoryClient.getBookDetail] which hits `/api/v1/app/books/{id}`
     * and returns a lightweight DTO, this includes [GrimmoryBookDetail.primaryFile],
     * [GrimmoryBookDetail.libraryPath], and other fields the Organize Files preview
     * needs to render the current and new file paths.
     */
    suspend fun getBookDetailFull(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long
    ): Result<GrimmoryFullBook> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/books/$grimmoryBookId") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get book detail failed: ${response.status}")
        response.body<GrimmoryFullBook>()
    }

    /**
     * Full Grimmory library list including [GrimmoryLibraryFull.paths] and
     * [GrimmoryLibraryFull.fileNamingPattern], via the regular (non-app) libraries
     * endpoint. Used by Organize Files to offer a destination picker and drive the
     * filename preview.
     */
    suspend fun getFullLibraries(
        baseUrl: String,
        serverId: Long
    ): Result<List<GrimmoryLibraryFull>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/libraries") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get full libraries failed: ${response.status}")
        response.body<List<GrimmoryLibraryFull>>()
    }

    /**
     * Posts a bulk file-move request to Grimmory. The server physically moves files
     * between library paths and updates DB foreign keys so the books appear under
     * their new libraries. Returns [Unit] on 2xx; wraps non-success status codes in
     * the [Result] failure with the status text in the exception message.
     */
    suspend fun moveFiles(baseUrl: String, serverId: Long, request: FileMoveRequest): Result<Unit> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/files/move") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/json")
                setBody(request)
            }
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Move files failed: ${response.status}")
        }

    /**
     * Fetches the currently-authenticated user's [GrimmoryUser] record, including the
     * nested [GrimmoryUserPermissions] used to gate admin-only actions like Organize
     * Files.
     */
    suspend fun getCurrentUser(baseUrl: String, serverId: Long): Result<GrimmoryUser> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            fetchCurrentUserWithToken(baseUrl, token)
        }

    /**
     * Fetches the current user with an already-obtained access token.
     * For flows that don't yet have a persisted [GrimmoryTokenManager] entry —
     * e.g. the pre-save Test button on the server form.
     */
    suspend fun fetchCurrentUser(baseUrl: String, accessToken: String): Result<GrimmoryUser> =
        runCatching { fetchCurrentUserWithToken(baseUrl, accessToken) }

    private suspend fun fetchCurrentUserWithToken(baseUrl: String, token: String): GrimmoryUser {
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/users/me") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Get current user failed: ${response.status}")
        }
        return response.body<GrimmoryUser>()
    }

    suspend fun getMagicShelfBooks(
        baseUrl: String,
        serverId: Long,
        magicShelfId: Long,
        page: Int = 0,
        size: Int = 50
    ): Result<GrimmoryAppPage<GrimmoryAppBook>> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/shelves/magic/$magicShelfId/books") {
                header("Authorization", "Bearer $token")
                parameter("page", page)
                parameter("size", size)
            }
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get magic shelf books failed: ${response.status}")
            response.body<GrimmoryAppPage<GrimmoryAppBook>>()
        }

    suspend fun getMagicShelves(
        baseUrl: String,
        serverId: Long
    ): Result<List<GrimmoryAppMagicShelf>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/shelves/magic") {
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get magic shelves failed: ${response.status}")
        response.body<List<GrimmoryAppMagicShelf>>()
    }

    suspend fun getSeries(
        baseUrl: String,
        serverId: Long,
        page: Int = 0,
        size: Int = 20,
        sort: String = "name",
        dir: String = "asc",
        libraryId: Long? = null,
        search: String? = null
    ): Result<GrimmoryAppPage<GrimmoryAppSeries>> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/series") {
                header("Authorization", "Bearer $token")
                parameter("page", page)
                parameter("size", size)
                parameter("sort", sort)
                parameter("dir", dir)
                libraryId?.let { parameter("libraryId", it) }
                search?.let { parameter("search", it) }
            }
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get series failed: ${response.status}")
            response.body<GrimmoryAppPage<GrimmoryAppSeries>>()
        }

    suspend fun getSeriesBooks(
        baseUrl: String,
        serverId: Long,
        seriesName: String,
        page: Int = 0,
        size: Int = 50
    ): Result<GrimmoryAppPage<GrimmoryAppBook>> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            // URLEncoder uses + for spaces (query string convention), but path segments need %20
            val encodedName = java.net.URLEncoder.encode(seriesName, "UTF-8").replace("+", "%20")
            val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/series/$encodedName/books") {
                header("Authorization", "Bearer $token")
                parameter("page", page)
                parameter("size", size)
            }
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get series books failed: ${response.status}")
            response.body<GrimmoryAppPage<GrimmoryAppBook>>()
        }

    suspend fun getAuthors(
        baseUrl: String,
        serverId: Long,
        page: Int = 0,
        size: Int = 30,
        search: String? = null
    ): Result<GrimmoryAppPage<GrimmoryAppAuthor>> =
        tokenManager.withAuth(baseUrl, serverId) { token ->
            val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/authors") {
                header("Authorization", "Bearer $token")
                parameter("page", page)
                parameter("size", size)
                search?.let { parameter("search", it) }
            }
            if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get authors failed: ${response.status}")
            response.body<GrimmoryAppPage<GrimmoryAppAuthor>>()
        }

    suspend fun getRecentlyAdded(
        baseUrl: String,
        serverId: Long,
        limit: Int = 10
    ): Result<List<GrimmoryBookSummary>> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/books/recently-added") {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get recently added failed: ${response.status}")
        response.body<List<GrimmoryBookSummary>>()
    }

    suspend fun getFilterOptions(
        baseUrl: String,
        serverId: Long,
        libraryId: Long? = null,
        shelfId: Long? = null,
        magicShelfId: Long? = null
    ): Result<GrimmoryAppFilterOptions> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/app/filter-options") {
            header("Authorization", "Bearer $token")
            libraryId?.let { parameter("libraryId", it) }
            shelfId?.let { parameter("shelfId", it) }
            magicShelfId?.let { parameter("magicShelfId", it) }
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get filter options failed: ${response.status}")
        response.body<GrimmoryAppFilterOptions>()
    }

    /**
     * Set or clear the user's personal rating for a Grimmory book on the 1-10 scale
     * used by the web UI (`metadata-viewer` uses `stars="10"`). Pass a non-null
     * [rating] in [1, 10] to set it, or `null` to clear it. The set path hits
     * `PUT /api/v1/books/personal-rating` with `{ids:[bookId], rating}`; the clear
     * path hits `POST /api/v1/books/reset-personal-rating` with `[bookId]`, matching
     * the frontend's `book-patch.service.ts`.
     */
    suspend fun setPersonalRating(
        baseUrl: String,
        serverId: Long,
        grimmoryBookId: Long,
        rating: Int?
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = if (rating == null) {
            httpClient.post("${serverOrigin(baseUrl)}/api/v1/books/reset-personal-rating") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/json")
                setBody(listOf(grimmoryBookId))
            }
        } else {
            httpClient.put("${serverOrigin(baseUrl)}/api/v1/books/personal-rating") {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/json")
                setBody(PersonalRatingUpdateRequest(ids = listOf(grimmoryBookId), rating = rating))
            }
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Set personal rating failed: ${response.status}")
        }
    }

    fun coverUrl(baseUrl: String, grimmoryBookId: Long, coverUpdatedOn: String? = null): String =
        grimmoryCoverUrl(baseUrl, grimmoryBookId, coverUpdatedOn)

    fun audiobookCoverUrl(baseUrl: String, grimmoryBookId: Long, coverUpdatedOn: String? = null): String =
        grimmoryAudiobookCoverUrl(baseUrl, grimmoryBookId, coverUpdatedOn)
}
