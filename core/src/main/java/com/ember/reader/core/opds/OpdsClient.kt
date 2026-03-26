package com.ember.reader.core.opds

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.network.buildServerUrl
import com.ember.reader.core.network.resolveUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsClient @Inject constructor(
    private val httpClient: HttpClient,
) {

    suspend fun testConnection(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<String> = runCatching {
        val response = httpClient.get(buildServerUrl(baseUrl, "/api/v1/opds")) {
            header("Accept", "application/atom+xml")
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
        path: String = "/api/v1/opds",
    ): Result<OpdsFeed> = runCatching {
        val url = buildServerUrl(baseUrl, path)
        val response = httpClient.get(url) {
            header("Accept", "application/atom+xml")
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS fetch failed: ${response.status}")
        }
        OpdsParser.parseFeed(requireXmlBody(response), baseUrl)
    }

    suspend fun fetchBooks(
        baseUrl: String,
        username: String,
        password: String,
        serverId: Long,
        path: String = "/api/v1/opds/catalog",
        page: Int = 1,
    ): Result<OpdsBookPage> = runCatching {
        val url = buildServerUrl(baseUrl, "$path?page=$page")
        val response = httpClient.get(url) {
            header("Accept", "application/atom+xml")
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS book fetch failed: ${response.status}")
        }
        OpdsParser.parseBookFeed(requireXmlBody(response), baseUrl, serverId)
    }

    suspend fun searchBooks(
        baseUrl: String,
        username: String,
        password: String,
        serverId: Long,
        query: String,
        page: Int = 1,
    ): Result<OpdsBookPage> = runCatching {
        val url = buildServerUrl(baseUrl, "/api/v1/opds/catalog?q=$query&page=$page")
        val response = httpClient.get(url) {
            header("Accept", "application/atom+xml")
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS search failed: ${response.status}")
        }
        OpdsParser.parseBookFeed(requireXmlBody(response), baseUrl, serverId)
    }

    suspend fun downloadBookToFile(
        baseUrl: String,
        username: String,
        password: String,
        downloadPath: String,
        destination: File,
    ): Result<Unit> = runCatching {
        val url = resolveUrl(baseUrl, downloadPath)
        httpClient.prepareGet(url) {
            header("Authorization", basicAuth(username, password))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("Download failed: ${response.status}")
            }
            val channel = response.bodyAsChannel()
            withContext(Dispatchers.IO) {
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
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

    private fun basicAuth(username: String, password: String): String {
        val credentials = "$username:$password"
        val encoded = android.util.Base64.encodeToString(
            credentials.toByteArray(),
            android.util.Base64.NO_WRAP,
        )
        return "Basic $encoded"
    }
}

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsFeedEntry>,
)

data class OpdsFeedEntry(
    val id: String,
    val title: String,
    val href: String,
    val content: String? = null,
)

data class OpdsBookPage(
    val books: List<Book>,
    val nextPagePath: String? = null,
    val totalResults: Int? = null,
)
