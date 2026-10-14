package com.ember.reader.core.network

import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Timeouts for a request that moves a whole file. The client's default request timeout
 * covers the body too, so it would cut off any transfer longer than it however steadily the
 * bytes came; here there is none, and only a transfer that stalls for [STALL_TIMEOUT_MS] fails.
 */
fun HttpRequestBuilder.fileTransferTimeouts() {
    timeout {
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = STALL_TIMEOUT_MS
    }
}

/**
 * Streams this response's body into [destination].
 *
 * The bytes go to a `.part` file beside it, which takes [destination]'s place only once
 * complete and accepted by [validate]. A transfer that fails, is cancelled or is rejected
 * removes the `.part` file and leaves whatever [destination] held before, so a broken
 * download is never taken for a whole one, nor replaces a good copy.
 *
 * [onProgress] is told the bytes so far and the total (null when unknown), every
 * [PROGRESS_STEP_BYTES] and at the end.
 */
suspend fun HttpResponse.streamTo(
    destination: File,
    onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)? = null,
    validate: (File) -> Unit = {},
) {
    val totalBytes = contentLength()
    val channel = bodyAsChannel()
    val part = partFileFor(destination)
    try {
        withContext(Dispatchers.IO) {
            part.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                var downloaded = 0L
                var reported = 0L
                while (!channel.isClosedForRead) {
                    val bytes = channel.readAvailable(buffer)
                    if (bytes > 0) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (downloaded - reported >= PROGRESS_STEP_BYTES) {
                            reported = downloaded
                            onProgress?.invoke(downloaded, totalBytes)
                        }
                    }
                }
                if (downloaded != reported) onProgress?.invoke(downloaded, totalBytes)
            }
            if (totalBytes != null && part.length() != totalBytes) {
                error("Download ended early: ${part.length()} of $totalBytes bytes")
            }
            validate(part)
            if (!part.renameTo(destination)) {
                // renameTo will not replace an existing file on every filesystem.
                destination.delete()
                if (!part.renameTo(destination)) error("Could not move the download into place: ${destination.name}")
            }
        }
    } finally {
        part.delete()
    }
}

/** The file [destination] is written to while it downloads. */
fun partFileFor(destination: File): File = File(destination.parentFile, destination.name + PART_SUFFIX)

/** Whether [file] is an unfinished download, to be left out of anything that lists downloads. */
fun isPartFile(file: File): Boolean = file.name.endsWith(PART_SUFFIX)

private const val PART_SUFFIX = ".part"
private const val BUFFER_BYTES = 8192
private const val PROGRESS_STEP_BYTES = 256 * 1024L
private const val STALL_TIMEOUT_MS = 120_000L
