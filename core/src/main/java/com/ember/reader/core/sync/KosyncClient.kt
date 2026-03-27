package com.ember.reader.core.sync

import com.ember.reader.core.network.md5Hash
import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

@Singleton
class KosyncClient @Inject constructor(
    private val httpClient: HttpClient
) {

    suspend fun authenticate(baseUrl: String, username: String, password: String): Result<Unit> =
        runCatching {
            val response = httpClient.get(serverOrigin(baseUrl) + "/api/koreader/users/auth") {
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
        request: KosyncProgressRequest
    ): Result<Unit> = runCatching {
        val response = httpClient.put(serverOrigin(baseUrl) + "/api/koreader/syncs/progress") {
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
        documentHash: String
    ): Result<KosyncProgressResponse?> = runCatching {
        val response = httpClient.get(
            serverOrigin(baseUrl) + "/api/koreader/syncs/progress/$documentHash"
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
    }
}

@Serializable
data class KosyncProgressRequest(
    val document: String,
    val progress: String,
    val percentage: Float,
    val device: String,
    @SerialName("device_id")
    val deviceId: String
)

@Serializable
data class KosyncProgressResponse(
    val document: String? = null,
    val progress: String? = null,
    val percentage: Float? = null,
    val device: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null,
    val timestamp: Long? = null
)
