package com.ember.reader.core.opds

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class OpdsClient @Inject constructor(
    private val httpClient: HttpClient,
) {

    suspend fun testConnection(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<String> = runCatching {
        val response = httpClient.get("${baseUrl.trimEnd('/')}/api/v1/opds") {
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS connection failed: ${response.status}")
        }
        val body = response.bodyAsText()
        OpdsParser.parseFeedTitle(body) ?: "Connected"
    }

    suspend fun fetchCatalog(
        baseUrl: String,
        username: String,
        password: String,
        path: String = "/api/v1/opds",
    ): Result<OpdsFeed> = runCatching {
        val url = "${baseUrl.trimEnd('/')}$path"
        val response = httpClient.get(url) {
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS fetch failed: ${response.status}")
        }
        OpdsParser.parseFeed(response.bodyAsText(), baseUrl)
    }

    suspend fun fetchBooks(
        baseUrl: String,
        username: String,
        password: String,
        serverId: Long,
        path: String = "/api/v1/opds/catalog",
        page: Int = 1,
    ): Result<OpdsBookPage> = runCatching {
        val url = "${baseUrl.trimEnd('/')}$path?page=$page"
        val response = httpClient.get(url) {
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS book fetch failed: ${response.status}")
        }
        OpdsParser.parseBookFeed(response.bodyAsText(), baseUrl, serverId)
    }

    suspend fun searchBooks(
        baseUrl: String,
        username: String,
        password: String,
        serverId: Long,
        query: String,
        page: Int = 1,
    ): Result<OpdsBookPage> = runCatching {
        val url = "${baseUrl.trimEnd('/')}/api/v1/opds/catalog?q=$query&page=$page"
        val response = httpClient.get(url) {
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("OPDS search failed: ${response.status}")
        }
        OpdsParser.parseBookFeed(response.bodyAsText(), baseUrl, serverId)
    }

    suspend fun downloadBook(
        baseUrl: String,
        username: String,
        password: String,
        downloadPath: String,
    ): Result<ByteArray> = runCatching {
        val url = if (downloadPath.startsWith("http")) {
            downloadPath
        } else {
            "${baseUrl.trimEnd('/')}$downloadPath"
        }
        val response = httpClient.get(url) {
            header("Authorization", basicAuth(username, password))
        }
        if (!response.status.isSuccess()) {
            error("Download failed: ${response.status}")
        }
        io.ktor.client.call.body<ByteArray>(response)
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
