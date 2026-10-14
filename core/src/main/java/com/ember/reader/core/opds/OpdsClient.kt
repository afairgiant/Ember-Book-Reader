package com.ember.reader.core.opds

import com.ember.reader.core.coroutine.runCatchingCancellable
import com.ember.reader.core.model.Book
import com.ember.reader.core.network.basicAuthHeader
import com.ember.reader.core.network.fileTransferTimeouts
import com.ember.reader.core.network.normalizeUrl
import com.ember.reader.core.network.resolveUrl
import com.ember.reader.core.network.serverOrigin
import com.ember.reader.core.network.streamTo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class OpdsClient @Inject constructor(
    private val httpClient: HttpClient
) {

    suspend fun testConnection(
        baseUrl: String,
        username: String,
        password: String
    ): Result<String> = runCatching {
        val response = httpClient.get(normalizeUrl(baseUrl)) {
            headers[HttpHeaders.Accept] = "application/atom+xml"
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Timber.e("OPDS connection failed: ${response.status}\n$errorBody")
            error("OPDS connection failed: ${response.status}")
        }
        val body = requireXmlBody(response)
        OpdsParser.parseFeedTitle(body) ?: "Connected"
    }

    suspend fun fetchCatalog(
        baseUrl: String,
        username: String,
        password: String,
        path: String? = null
    ): Result<OpdsFeed> = runCatching {
        var url = if (path != null) resolveUrl(baseUrl, path) else normalizeUrl(baseUrl)
        var title = ""
        val entries = mutableListOf<OpdsFeedEntry>()
        // Grimmory pages its author/series navigation feeds (50 per page), so keep
        // following rel="next" until the feed is exhausted.
        for (pageIndex in 0 until MAX_NAVIGATION_PAGES) {
            val response = httpClient.get(url) {
                headers[HttpHeaders.Accept] = "application/atom+xml"
                header("Authorization", basicAuth(username, password))
            }
            if (!response.status.isSuccess()) {
                error("OPDS fetch failed: ${response.status}")
            }
            val page = OpdsParser.parseFeed(requireXmlBody(response), serverOrigin(baseUrl))
            if (pageIndex == 0) title = page.title
            entries += page.entries
            url = resolveUrl(baseUrl, page.nextPagePath ?: break)
        }
        OpdsFeed(title = title, entries = entries)
    }

    suspend fun fetchBooks(
        baseUrl: String,
        username: String,
        password: String,
        serverId: Long,
        path: String,
        page: Int = 1
    ): Result<OpdsBookPage> = runCatching {
        val separator = if ("?" in path) "&" else "?"
        val resolvedPath = "${path}${separator}page=$page"
        val url = resolveUrl(baseUrl, resolvedPath)
        val response = httpClient.get(url) {
            headers[HttpHeaders.Accept] = "application/atom+xml"
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS book fetch failed: ${response.status}")
        }
        OpdsParser.parseBookFeed(requireXmlBody(response), serverOrigin(baseUrl), serverId)
    }

    suspend fun searchBooks(
        baseUrl: String,
        username: String,
        password: String,
        serverId: Long,
        query: String,
        page: Int = 1
    ): Result<OpdsBookPage> = runCatching {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchPath = "${normalizeUrl(baseUrl)}/catalog?q=$encodedQuery&page=$page"
        val response = httpClient.get(searchPath) {
            headers[HttpHeaders.Accept] = "application/atom+xml"
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS search failed: ${response.status}")
        }
        OpdsParser.parseBookFeed(requireXmlBody(response), serverOrigin(baseUrl), serverId)
    }

    suspend fun downloadBookToFile(
        baseUrl: String,
        username: String,
        password: String,
        downloadPath: String,
        destination: File,
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
        validate: (File) -> Unit = {},
    ): Result<Unit> = runCatchingCancellable {
        val url = resolveUrl(baseUrl, downloadPath)
        httpClient.prepareGet(url) {
            header("Authorization", basicAuth(username, password))
            fileTransferTimeouts()
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("Download failed: ${response.status}")
            }
            response.streamTo(destination, onProgress, validate)
        }
    }

    private suspend fun requireXmlBody(response: HttpResponse): String {
        val body = response.bodyAsText()
        val contentType = response.contentType()?.toString().orEmpty()
        if (contentType.contains("html") || body.trimStart().startsWith("<!")) {
            error("Server returned HTML instead of OPDS XML — check the server URL and credentials")
        }
        return body
    }

    private fun basicAuth(username: String, password: String): String =
        basicAuthHeader(username, password)

    private companion object {
        /** Upper bound on navigation feed pages followed, in case a server loops its next links. */
        const val MAX_NAVIGATION_PAGES = 50
    }
}

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsFeedEntry>,
    /** Feed-level rel="next" link of a paginated navigation feed, if any. */
    val nextPagePath: String? = null
)

data class OpdsFeedEntry(
    val id: String,
    val title: String,
    val href: String,
    val content: String? = null
)

data class OpdsBookPage(
    val books: List<Book>,
    val nextPagePath: String? = null,
    val totalResults: Int? = null,
    val resolvedBookIds: List<String> = emptyList()
)
