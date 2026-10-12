package com.ember.reader.ui.catalog

import androidx.lifecycle.SavedStateHandle
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryBookSummary
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.CatalogEntryType
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.CatalogLayoutPreferencesRepository
import com.ember.reader.core.repository.CatalogPreferencesRepository
import com.ember.reader.core.repository.CatalogSeriesViewMode
import com.ember.reader.core.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** The reading shortcuts' cover rows on a Grimmory catalogue's first page. */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class CatalogViewModelShelfPreviewTest {

    @MockK
    private lateinit var serverRepository: ServerRepository

    @MockK
    private lateinit var opdsClient: OpdsClient

    @MockK
    private lateinit var grimmoryAppClient: GrimmoryAppClient

    @MockK
    private lateinit var grimmoryClient: GrimmoryClient

    @MockK
    private lateinit var bookRepository: BookRepository

    @MockK
    private lateinit var grimmoryTokenManager: GrimmoryTokenManager

    @MockK
    private lateinit var catalogPreferencesRepository: CatalogPreferencesRepository

    @MockK
    private lateinit var catalogLayoutPreferencesRepository: CatalogLayoutPreferencesRepository

    private val testDispatcher = StandardTestDispatcher()

    private val server = Server(
        id = 7L,
        name = "Grimmory",
        url = "http://localhost:8080",
        opdsUsername = "u",
        opdsPassword = "p",
        kosyncUsername = "ku",
        kosyncPassword = "kp",
        isGrimmory = true
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { serverRepository.getById(7L) } returns server
        every { grimmoryTokenManager.isLoggedIn(7L) } returns true
        val url = server.url
        coEvery { grimmoryAppClient.getFullLibraries(url, 7L) } returns Result.success(emptyList())
        coEvery { grimmoryAppClient.getShelves(url, 7L) } returns Result.success(emptyList())
        coEvery { grimmoryAppClient.getMagicShelves(url, 7L) } returns Result.success(emptyList())
        every { grimmoryAppClient.coverUrl(any(), any(), any()) } answers { "cover/${secondArg<Long>()}" }
        every { catalogPreferencesRepository.observePreferences(7L) } returns emptyFlow()
        every { catalogLayoutPreferencesRepository.seriesViewModeFlow } returns
            flowOf(CatalogSeriesViewMode.GRID)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun rootCatalogue() = CatalogViewModel(
        savedStateHandle = SavedStateHandle(mapOf("serverId" to 7L, "path" to "")),
        serverRepository = serverRepository,
        opdsClient = opdsClient,
        grimmoryAppClient = grimmoryAppClient,
        grimmoryClient = grimmoryClient,
        bookRepository = bookRepository,
        grimmoryTokenManager = grimmoryTokenManager,
        catalogPreferencesRepository = catalogPreferencesRepository,
        catalogLayoutPreferencesRepository = catalogLayoutPreferencesRepository
    )

    @Test
    fun `each shortcut gets the books the server returns, with their local ids`() = runTest(testDispatcher) {
        val reading = listOf(GrimmoryBookSummary(id = 1, title = "Dune", authors = listOf("Frank Herbert", "Other")))
        val recent = listOf(GrimmoryBookSummary(id = 2, title = "Emma"), GrimmoryBookSummary(id = 3, title = "Ulysses"))
        coEvery { grimmoryClient.getContinueReading(server.url, server.id, any()) } returns Result.success(reading)
        coEvery { grimmoryAppClient.getRecentlyAdded(server.url, server.id, any()) } returns Result.success(recent)
        coEvery { bookRepository.upsertGrimmorySummaries(server, reading) } returns listOf("local-1")
        coEvery { bookRepository.upsertGrimmorySummaries(server, recent) } returns listOf("local-2", "local-3")

        val vm = rootCatalogue()
        advanceUntilIdle()

        val previews = vm.shelfPreviews.value
        assertEquals(
            listOf(CatalogShelfBook("local-1", "Dune", "Frank Herbert", "cover/1")),
            previews[CatalogEntryType.CONTINUE_READING]
        )
        assertEquals(listOf("local-2", "local-3"), previews[CatalogEntryType.RECENTLY_ADDED]?.map { it.localBookId })
    }

    @Test
    fun `a shortcut whose request fails ends up empty without affecting the other`() = runTest(testDispatcher) {
        val recent = listOf(GrimmoryBookSummary(id = 2, title = "Emma"))
        coEvery { grimmoryClient.getContinueReading(server.url, server.id, any()) } returns
            Result.failure(RuntimeException("boom"))
        coEvery { grimmoryAppClient.getRecentlyAdded(server.url, server.id, any()) } returns Result.success(recent)
        coEvery { bookRepository.upsertGrimmorySummaries(server, recent) } returns listOf("local-2")

        val vm = rootCatalogue()
        advanceUntilIdle()

        val previews = vm.shelfPreviews.value
        assertTrue(previews.getValue(CatalogEntryType.CONTINUE_READING).isEmpty())
        assertEquals(1, previews.getValue(CatalogEntryType.RECENTLY_ADDED).size)
        assertTrue(vm.uiState.value !is CatalogUiState.Error)
    }
}
