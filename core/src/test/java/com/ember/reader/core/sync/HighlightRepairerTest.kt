package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.CreateAnnotationRequest
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.testutil.FakeHighlightDao
import com.ember.reader.core.testutil.TestFixtures.grimmoryAnnotation
import com.ember.reader.core.testutil.TestFixtures.highlightEntity
import com.ember.reader.core.testutil.TestFixtures.server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HighlightRepairerTest {

    private val grimmoryClient: GrimmoryClient = mockk(relaxed = true)
    private lateinit var dao: FakeHighlightDao
    private lateinit var repairer: HighlightRepairer

    private val testServer = server()
    private val bookId = "book-1"
    private val grimmoryBookId = 101L

    @BeforeEach
    fun setUp() {
        dao = FakeHighlightDao()
        repairer = HighlightRepairer(dao, grimmoryClient)
        coEvery { grimmoryClient.deleteAnnotation(any(), any(), any()) } returns Result.success(Unit)
    }

    /** Places every highlight it is asked about at [REAL_CFI], and records what it was asked. */
    private val asked = mutableListOf<Highlight>()
    private val placeAll: suspend (List<Highlight>) -> Map<Long, String> = { highlights ->
        asked += highlights
        highlights.associate { it.id to REAL_CFI }
    }

    private suspend fun repair(compute: suspend (List<Highlight>) -> Map<Long, String> = placeAll) =
        repairer.repair(testServer, bookId, grimmoryBookId, compute).getOrThrow()

    @Test
    fun `a highlight pushed with a fake CFI is replaced on the server`() = runTest {
        dao.insert(
            highlightEntity(
                bookId = bookId, color = HighlightColor.BLUE, annotation = "note",
                selectedText = "my text", remoteId = 10,
            )
        )
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(
            listOf(grimmoryAnnotation(id = 10, cfi = "0.4231", chapterTitle = "Ch 2"))
        )
        coEvery { grimmoryClient.createAnnotation(any(), any(), any()) } returns Result.success(
            grimmoryAnnotation(id = 77, cfi = REAL_CFI)
        )

        val summary = repair()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        val request = slot<CreateAnnotationRequest>()
        coVerifyOrder {
            grimmoryClient.createAnnotation(any(), any(), capture(request))
            grimmoryClient.deleteAnnotation(any(), any(), 10)
        }
        assertEquals(REAL_CFI, request.captured.cfi)
        assertEquals("my text", request.captured.text)
        assertEquals(HighlightColor.BLUE.hex, request.captured.color)
        assertEquals("note", request.captured.note)
        assertEquals("Ch 2", request.captured.chapterTitle)
        val local = dao.all.single()
        assertEquals(77L, local.remoteId)
        assertEquals(REAL_CFI, local.cfi)
    }

    @Test
    fun `a repaired highlight that was never placed gets the real CFI in its locator too`() = runTest {
        dao.insert(
            highlightEntity(
                bookId = bookId, selectedText = "my text", remoteId = 10,
                locatorJson = CfiLocatorConverter.buildLocatorJson("0.4231", "my text", "Ch 2"),
            )
        )
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(
            listOf(grimmoryAnnotation(id = 10, cfi = "0.4231"))
        )
        coEvery { grimmoryClient.createAnnotation(any(), any(), any()) } returns Result.success(
            grimmoryAnnotation(id = 77, cfi = REAL_CFI)
        )

        repair()

        val locator = dao.all.single().locatorJson
        assertEquals(REAL_CFI, CfiLocatorConverter.unplacedCfi(locator))
        assertEquals("Ch 2", CfiLocatorConverter.extractTitle(locator))
    }

    @Test
    fun `a highlight made on this device keeps its own locator`() = runTest {
        val readiumLocator = """{"href":"OEBPS/ch2.xhtml","locations":{"progression":0.4},"text":{"highlight":"my text"}}"""
        dao.insert(highlightEntity(bookId = bookId, locatorJson = readiumLocator, remoteId = null))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(emptyList())

        repair()

        assertEquals(readiumLocator, dao.all.single().locatorJson)
        assertEquals(REAL_CFI, dao.all.single().cfi)
    }

    @Test
    fun `a highlight made in the web reader takes the server's CFI and is not replaced`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, remoteId = 10))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(
            listOf(grimmoryAnnotation(id = 10, cfi = OTHER_REAL_CFI))
        )

        val summary = repair()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        assertEquals(OTHER_REAL_CFI, dao.all.single().cfi)
        assertTrue(asked.isEmpty())
        coVerify(exactly = 0) { grimmoryClient.createAnnotation(any(), any(), any()) }
        coVerify(exactly = 0) { grimmoryClient.deleteAnnotation(any(), any(), any()) }
    }

    @Test
    fun `a highlight that never synced only gets its CFI stored`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, remoteId = null))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(emptyList())

        val summary = repair()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        assertEquals(REAL_CFI, dao.all.single().cfi)
        assertNull(dao.all.single().remoteId)
        coVerify(exactly = 0) { grimmoryClient.createAnnotation(any(), any(), any()) }
    }

    @Test
    fun `a highlight whose text cannot be placed is left alone and counted`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, remoteId = 10))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(
            listOf(grimmoryAnnotation(id = 10, cfi = "0.4231"))
        )

        val summary = repair { emptyMap() }

        assertEquals(RepairSummary(repaired = 0, unresolved = 1), summary)
        assertNull(dao.all.single().cfi)
        assertEquals(10L, dao.all.single().remoteId)
        coVerify(exactly = 0) { grimmoryClient.createAnnotation(any(), any(), any()) }
        coVerify(exactly = 0) { grimmoryClient.deleteAnnotation(any(), any(), any()) }
    }

    @Test
    fun `when the replacement cannot be created nothing changes`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, remoteId = 10))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(
            listOf(grimmoryAnnotation(id = 10, cfi = "0.4231"))
        )
        coEvery { grimmoryClient.createAnnotation(any(), any(), any()) } returns Result.failure(Exception("500"))

        val summary = repair()

        assertEquals(RepairSummary(repaired = 0, unresolved = 1), summary)
        assertEquals(10L, dao.all.single().remoteId)
        assertNull(dao.all.single().cfi)
        coVerify(exactly = 0) { grimmoryClient.deleteAnnotation(any(), any(), any()) }
    }

    @Test
    fun `a failed delete keeps the replacement and a second run removes the leftover`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, remoteId = 10))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(
            listOf(grimmoryAnnotation(id = 10, cfi = "0.4231"))
        )
        coEvery { grimmoryClient.createAnnotation(any(), any(), any()) } returns Result.success(
            grimmoryAnnotation(id = 77, cfi = REAL_CFI)
        )
        coEvery { grimmoryClient.deleteAnnotation(any(), any(), 10) } returns Result.failure(Exception("503"))

        repair()

        assertEquals(77L, dao.all.single().remoteId)

        // The old annotation is still on the server, and a sync has pulled it in as a new row.
        dao.insert(highlightEntity(bookId = bookId, remoteId = 10))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.success(
            listOf(grimmoryAnnotation(id = 10, cfi = "0.4231"), grimmoryAnnotation(id = 77, cfi = REAL_CFI))
        )
        coEvery { grimmoryClient.deleteAnnotation(any(), any(), 10) } returns Result.success(Unit)

        repair()

        assertEquals(listOf(77L), dao.all.map { it.remoteId })
        coVerify(exactly = 1) { grimmoryClient.createAnnotation(any(), any(), any()) }
        coVerify(exactly = 2) { grimmoryClient.deleteAnnotation(any(), any(), 10) }
    }

    @Test
    fun `highlights that already have a real CFI and deleted ones are not touched`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, cfi = OTHER_REAL_CFI, remoteId = 10))
        dao.insert(highlightEntity(bookId = bookId, remoteId = 11, deletedAt = java.time.Instant.EPOCH))

        val summary = repair()

        assertEquals(RepairSummary(repaired = 0, unresolved = 0), summary)
        coVerify(exactly = 0) { grimmoryClient.getAnnotations(any(), any(), any()) }
    }

    @Test
    fun `a book without a server only stores CFIs`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, remoteId = null))

        val summary = repairer.repair(server = null, bookId = bookId, grimmoryBookId = null, placeAll).getOrThrow()

        assertEquals(RepairSummary(repaired = 1, unresolved = 0), summary)
        assertEquals(REAL_CFI, dao.all.single().cfi)
        coVerify(exactly = 0) { grimmoryClient.getAnnotations(any(), any(), any()) }
    }

    @Test
    fun `a server that cannot be reached fails the repair without changing anything`() = runTest {
        dao.insert(highlightEntity(bookId = bookId, remoteId = 10))
        coEvery { grimmoryClient.getAnnotations(any(), any(), any()) } returns Result.failure(Exception("offline"))

        val result = repairer.repair(testServer, bookId, grimmoryBookId, placeAll)

        assertTrue(result.isFailure)
        assertNull(dao.all.single().cfi)
        assertTrue(asked.isEmpty())
    }

    private companion object {
        const val REAL_CFI = "epubcfi(/6/8!/4/2,/1:0,/1:7)"
        const val OTHER_REAL_CFI = "epubcfi(/6/4!/4/6,/1:3,/1:9)"
    }
}
