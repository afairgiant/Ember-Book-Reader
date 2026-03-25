package com.ember.reader.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject

class KosyncClient @Inject constructor(
    private val httpClient: HttpClient,
) {

    suspend fun authenticate(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<Unit> = runCatching {
        val response = httpClient.get("${baseUrl.trimEnd('/')}/api/koreader/users/auth") {
            header("x-auth-user", username)
            header("x-auth-key", md5Hash(password))
            header("Accept", ACCEPT_HEADER)
        }
        if (!response.status.isSuccess()) {
            error("Authentication failed: ${response.status}")
        }
    }

    suspend fun pushProgress(
        baseUrl: String,
        username: String,
        password: String,
        request: KosyncProgressRequest,
    ): Result<Unit> = runCatching {
        val response = httpClient.put("${baseUrl.trimEnd('/')}/api/koreader/syncs/progress") {
            header("x-auth-user", username)
            header("x-auth-key", md5Hash(password))
            header("Accept", ACCEPT_HEADER)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Push progress failed: ${response.status}")
        }
    }

    suspend fun pullProgress(
        baseUrl: String,
        username: String,
        password: String,
        documentHash: String,
    ): Result<KosyncProgressResponse?> = runCatching {
        val response = httpClient.get(
            "${baseUrl.trimEnd('/')}/api/koreader/syncs/progress/$documentHash",
        ) {
            header("x-auth-user", username)
            header("x-auth-key", md5Hash(password))
            header("Accept", ACCEPT_HEADER)
        }
        if (response.status.isSuccess()) {
            response.body<KosyncProgressResponse>()
        } else {
            Timber.w("Pull progress returned ${response.status}")
            null
        }
    }

    companion object {
        private const val ACCEPT_HEADER = "application/vnd.koreader.v1+json"

        fun md5Hash(input: String): String {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
data class KosyncProgressRequest(
    val document: String,
    val progress: String,
    val percentage: Float,
    val device: String,
    val deviceId: String,
)

@Serializable
data class KosyncProgressResponse(
    val document: String? = null,
    val progress: String? = null,
    val percentage: Float? = null,
    val device: String? = null,
    val deviceId: String? = null,
    val timestamp: Long? = null,
)
