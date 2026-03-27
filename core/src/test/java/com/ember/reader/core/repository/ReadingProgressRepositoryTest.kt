package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ReadingProgressDao
import com.ember.reader.core.database.entity.ReadingProgressEntity
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import com.ember.reader.core.sync.DeviceIdentity
import com.ember.reader.core.sync.KosyncClient
import com.ember.reader.core.sync.KosyncProgressRequest
import com.ember.reader.core.sync.KosyncProgressResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ReadingProgressRepositoryTest {

    @MockK
    private lateinit var readingProgressDao: ReadingProgressDao

    @MockK
    private lateinit var kosyncClient: KosyncClient

    @MockK
    private lateinit var deviceIdentity: DeviceIdentity

    private lateinit var repository: ReadingProgressRepository

    private val testServer = Server(
        id = 1L,
        name = "Test Server",
        url = "http://localhost",
        opdsUsername = "opds_user",
        opdsPassword = "opds_pass",
        kosyncUsername = "kosync_user",
        kosyncPassword = "kosync_pass"
    )

    @BeforeEach
    fun setUp() {
        repository = ReadingProgressRepository(readingProgressDao, kosyncClient, deviceIdentity)
    }

    @Test
    fun `updateProgress with non-null serverId sets needsSync true`() = runTest {
        val entitySlot = slot<ReadingProgressEntity>()
        coEvery { readingProgressDao.upsert(capture(entitySlot)) } returns Unit

        repository.updateProgress(
            bookId = "book-1",
            serverId = 1L,
            percentage = 0.5f,
            locatorJson = "{\"locator\":\"test\"}"
        )

        assertTrue(entitySlot.captured.needsSync)
        assertEquals(1L, entitySlot.captured.serverId)
    }

    @Test
    fun `updateProgress with null serverId sets needsSync false`() = runTest {
        val entitySlot = slot<ReadingProgressEntity>()
        coEvery { readingProgressDao.upsert(capture(entitySlot)) } returns Unit

        repository.updateProgress(
            bookId = "book-1",
            serverId = null,
            percentage = 0.3f,
            locatorJson = null
        )

        assertFalse(entitySlot.captured.needsSync)
        assertEquals(null, entitySlot.captured.serverId)
    }

    @Test
    fun `pushProgress constructs correct KosyncProgressRequest`() = runTest {
        val progressEntity = ReadingProgressEntity(
            bookId = "book-1",
            serverId = 1L,
            percentage = 0.42f,
            locatorJson = "{\"locator\":\"data\"}",
            kosyncProgress = "{\"locator\":\"data\"}",
            lastReadAt = java.time.Instant.now(),
            needsSync = true
        )
        coEvery { readingProgressDao.getByBookId("book-1") } returns progressEntity

        every { deviceIdentity.deviceName } returns "Ember"
        every { deviceIdentity.deviceId } returns "test-device-id"

        val requestSlot = slot<KosyncProgressRequest>()
        coEvery {
            kosyncClient.pushProgress(any(), any(), any(), capture(requestSlot))
        } returns Result.success(Unit)
        coEvery { readingProgressDao.markSynced(any(), any()) } returns Unit

        repository.pushProgress(testServer, "book-1", "doc-hash-123")

        val request = requestSlot.captured
        assertEquals("doc-hash-123", request.document)
        assertEquals("{\"locator\":\"data\"}", request.progress)
        assertEquals(0.42f, request.percentage)
        assertEquals("Ember", request.device)
        assertEquals("test-device-id", request.deviceId)
    }

    @Test
    fun `pushProgress marks synced on success`() = runTest {
        val progressEntity = ReadingProgressEntity(
            bookId = "book-1",
            serverId = 1L,
            percentage = 0.5f,
            locatorJson = "{}",
            kosyncProgress = "{}",
            lastReadAt = java.time.Instant.now(),
            needsSync = true
        )
        coEvery { readingProgressDao.getByBookId("book-1") } returns progressEntity
        every { deviceIdentity.deviceName } returns "Ember"
        every { deviceIdentity.deviceId } returns "device-id"
        coEvery { kosyncClient.pushProgress(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { readingProgressDao.markSynced(any(), any()) } returns Unit

        val result = repository.pushProgress(testServer, "book-1", "hash")

        assertTrue(result.isSuccess)
        coVerify { readingProgressDao.markSynced("book-1", any()) }
    }

    @Test
    fun `pullProgress returns RemoteProgressResult without writing to DB`() = runTest {
        val response = KosyncProgressResponse(
            document = "doc-hash",
            progress = "{\"locator\":\"remote\"}",
            percentage = 0.8f,
            device = "KOReader",
            deviceId = "remote-device",
            timestamp = 1700000000L
        )
        coEvery {
            kosyncClient.pullProgress(
                baseUrl = testServer.url,
                username = testServer.kosyncUsername,
                password = testServer.kosyncPassword,
                documentHash = "doc-hash"
            )
        } returns Result.success(response)

        val result = repository.pullProgress(testServer, "book-1", "doc-hash")

        assertTrue(result.isSuccess)
        val remoteResult = result.getOrNull()
        assertNotNull(remoteResult)
        assertEquals(0.8f, remoteResult!!.progress.percentage)
        assertEquals("{\"locator\":\"remote\"}", remoteResult.progress.locatorJson)
        assertEquals("KOReader", remoteResult.deviceName)
        assertFalse(remoteResult.progress.needsSync)

        // Verify no writes to the DAO
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.markSynced(any(), any()) }
    }

    @Test
    fun `applyRemoteProgress writes to DB`() = runTest {
        val progress = ReadingProgress(
            bookId = "book-1",
            serverId = 1L,
            percentage = 0.75f,
            locatorJson = "{\"locator\":\"remote\"}",
            kosyncProgress = "{\"locator\":\"remote\"}",
            needsSync = false
        )
        coEvery { readingProgressDao.upsert(any()) } returns Unit

        repository.applyRemoteProgress(progress)

        coVerify { readingProgressDao.upsert(any()) }
    }

    @Test
    fun `syncUnsyncedProgress filters by serverId`() = runTest {
        val unsyncedEntity = ReadingProgressEntity(
            bookId = "book-1",
            serverId = 1L,
            percentage = 0.5f,
            locatorJson = "{}",
            kosyncProgress = "{}",
            lastReadAt = java.time.Instant.now(),
            needsSync = true
        )
        coEvery { readingProgressDao.getUnsyncedProgress(1L) } returns listOf(unsyncedEntity)
        coEvery { readingProgressDao.getByBookId("book-1") } returns unsyncedEntity
        every { deviceIdentity.deviceName } returns "Ember"
        every { deviceIdentity.deviceId } returns "device-id"
        coEvery { kosyncClient.pushProgress(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { readingProgressDao.markSynced(any(), any()) } returns Unit

        repository.syncUnsyncedProgress(testServer) { bookId ->
            if (bookId == "book-1") "hash-1" else null
        }

        coVerify { readingProgressDao.getUnsyncedProgress(testServer.id) }
        coVerify { kosyncClient.pushProgress(any(), any(), any(), any()) }
    }
}
