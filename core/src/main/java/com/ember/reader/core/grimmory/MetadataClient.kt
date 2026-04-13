package com.ember.reader.core.grimmory

import com.ember.reader.core.network.serverOrigin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Client for Grimmory's book metadata editing endpoints.
 *
 * Auth is handled via the existing [GrimmoryTokenManager] + `withAuth` pattern used by
 * [BookdropClient]. The PUT/GET/POST calls return [Result]; the SSE search returns a [Flow]
 * that is collected from a coroutine (401 refresh is not attempted on SSE — caller must be
 * logged in).
 */
@Singleton
class MetadataClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager,
) {

    /** Fetch the current metadata (with lock flags) for a book. */
    suspend fun getBookMetadata(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
    ): Result<GrimmoryBookMetadata> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.get("${serverOrigin(baseUrl)}/api/v1/books/$bookId") {
            header("Authorization", "Bearer $token")
            parameter("withDescription", true)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Get book metadata failed: ${response.status}")
        response.body<BookEnvelope>().metadata
            ?: error("Book ${'$'}bookId has no metadata")
    }

    /** Update metadata. Returns the server's canonical view after save. */
    suspend fun updateMetadata(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
        wrapper: MetadataUpdateWrapper,
        replaceMode: MetadataReplaceMode = MetadataReplaceMode.REPLACE_WHEN_PROVIDED,
        mergeCategories: Boolean = false,
    ): Result<GrimmoryBookMetadata> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.put("${serverOrigin(baseUrl)}/api/v1/books/$bookId/metadata") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            parameter("mergeCategories", mergeCategories)
            parameter("replaceMode", replaceMode.name)
            setBody(wrapper)
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Update metadata failed: ${response.status}")
        response.body<GrimmoryBookMetadata>()
    }

    /** Upload a new cover from a URL. */
    suspend fun uploadCoverFromUrl(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
        url: String,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/books/$bookId/metadata/cover/from-url") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CoverFromUrlRequest(url))
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Cover upload (url) failed: ${response.status}")
    }

    /** Upload a new cover from a local byte array. */
    suspend fun uploadCoverFile(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/books/$bookId/metadata/cover/upload") {
            header("Authorization", "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) throw GrimmoryHttpException(response.status.value, "Cover upload failed: ${response.status}")
    }

    /**
     * Streams prospective metadata from Grimmory. Grimmory returns a Spring
     * `text/event-stream` where each event is a `data: <json BookMetadata>` line.
     *
     * The flow emits [MetadataSearchEvent.Candidate] for each parsed result, [Error] for
     * malformed lines, and [Done] when the stream closes.
     */
    fun searchProviders(
        baseUrl: String,
        serverId: Long,
        bookId: Long,
        request: FetchMetadataRequest,
    ): Flow<MetadataSearchEvent> = flow {
        val token = tokenManager.getAccessToken(serverId)
            ?: throw IllegalStateException("Not logged in to Grimmory")
        httpClient.preparePost("${serverOrigin(baseUrl)}/api/v1/books/$bookId/metadata/prospective") {
            header("Authorization", "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            contentType(ContentType.Application.Json)
            setBody(request)
            // Provider searches can be slow — let the stream run.
            timeout { requestTimeoutMillis = 120_000 }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit(MetadataSearchEvent.Error("Search failed: ${response.status}"))
                emit(MetadataSearchEvent.Done)
                return@execute
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                try {
                    val metadata = sseJson.decodeFromString(
                        GrimmoryBookMetadata.serializer(),
                        payload,
                    )
                    emit(MetadataSearchEvent.Candidate(metadata))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse metadata SSE line")
                    emit(MetadataSearchEvent.Error(e.message ?: "Parse error"))
                }
            }
            emit(MetadataSearchEvent.Done)
        }
    }

    private companion object {
        val sseJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

@kotlinx.serialization.Serializable
private data class CoverFromUrlRequest(val url: String)

@kotlinx.serialization.Serializable
private data class BookEnvelope(val metadata: GrimmoryBookMetadata? = null)
