package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAppBookFile
import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryEpubProgress
import com.ember.reader.core.grimmory.GrimmoryHttpException
import com.ember.reader.core.grimmory.GrimmoryKoreaderProgress
import com.ember.reader.core.grimmory.GrimmoryPdfProgress
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.grimmory.GrimmoryUpdateProgressRequest
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import com.ember.reader.core.repository.ReadingProgressRepository.KosyncProgressResult
import com.ember.reader.core.testutil.TestFixtures.book
import com.ember.reader.core.testutil.TestFixtures.grimmoryAppBookProgress
import com.ember.reader.core.testutil.TestFixtures.grimmoryBookDetail
import com.ember.reader.core.testutil.TestFixtures.readingProgress
import com.ember.reader.core.testutil.TestFixtures.server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
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
    private val bookRepository: BookRepository = mockk(relaxed = true)
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
            bookRepository,
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

            val progress =
                grimmoryAppBookProgress(readProgress = 0.50f, koreaderProgress = GrimmoryKoreaderProgress(device = "Grimmory"))
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

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

            val progress =
                grimmoryAppBookProgress(readProgress = 0.80f, koreaderProgress = GrimmoryKoreaderProgress(device = "Browser"))
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertNotNull(result)
            assertEquals(0.80f, result!!.progress.percentage)
            assertEquals("Browser", result.source)
        }

        @Test
        fun `both sources return null yields null`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            val progress = grimmoryAppBookProgress(readProgress = null)
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)
            assertNull(result)
        }

        @Test
        fun `one source fails other succeeds returns the successful one`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))

            val progress = grimmoryAppBookProgress(readProgress = 0.60f)
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertNotNull(result)
            assertEquals(0.60f, result!!.progress.percentage)
        }

        @Test
        fun `skips kosync when no fileHash`() = runTest {
            val bookNoHash = book(fileHash = null)

            val progress = grimmoryAppBookProgress(readProgress = 0.50f)
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            syncManager.pullBestProgress(testServer, bookNoHash)

            coVerify(exactly = 0) { readingProgressRepository.pullKosyncProgress(any(), any(), any()) }
        }

        @Test
        fun `skips kosync when blank kosync credentials`() = runTest {
            val serverNoCreds = server(kosyncUsername = "", kosyncPassword = "")

            val progress = grimmoryAppBookProgress(readProgress = 0.50f)
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

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

            coVerify(exactly = 0) { grimmoryClient.getAppBookProgress(any(), any(), any()) }
        }

        @Test
        fun `skips grimmory when server is not grimmory`() = runTest {
            val nonGrimmoryServer = server(isGrimmory = false)

            val kosyncProgress = readingProgress(percentage = 0.40f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "device"))

            syncManager.pullBestProgress(nonGrimmoryServer, testBook)

            coVerify(exactly = 0) { grimmoryClient.getAppBookProgress(any(), any(), any()) }
        }

        @Test
        fun `skips grimmory when not logged in`() = runTest {
            every { grimmoryTokenManager.isLoggedIn(any()) } returns false

            val kosyncProgress = readingProgress(percentage = 0.40f)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(kosyncProgress, "device"))

            syncManager.pullBestProgress(testServer, testBook)

            coVerify(exactly = 0) { grimmoryClient.getAppBookProgress(any(), any(), any()) }
        }

        @Test
        fun `grimmory pull takes epub progress when the koreader value is stale`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)
            val progress = grimmoryAppBookProgress(
                readProgress = 0.334152f,
                koreaderProgress = GrimmoryKoreaderProgress(percentage = 0.334152f, device = "Ember"),
                epubProgress = GrimmoryEpubProgress(cfi = "epubcfi(/6/2)", percentage = 34.4f)
            )
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertEquals(0.344f, result!!.progress.percentage, 0.0001f)
        }

        @Test
        fun `grimmory pull carries the real CFI of the epub progress`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)
            val progress = grimmoryAppBookProgress(
                readProgress = 34.4f,
                // What Grimmory mirrors from Ember's own kosync push: same position, more digits.
                koreaderProgress = GrimmoryKoreaderProgress(percentage = 0.34437f, device = "Ember"),
                epubProgress = GrimmoryEpubProgress(cfi = REAL_CFI, percentage = 34.4f)
            )
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertEquals(REAL_CFI, result!!.progress.cfi)
        }

        @Test
        fun `grimmory pull carries no CFI for the placeholder`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)
            val progress = grimmoryAppBookProgress(
                readProgress = 34.4f,
                epubProgress = GrimmoryEpubProgress(cfi = ProgressSyncManager.PLACEHOLDER_CFI, percentage = 34.4f)
            )
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertNull(result!!.progress.cfi)
        }

        @Test
        fun `grimmory pull carries no CFI when the koreader record is further along`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)
            val progress = grimmoryAppBookProgress(
                readProgress = 0.5f,
                koreaderProgress = GrimmoryKoreaderProgress(percentage = 0.5f, device = "KOReader"),
                epubProgress = GrimmoryEpubProgress(cfi = REAL_CFI, percentage = 34.4f)
            )
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertEquals(0.5f, result!!.progress.percentage, 0.0001f)
            assertNull(result.progress.cfi)
        }

        @Test
        fun `kosync result takes Grimmory's CFI when both describe the same position`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(readingProgress(percentage = 0.34437f), "Ember"))
            val progress = grimmoryAppBookProgress(
                readProgress = 34.4f,
                epubProgress = GrimmoryEpubProgress(cfi = REAL_CFI, percentage = 34.4f)
            )
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertEquals(0.34437f, result!!.progress.percentage, 0.0001f)
            assertEquals(REAL_CFI, result.progress.cfi)
        }

        @Test
        fun `kosync result further along than Grimmory takes no CFI`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(KosyncProgressResult(readingProgress(percentage = 0.6f), "KOReader"))
            val progress = grimmoryAppBookProgress(
                readProgress = 34.4f,
                epubProgress = GrimmoryEpubProgress(cfi = REAL_CFI, percentage = 34.4f)
            )
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertNull(result!!.progress.cfi)
        }

        @Test
        fun `grimmory pull takes koreader progress when the epub value is stale`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)
            val progress = grimmoryAppBookProgress(
                readProgress = 0.21f,
                koreaderProgress = GrimmoryKoreaderProgress(percentage = 0.21f, device = "Ember"),
                epubProgress = GrimmoryEpubProgress(cfi = "epubcfi(/6/2)", percentage = 20f)
            )
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertEquals(0.21f, result!!.progress.percentage, 0.0001f)
        }

        @Test
        fun `grimmory pull takes pdf progress when it is the furthest along`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)
            val progress = grimmoryAppBookProgress(
                readProgress = 0.21f,
                koreaderProgress = GrimmoryKoreaderProgress(percentage = 0.21f, device = "Ember")
            ).copy(pdfProgress = GrimmoryPdfProgress(page = 80, percentage = 34.2f))
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)

            assertEquals(0.342f, result!!.progress.percentage, 0.0001f)
        }

        @Test
        fun `grimmory readProgress of 0 is treated as no progress`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            val progress = grimmoryAppBookProgress(readProgress = 0f)
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

            val result = syncManager.pullBestProgress(testServer, testBook)
            assertNull(result)
        }

        @Test
        fun `grimmory negative readProgress is treated as no progress`() = runTest {
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            val progress = grimmoryAppBookProgress(readProgress = -0.5f)
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)

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
        fun `push sends the real CFI of the saved position when there is one`() = runTest {
            val cfi = "epubcfi(/6/14!/4/22/1:152)"
            coEvery { readingProgressRepository.getByBookId(any()) } returns
                readingProgress(percentage = 0.5f, cfi = cfi)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(files = listOf(GrimmoryAppBookFile(id = 42L, bookType = "EPUB", isPrimary = true)))
            )
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            syncManager.pushProgress(testServer, testBook)

            assertEquals(cfi, request.captured.fileProgress?.positionData)
            assertEquals(cfi, request.captured.epubProgress?.cfi)
        }

        @Test
        fun `push without a real CFI sends the chapter and the place in it`() = runTest {
            val locator = """{"href":"OEBPS/ch3.xhtml","locations":{"progression":0.254,"totalProgression":0.5}}"""
            coEvery { readingProgressRepository.getByBookId(any()) } returns
                readingProgress(percentage = 0.5f, locatorJson = locator)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(files = listOf(GrimmoryAppBookFile(id = 42L, bookType = "EPUB", isPrimary = true)))
            )
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            syncManager.pushProgress(testServer, testBook)

            assertEquals(ProgressSyncManager.PLACEHOLDER_CFI, request.captured.fileProgress?.positionData)
            assertEquals("OEBPS/ch3.xhtml", request.captured.fileProgress?.positionHref)
            assertEquals(25.4f, request.captured.fileProgress?.contentSourceProgressPercent)
        }

        @Test
        fun `push with a real CFI leaves the chapter for the server to work out`() = runTest {
            val locator = """{"href":"OEBPS/ch3.xhtml","locations":{"progression":0.254}}"""
            coEvery { readingProgressRepository.getByBookId(any()) } returns
                readingProgress(percentage = 0.5f, locatorJson = locator, cfi = "epubcfi(/6/14!/4/22/1:152)")
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(files = listOf(GrimmoryAppBookFile(id = 42L, bookType = "EPUB", isPrimary = true)))
            )
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            syncManager.pushProgress(testServer, testBook)

            assertNull(request.captured.fileProgress?.positionHref)
            assertNull(request.captured.fileProgress?.contentSourceProgressPercent)
        }

        @Test
        fun `push falls back to the placeholder for a saved CFI that is not real`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns
                readingProgress(percentage = 0.5f, cfi = "0.4231")
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(files = listOf(GrimmoryAppBookFile(id = 42L, bookType = "EPUB", isPrimary = true)))
            )
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            syncManager.pushProgress(testServer, testBook)

            assertEquals(ProgressSyncManager.PLACEHOLDER_CFI, request.captured.fileProgress?.positionData)
            assertEquals(ProgressSyncManager.PLACEHOLDER_CFI, request.captured.epubProgress?.cfi)
        }

        @Test
        fun `push sends file-level progress for the primary file with the placeholder CFI when no CFI was saved`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(
                    files = listOf(
                        GrimmoryAppBookFile(id = 7L, bookType = "PDF", isPrimary = false),
                        GrimmoryAppBookFile(id = 42L, bookType = "EPUB", isPrimary = true)
                    )
                )
            )
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.grimmory is SourceOutcome.Ok)
            assertEquals(42L, request.captured.fileProgress?.bookFileId)
            assertEquals(ProgressSyncManager.PLACEHOLDER_CFI, request.captured.fileProgress?.positionData)
            assertEquals(50f, request.captured.fileProgress?.progressPercent)
            assertEquals(ProgressSyncManager.PLACEHOLDER_CFI, request.captured.epubProgress?.cfi)
            assertEquals(50f, request.captured.epubProgress?.percentage)
        }

        @Test
        fun `push falls back to epubProgress only when the detail has no primary file`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                Result.success(grimmoryBookDetail(files = null))
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.grimmory is SourceOutcome.Ok)
            assertNull(request.captured.fileProgress)
            assertEquals(ProgressSyncManager.PLACEHOLDER_CFI, request.captured.epubProgress?.cfi)
        }

        @Test
        fun `push retries without fileProgress when Grimmory rejects the file id with 404`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(grimmoryBookDetail())
            val requests = mutableListOf<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(requests)) } returnsMany listOf(
                Result.failure(GrimmoryHttpException(404, "Book file not found")),
                Result.success(Unit)
            )

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.grimmory is SourceOutcome.Ok)
            assertEquals(2, requests.size)
            assertNotNull(requests[0].fileProgress)
            assertNull(requests[1].fileProgress)
            assertEquals(50f, requests[1].epubProgress?.percentage)
        }

        @Test
        fun `push for a PDF sends the page number and no CFI`() = runTest {
            // Page 80 of 232 is saved as 79 / 231.
            coEvery { readingProgressRepository.getByBookId(any()) } returns
                readingProgress(percentage = 79f / 231f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(files = listOf(GrimmoryAppBookFile(id = 42L, bookType = "PDF", isPrimary = true)))
                    .copy(pageCount = 232)
            )
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.grimmory is SourceOutcome.Ok)
            assertEquals(42L, request.captured.fileProgress?.bookFileId)
            assertEquals("80", request.captured.fileProgress?.positionData)
            assertEquals(34.2f, request.captured.fileProgress?.progressPercent)
            assertNull(request.captured.epubProgress)
            assertNull(request.captured.pdfProgress)
        }

        @Test
        fun `push for a PDF takes the page from the locator when the server has no page count`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns
                readingProgress(percentage = 0.5f, locatorJson = """{"href":"book.pdf","locations":{"position":12}}""")
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(files = listOf(GrimmoryAppBookFile(id = 42L, bookType = "PDF", isPrimary = true)))
            )
            val request = slot<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(request)) } returns
                Result.success(Unit)

            syncManager.pushProgress(testServer, testBook)

            assertEquals("12", request.captured.fileProgress?.positionData)
        }

        @Test
        fun `push for a PDF with no known file sends pdfProgress, and again after a 404`() = runTest {
            val pdfBook = book(format = BookFormat.PDF)
            coEvery { readingProgressRepository.getByBookId(any()) } returns
                readingProgress(percentage = 0.5f, locatorJson = """{"href":"book.pdf","locations":{"position":12}}""")
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                Result.failure(RuntimeException("offline"))
            val requests = mutableListOf<GrimmoryUpdateProgressRequest>()
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(requests)) } returns
                Result.success(Unit)

            syncManager.pushProgress(testServer, pdfBook)

            assertNull(requests.single().fileProgress)
            assertEquals(GrimmoryPdfProgress(page = 12, percentage = 50f), requests.single().pdfProgress)

            requests.clear()
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(
                grimmoryBookDetail(files = listOf(GrimmoryAppBookFile(id = 42L, bookType = "PDF", isPrimary = true)))
            )
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), capture(requests)) } returnsMany listOf(
                Result.failure(GrimmoryHttpException(404, "Book file not found")),
                Result.success(Unit)
            )

            val result = syncManager.pushProgress(testServer, pdfBook)

            assertTrue(result.grimmory is SourceOutcome.Ok)
            assertEquals(2, requests.size)
            assertEquals(GrimmoryPdfProgress(page = 12, percentage = 50f), requests[1].pdfProgress)
            assertNull(requests[1].fileProgress)
        }

        @Test
        fun `push does not retry on non-404 Grimmory failures`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(grimmoryBookDetail())
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns
                Result.failure(GrimmoryHttpException(500, "boom"))

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.grimmory is SourceOutcome.Failure)
            coVerify(exactly = 1) { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) }
        }

        @Test
        fun `push sends Grimmory progress before kosync`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns Result.success(Unit)
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(grimmoryBookDetail())
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

            syncManager.pushProgress(testServer, testBook)

            coVerifyOrder {
                grimmoryClient.putAppBookProgress(any(), any(), any(), any())
                readingProgressRepository.pushKosyncProgress(any(), any(), any())
            }
        }

        @Test
        fun `placeholder CFI has no indirection step`() {
            // Grimmory would convert a parseable CFI into a start-of-book KOReader position.
            assertFalse(ProgressSyncManager.PLACEHOLDER_CFI.contains('!'))
        }

        @Test
        fun `push reports partial success when kosync fails but Grimmory succeeds`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))
            val detail = grimmoryBookDetail()
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns Result.success(detail)
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

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
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

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
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

            val result = syncManager.pushProgress(serverNoCreds, testBook)

            val skipped = result.kosync as SourceOutcome.Skipped
            assertEquals(SkipReason.NoKosyncCreds, skipped.reason)
        }

        @Test
        fun `push reports allFailed when both endpoints fail`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(Exception("timeout"))
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns
                Result.failure(Exception("server error"))

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(result.allFailed)
            assertTrue(result.anyFailed)
            assertFalse(result.anySucceeded)
        }

        @Test
        fun `kosync 404 triggers file refresh and retries with new hash`() = runTest {
            val originalHash = "old-hash"
            val newHash = "new-hash"
            val bookWithOldHash = book(fileHash = originalHash)
            val bookWithNewHash = bookWithOldHash.copy(fileHash = newHash)

            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)

            // First push (with old hash) 404s; second push (with refreshed hash) succeeds.
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), originalHash) } returns
                Result.failure(KosyncHttpException(404, "Book not found"))
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), newHash) } returns
                Result.success(Unit)

            // Before refresh, getById returns old hash (no concurrent refresh yet).
            // After refresh, the returned Book carries the new hash.
            coEvery { bookRepository.getById(bookWithOldHash.id) } returns bookWithOldHash
            coEvery { bookRepository.refreshDownloadedFile(bookWithOldHash.id) } returns
                Result.success(bookWithNewHash)

            // Grimmory push — keep the other channel clean so we can isolate the kosync retry.
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                Result.success(grimmoryBookDetail())
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

            val result = syncManager.pushProgress(testServer, bookWithOldHash)

            assertTrue(result.kosync is SourceOutcome.Ok, "expected kosync OK after retry, got ${result.kosync}")
            assertTrue(result.grimmory is SourceOutcome.Ok)
            coVerify(exactly = 1) { bookRepository.refreshDownloadedFile(bookWithOldHash.id) }
            coVerify(exactly = 1) { readingProgressRepository.pushKosyncProgress(any(), any(), originalHash) }
            coVerify(exactly = 1) { readingProgressRepository.pushKosyncProgress(any(), any(), newHash) }
        }

        @Test
        fun `kosync 404 does not retry when refresh produces same hash`() = runTest {
            val sameHash = "same-hash"
            val bookStatic = book(fileHash = sameHash)

            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), sameHash) } returns
                Result.failure(KosyncHttpException(404, "Book not found"))
            coEvery { bookRepository.refreshDownloadedFile(bookStatic.id) } returns Result.success(bookStatic)
            coEvery { bookRepository.getById(bookStatic.id) } returns bookStatic
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                Result.success(grimmoryBookDetail())
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

            val result = syncManager.pushProgress(testServer, bookStatic)

            assertTrue(result.kosync is SourceOutcome.Failure, "expected failure when hash unchanged")
            coVerify(exactly = 1) { readingProgressRepository.pushKosyncProgress(any(), any(), sameHash) }
        }

        @Test
        fun `firstActionableError prefers GrimmoryAuthExpired over generic errors`() = runTest {
            coEvery { readingProgressRepository.getByBookId(any()) } returns readingProgress(percentage = 0.5f)
            coEvery { readingProgressRepository.pushKosyncProgress(any(), any(), any()) } returns
                Result.failure(UnknownHostException("net down"))
            coEvery { grimmoryClient.getBookDetail(any(), any(), any()) } returns
                Result.success(grimmoryBookDetail())
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns
                Result.failure(GrimmoryAuthExpiredException(testServer.id))

            val result = syncManager.pushProgress(testServer, testBook)

            assertTrue(
                result.firstActionableError() is GrimmoryAuthExpiredException,
                "expected GrimmoryAuthExpiredException, got ${result.firstActionableError()} (kosync=${result.kosync}, grimmory=${result.grimmory})"
            )
        }
    }

    @Nested
    inner class DisabledChannels {

        @Test
        fun `pushProgress skips kosync when kosyncEnabled is false`() = runTest {
            val disabled = testServer.copy(kosyncEnabled = false)
            coEvery { readingProgressRepository.getByBookId(testBook.id) } returns
                readingProgress(percentage = 0.5f)

            val result = syncManager.pushProgress(disabled, testBook)

            assertTrue(result.kosync is SourceOutcome.Skipped)
            assertEquals(SkipReason.KosyncDisabled, (result.kosync as SourceOutcome.Skipped).reason)
            coVerify(exactly = 0) { readingProgressRepository.pushKosyncProgress(any(), any(), any()) }
        }

        @Test
        fun `pullBestProgress skips kosync when kosyncEnabled is false`() = runTest {
            val disabled = testServer.copy(kosyncEnabled = false)

            val result = syncManager.pullBestProgress(disabled, testBook)

            assertNull(result)
            coVerify(exactly = 0) { readingProgressRepository.pullKosyncProgress(any(), any(), any()) }
        }
    }

    @Nested
    inner class StatusReporting {

        @Test
        fun `pullBestProgress reports success when any source responds`() = runTest {
            val progress = grimmoryAppBookProgress(readProgress = 0.50f)
            coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns Result.success(progress)
            coEvery { readingProgressRepository.pullKosyncProgress(any(), any(), any()) } returns
                Result.success(null)

            syncManager.pullBestProgress(testServer, testBook)

            assertTrue(syncStatusRepository.get(testServer.id) is SyncStatus.Ok)
        }

        @Test
        fun `pullBestProgress reports AuthExpired when Grimmory raises it and kosync is skipped`() =
            runTest {
                val bookNoHash = book(fileHash = null) // skips kosync
                coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns
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
                coEvery { grimmoryClient.getAppBookProgress(any(), any(), any()) } returns
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
            coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

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
                coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns Result.success(Unit)

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
                coEvery { grimmoryClient.putAppBookProgress(any(), any(), any(), any()) } returns
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

    private companion object {
        const val REAL_CFI = "epubcfi(/6/14!/4/22/1:152)"
    }
}
