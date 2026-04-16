package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryHttpException
import com.ember.reader.core.grimmory.GrimmoryKoreaderProgress
import com.ember.reader.core.grimmory.GrimmoryTokenManager
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
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
    private val fixedInstant = Instant.parse("2026-04-14T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val syncStatusRepository =
        SyncStatusRepository(com.ember.reader.core.testutil.FakeSyncStatusDao(), clock)
    private lateinit var syncManager: ProgressSyncManager

    private val testServer = server()
    private val testBook = book()

    @BeforeEach
    fun setUp() {
        syncManager = ProgressSyncManager(
            readingProgressRepository,
            grimmoryClient,
            grimmoryTokenManager,
            syncStatusRepository
        )
        every { grimmoryTokenManager.isLoggedIn(any()) } returns true
    }

    @Nested
    inner class PullBestProgress {

        @Test
        fun `kosync higher than grimmory returns kosync result`() = runTest {
            val kosyncProgress = readingProgress(percentage = 0.75f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "KOReader"))

            val detail =
                grimmoryBookDetail(readProgress = 0.50f, koreaderProgress = GrimmoryKoreaderProgress(device = "Grimmory"))
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

            val detail =
                grimmoryBookDetail(readProgress = 0.80f, koreaderProgress = GrimmoryKoreaderProgress(device = "Browser"))
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
        fun `push returns NothingToPush when no local progress exists`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns null

            val result = syncManager.pushProgress(testServer, testBook)

            assertEquals(PushResult.NothingToPush, result)
            assertTrue(result.allSkipped)
            assertFalse(result.anySucceeded)
            coVerify(exactly = 0) { readingProgressRepository.pushKosyncProgress(any(), any(), any()) }
        }

        @Test
        fun `push returns NothingToPush when local progress is zero`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0f)

            val result = syncManager.pushProgress(testServer, testBook)

            assertEquals(PushResult.NothingToPush, result)
        }

        @Test
        fun `push reports partial success when kosync fails but Grimmory succeeds`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))
            val detail = grimmoryBookDetail()
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns Result.success(Unit)

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.anySucceeded)
            assertTrue(result.anyFailed)
            assertFalse(result.allFailed)
            assertTrue(result.grimmory is SourceOutcome.Ok)
            assertTrue(result.kosync is SourceOutcome.Failure)
        }

        @Test
        fun `push reports kosync skipped with NoFileHash when book not downloaded`() = runTest {
            val streamedBook = book(fileHash = null)
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            val detail = grimmoryBookDetail()
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns Result.success(Unit)

            val result = syncManager.pushProgress(testServer, streamedBook)

            assertTrue(result.grimmory is SourceOutcome.Ok)
            val skipped = result.kosync as SourceOutcome.Skipped
            assertEquals(SkipReason.NoFileHash, skipped.reason)
            assertTrue(result.anySucceeded)
            assertFalse(result.anyFailed)
        }

        @Test
        fun `push reports kosync skipped with NoKosyncCreds when username blank`() = runTest {
            val serverNoCreds = server(kosyncUsername = "", kosyncPassword = "")
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            val detail = grimmoryBookDetail()
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns Result.success(Unit)

            val result = syncManager.pushProgress(serverNoCreds, testBook)

            val skipped = result.kosync as SourceOutcome.Skipped
            assertEquals(SkipReason.NoKosyncCreds, skipped.reason)
        }

        @Test
        fun `push reports allFailed when both endpoints fail`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns
                Result.failure(Exception("server error"))

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.allFailed)
            assertTrue(result.anyFailed)
            assertFalse(result.anySucceeded)
        }

        @Test
        fun `firstActionableError prefers GrimmoryAuthExpired over generic errors`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(UnknownHostException("net down"))
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                Result.success(grimmoryBookDetail())
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns
                Result.failure(GrimmoryAuthExpiredException(testServer.id))

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(
                result.firstActionableError() is GrimmoryAuthExpiredException,
                "expected GrimmoryAuthExpiredException, got ${result.firstActionableError()} (kosync=${result.kosync}, grimmory=${result.grimmory})"
            )
        }
    }

    @Nested
    inner class StatusReporting {

        @Test
        fun `pullBestProgress reports success when any source responds`() = runTest {
            val detail = grimmoryBookDetail(readProgress = 0.50f)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            syncManager.pullBestProgress(testServer, testBook)

            assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.Ok)
        }

        @Test
        fun `pullBestProgress reports AuthExpired when Grimmory raises it and kosync is skipped`() =
            runTest {
                val bookNoHash = book(fileHash = null) // skips kosync
                coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                    Result.failure(GrimmoryAuthExpiredException(testServer.id))

                syncManager.pullBestProgress(testServer, bookNoHash)

                assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.AuthExpired)
            }

        @Test
        fun `pullBestProgress does not report when all sources are skipped`() = runTest {
            val bookNoSync = book(fileHash = null, opdsEntryId = "not-a-urn-format")

            syncManager.pullBestProgress(testServer, bookNoSync)

            assertEquals(SyncStatus.Unknown, syncStatusRepository.get(testServer.id))
        }

        @Test
        fun `pullBestProgress prefers AuthExpired over generic HTTP failure when both fail`() =
            runTest {
                coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                    Result.failure(UnknownHostException("grimmory.invalid"))
                coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                    Result.failure(GrimmoryAuthExpiredException(testServer.id))

                syncManager.pullBestProgress(testServer, testBook)

                assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.AuthExpired)
            }

        @Test
        fun `pullBestProgress reports NetworkError when only kosync was attempted and failed offline`() =
            runTest {
                val bookKosyncOnly = book(opdsEntryId = "not-a-urn-format") // skips Grimmory
                coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                    Result.failure(UnknownHostException("grimmory.invalid"))

                syncManager.pullBestProgress(testServer, bookKosyncOnly)

                assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.NetworkError)
            }

        @Test
        fun `pushProgress reports success only when no channel failed`() = runTest {
            val streamedBook = book(fileHash = null) // skips kosync, not a failure
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                Result.success(grimmoryBookDetail())
            coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns Result.success(Unit)

            syncManager.pushProgress(testServer, streamedBook)

            assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.Ok)
        }

        @Test
        fun `pushProgress reports failure when any channel failed (even if another succeeded)`() =
            runTest {
                coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
                coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                    Result.success(grimmoryBookDetail())
                coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                    Result.failure(Exception("kosync down"))
                coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns Result.success(Unit)

                syncManager.pushProgress(testServer, testBook)

                // Partial failure must surface so the user sees the banner — hiding kosync's
                // failure behind Grimmory's success is how the original bug went undetected.
                val status = syncStatusRepository.get(testServer.id)
                assertTrue(status !is SyncStatus.Ok, "expected non-Ok, got $status")
                assertTrue(status !is SyncStatus.Unknown, "expected reported failure, got $status")
            }

        @Test
        fun `pushProgress reports ServerError when all endpoints fail with HTTP errors`() =
            runTest {
                coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
                coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                    Result.success(grimmoryBookDetail())
                coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                    Result.failure(GrimmoryHttpException(500, "kosync 500"))
                coEvery { grimmoryClient.pushProgress(any(), any(), any()) } returns
                    Result.failure(GrimmoryHttpException(502, "grimmory 502"))

                syncManager.pushProgress(testServer, testBook)

                val status = syncStatusRepository.get(testServer.id)
                assertTrue(status is SyncStatus.ServerError)
            }

        @Test
        fun `pushProgress does not report when there is no local progress to push`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns null

            syncManager.pushProgress(testServer, testBook)

            assertEquals(SyncStatus.Unknown, syncStatusRepository.get(testServer.id))
        }
    }
}
