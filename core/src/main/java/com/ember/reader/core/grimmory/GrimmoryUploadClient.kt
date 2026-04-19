package com.ember.reader.core.grimmory

import android.content.Context
import android.net.Uri
import com.ember.reader.core.network.serverOrigin
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads a local file (picked via SAF) to Grimmory — either into a specific library/path
 * (`/api/v1/files/upload`) or into the Book Drop staging area (`/api/v1/files/upload/bookdrop`).
 *
 * The file is streamed from the [Uri] via the [android.content.ContentResolver]; no full copy
 * is held in memory. Progress updates are delivered via [onProgress] from Ktor's `onUpload`
 * hook. Both endpoints require the user's Grimmory account to have the `canUpload` permission.
 */
@Singleton
class GrimmoryUploadClient @Inject constructor(
    private val httpClient: HttpClient,
    private val tokenManager: GrimmoryTokenManager,
    @ApplicationContext private val context: Context,
) {

    suspend fun uploadToLibrary(
        baseUrl: String,
        serverId: Long,
        libraryId: Long,
        pathId: Long,
        fileUri: Uri,
        displayName: String,
        mimeType: String,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/files/upload") {
            header("Authorization", "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("libraryId", libraryId.toString())
                        append("pathId", pathId.toString())
                        append(
                            key = "file",
                            value = filePartProvider(fileUri),
                            headers = filePartHeaders(displayName, mimeType),
                        )
                    }
                )
            )
            onUpload { sent, total -> onProgress(sent, total ?: -1L) }
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Library upload failed: ${response.status}")
        }
    }

    suspend fun uploadToBookdrop(
        baseUrl: String,
        serverId: Long,
        fileUri: Uri,
        displayName: String,
        mimeType: String,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit,
    ): Result<Unit> = tokenManager.withAuth(baseUrl, serverId) { token ->
        val response = httpClient.post("${serverOrigin(baseUrl)}/api/v1/files/upload/bookdrop") {
            header("Authorization", "Bearer $token")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = filePartProvider(fileUri),
                            headers = filePartHeaders(displayName, mimeType),
                        )
                    }
                )
            )
            onUpload { sent, total -> onProgress(sent, total ?: -1L) }
        }
        if (!response.status.isSuccess()) {
            throw GrimmoryHttpException(response.status.value, "Book Drop upload failed: ${response.status}")
        }
    }

    /**
     * Streams file bytes lazily from the ContentResolver. Passing the known size lets Ktor emit
     * a Content-Length header so the server-side progress and size validation work normally.
     */
    private fun filePartProvider(uri: Uri): ChannelProvider {
        val size = querySize(uri)
        return ChannelProvider(size = size) {
            context.contentResolver.openInputStream(uri)?.toByteReadChannel()
                ?: ByteReadChannel.Empty
        }
    }

    private fun filePartHeaders(displayName: String, mimeType: String): Headers = Headers.build {
        append(HttpHeaders.ContentType, mimeType)
        append(HttpHeaders.ContentDisposition, "filename=\"${displayName.sanitize()}\"")
    }

    private fun querySize(uri: Uri): Long? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.SIZE),
            null,
            null,
            null,
        ) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx < 0 || it.isNull(idx)) null else it.getLong(idx).takeIf { size -> size >= 0 }
        }
    }

    /** Strip quotes and control characters that would break Content-Disposition parsing. */
    private fun String.sanitize(): String =
        replace('"', '_').replace('\r', '_').replace('\n', '_')
}
