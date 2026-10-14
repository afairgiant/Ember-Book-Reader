package com.ember.reader.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class FileTransfersTest {

    @TempDir
    lateinit var dir: File

    private fun client(body: ByteArray, contentLength: Long = body.size.toLong()) = HttpClient(
        MockEngine {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, contentLength.toString())
            )
        }
    )

    private suspend fun download(
        client: HttpClient,
        destination: File,
        onProgress: ((Long, Long?) -> Unit)? = null,
        validate: (File) -> Unit = {},
    ) = client.prepareGet("http://server/file").execute { it.streamTo(destination, onProgress, validate) }

    @Test
    fun `a whole download takes the destination's place and leaves no part file`() = runTest {
        val destination = File(dir, "book.epub")
        val body = ByteArray(1000) { it.toByte() }

        download(client(body), destination)

        assertTrue(destination.readBytes().contentEquals(body))
        assertFalse(partFileFor(destination).exists())
    }

    @Test
    fun `a download that ends early keeps the copy already there`() = runTest {
        val destination = File(dir, "book.epub").apply { writeText("the good copy") }

        assertThrows<IllegalStateException> {
            download(client(ByteArray(500), contentLength = 1000), destination)
        }

        assertEquals("the good copy", destination.readText())
        assertFalse(partFileFor(destination).exists())
    }

    @Test
    fun `a download rejected by validate keeps the copy already there`() = runTest {
        val destination = File(dir, "book.epub").apply { writeText("the good copy") }

        assertThrows<IllegalStateException> {
            download(client(ByteArray(1000)), destination, validate = { error("an error page") })
        }

        assertEquals("the good copy", destination.readText())
        assertFalse(partFileFor(destination).exists())
    }

    @Test
    fun `a failed first download leaves nothing that looks downloaded`() = runTest {
        val destination = File(dir, "track.m4a")

        runCatching { download(client(ByteArray(500), contentLength = 1000), destination) }

        assertFalse(destination.exists())
        assertFalse(partFileFor(destination).exists())
    }

    @Test
    fun `a download cancelled partway leaves neither a part file nor a destination`() = runTest {
        val destination = File(dir, "track.m4a")

        // Cancelled once some of it is written, as the Cancel button does mid-transfer.
        assertThrows<CancellationException> {
            download(client(ByteArray(1_000_000)), destination, onProgress = { _, _ -> throw CancellationException("cancelled") })
        }

        assertFalse(destination.exists())
        assertFalse(partFileFor(destination).exists())
    }

    @Test
    fun `progress is told in steps and once at the end, not for every buffer`() = runTest {
        val body = ByteArray(1_000_000)
        val reports = mutableListOf<Long>()

        download(client(body), File(dir, "big.m4b"), onProgress = { read, _ -> reports += read })

        assertEquals(1_000_000L, reports.last())
        assertTrue(reports.size in 2..10, "expected a few reports, got ${reports.size}")
    }
}
