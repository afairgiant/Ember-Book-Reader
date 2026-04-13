package com.ember.reader.core.opds

import com.ember.reader.core.model.Book
import com.ember.reader.core.network.basicAuthHeader
import com.ember.reader.core.network.normalizeUrl
import com.ember.reader.core.network.resolveUrl
import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
        val url = if (path != null) resolveUrl(baseUrl, path) else normalizeUrl(baseUrl)
        val response = httpClient.get(url) {
            headers[HttpHeaders.Accept] = "application/atom+xml"
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS fetch failed: ${response.status}")
        }
        OpdsParser.parseFeed(requireXmlBody(response), serverOrigin(baseUrl))
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
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null
    ): Result<Unit> = runCatching {
        val url = resolveUrl(baseUrl, downloadPath)
        httpClient.prepareGet(url) {
            header("Authorization", basicAuth(username, password))
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
}

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsFeedEntry>
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
