package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryKoreaderProgress
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ReadingProgressRepository.KosyncProgressResult
import com.ember.reader.core.testutil.TestFixtures.book
import com.ember.reader.core.testutil.TestFixtures.grimmoryBookDetail
import com.ember.reader.core.testutil.TestFixtures.readingProgress
import com.ember.reader.core.testutil.TestFixtures.server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ProgressSyncManagerTest {

    private val readingProgressRepository: ReadingProgressRepository = mockk(relaxed = true)
    private val grimmoryClient: GrimmoryClient = mockk(relaxed = true)
    private val grimmoryTokenManager: GrimmoryTokenManager = mockk(relaxed = true)
    private lateinit var syncManager: ProgressSyncManager

    private val testServer = server()
    private val testBook = book()

    @BeforeEach
    fun setUp() {
        syncManager = ProgressSyncManager(readingProgressRepository, grimmoryClient, grimmoryTokenManager)
        every { grimmoryTokenManager.isLoggedIn(any()) } returns true
    }

    @Nested
    inner class PullBestProgress {

        @Test
        fun `kosync higher than grimmory returns kosync result`() = runTest {
            val kosyncProgress = readingProgress(percentage = 0.75f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "KOReader"))

            val detail = grimmoryBookDetail(readProgress = 0.50f, koreaderProgress = GrimmoryKoreaderProgress(device = "Grimmory"))
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertNotNull(result)
            assertEquals(0.75f, result!!.progress.percentage)
            assertEquals("KOReader", result.source)
        }

        @Test
        fun `grimmory higher than kosync returns grimmory result`() = runTest {
            val kosyncProgress = readingProgress(percentage = 0.25f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "KOReader"))

            val detail = grimmoryBookDetail(readProgress = 0.80f, koreaderProgress = GrimmoryKoreaderProgress(device = "Browser"))
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertNotNull(result)
            assertEquals(0.80f, result!!.progress.percentage)
            assertEquals("Browser", result.source)
        }

        @Test
        fun `both sources return null yields null`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            val detail = grimmoryBookDetail(readProgress = null)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            val result = syncManager.pullBestProgress(testServer, testBook)
            assertNull(result)
        }

        @Test
        fun `one source fails other succeeds returns the successful one`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))

            val detail = grimmoryBookDetail(readProgress = 0.60f)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertNotNull(result)
            assertEquals(0.60f, result!!.progress.percentage)
        }

        @Test
        fun `skips kosync when no fileHash`() = runTest {
            val bookNoHash = book(fileHash = null)

            val detail = grimmoryBookDetail(readProgress = 0.50f)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            syncManager.pullBestProgress(testServer, bookNoHash)

            coVerify(exactly = 0) { readingProgressRepository.pullKosyncProgress(any(), any(), any()) }
        }

        @Test
        fun `skips kosync when blank kosync credentials`() = runTest {
            val serverNoCreds = server(kosyncUsername = "", kosyncPassword = "")

            val detail = grimmoryBookDetail(readProgress = 0.50f)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            syncManager.pullBestProgress(serverNoCreds, testBook)

            coVerify(exactly = 0) { readingProgressRepository.pullKosyncProgress(any(), any(), any()) }
        }

        @Test
        fun `skips grimmory when no grimmoryBookId`() = runTest {
            val bookNoGrimmory = book(opdsEntryId = "not-a-urn-format")

            val kosyncProgress = readingProgress(percentage = 0.40f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "device"))

            syncManager.pullBestProgress(testServer, bookNoGrimmory)

            coVerify(exactly = 0) { grimmoryClient.getBookDetail(any(), any(), any()) }
        }

        @Test
        fun `skips grimmory when server is not grimmory`() = runTest {
            val nonGrimmoryServer = server(isGrimmory = false)

            val kosyncProgress = readingProgress(percentage = 0.40f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "device"))

            syncManager.pullBestProgress(nonGrimmoryServer, testBook)

            coVerify(exactly = 0) { grimmoryClient.getBookDetail(any(), any(), any()) }
        }

        @Test
        fun `skips grimmory when not logged in`() = runTest {
            every { grimmoryTokenManager.isLoggedIn(any()) } returns false

            val kosyncProgress = readingProgress(percentage = 0.40f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "device"))

            syncManager.pullBestProgress(testServer, testBook)

            coVerify(exactly = 0) { grimmoryClient.getBookDetail(any(), any(), any()) }
        }

        @Test
        fun `grimmory readProgress of 0 is treated as no progress`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            val detail = grimmoryBookDetail(readProgress = 0f)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            val result = syncManager.pullBestProgress(testServer, testBook)
            assertNull(result)
        }

        @Test
        fun `grimmory negative readProgress is treated as no progress`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            val detail = grimmoryBookDetail(readProgress = -0.5f)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)

            val result = syncManager.pullBestProgress(testServer, testBook)
            assertNull(result)
        }
    }

    @Nested
    inner class PushProgress {

        @Test
        fun `push skipped when no local progress exists`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns null

            val result = syncManager.pushProgress(testServer, testBook)

            assertFalse(result)
            coVerify(exactly = 0) { readingProgressRepository.pushKosyncProgress(any(), any(), any()) }
        }

        @Test
        fun `push skipped when local progress is zero`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0f)

            val result = syncManager.pushProgress(testServer, testBook)

            assertFalse(result)
        }

        @Test
        fun `push returns true when at least one endpoint succeeds`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            // Kosync fails
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))
            // Grimmory succeeds
            val detail = grimmoryBookDetail()
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns Result.success(Unit)

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result)
        }

        @Test
        fun `push returns false when all endpoints fail`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))
            // pushGrimmory uses runCatching, so pushProgress must throw to make it fail
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns
                Result.failure(Exception("server error"))

            val result = syncManager.pushProgress(testServer, testBook)

            assertFalse(result)
        }
    }
}
