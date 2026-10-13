package com.ember.reader.ui.catalog

import androidx.lifecycle.SavedStateHandle
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryAppPage
import com.ember.reader.core.grimmory.GrimmoryBookSummary
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryLibraryFull
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.CatalogEntryType
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.opds.OpdsFeed
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.CatalogLayoutPreferencesRepository
import com.ember.reader.core.repository.CatalogPreferencesRepository
import com.ember.reader.core.repository.CatalogSeriesViewMode
import com.ember.reader.core.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        every { serverRepository.observeConnectionChanges(any()) } returns emptyFlow()
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

    private fun summary(id: Long, title: String, vararg authors: String) =
        GrimmoryBookSummary(id = id, title = title, authors = authors.toList())

    private fun stubShortcuts(
        reading: Result<List<GrimmoryBookSummary>>,
        recent: Result<List<GrimmoryBookSummary>>
    ) {
        coEvery {
            grimmoryClient.getContinueReading(server.url, 7L, any())
        } returns reading
        coEvery {
            grimmoryAppClient.getRecentlyAdded(server.url, 7L, any())
        } returns recent
    }

    @Test
    fun `each shortcut gets the books the server returns, with their local ids`() =
        runTest(testDispatcher) {
            val reading = listOf(summary(1, "Dune", "Frank Herbert", "Other"))
            val recent = listOf(summary(2, "Emma"), summary(3, "Ulysses"))
            stubShortcuts(Result.success(reading), Result.success(recent))
            coEvery {
                bookRepository.upsertGrimmorySummaries(server, reading)
            } returns listOf("local-1")
            coEvery {
                bookRepository.upsertGrimmorySummaries(server, recent)
            } returns listOf("local-2", "local-3")

            val vm = rootCatalogue()
            advanceUntilIdle()

            val previews = vm.shelfPreviews.value
            assertEquals(
                listOf(CatalogShelfBook("local-1", "Dune", "Frank Herbert", "cover/1")),
                previews[CatalogEntryType.CONTINUE_READING]
            )
            assertEquals(
                listOf("local-2", "local-3"),
                previews[CatalogEntryType.RECENTLY_ADDED]?.map { it.localBookId }
            )
        }

    @Test
    fun `a shortcut whose request fails ends up empty without affecting the other`() =
        runTest(testDispatcher) {
            val recent = listOf(summary(2, "Emma"))
            stubShortcuts(Result.failure(RuntimeException("boom")), Result.success(recent))
            coEvery {
                bookRepository.upsertGrimmorySummaries(server, recent)
            } returns listOf("local-2")

            val vm = rootCatalogue()
            advanceUntilIdle()

            val previews = vm.shelfPreviews.value
            assertTrue(previews.getValue(CatalogEntryType.CONTINUE_READING).isEmpty())
            assertEquals(1, previews.getValue(CatalogEntryType.RECENTLY_ADDED).size)
            assertTrue(vm.uiState.value !is CatalogUiState.Error)
        }

    @Test
    fun `each library row gets its book count, and one failed count leaves that row without`() =
        runTest(testDispatcher) {
            val url = server.url
            // The catalogue only reaches the UI state once the entry preferences have emitted.
            every { catalogPreferencesRepository.observePreferences(7L) } returns flowOf(emptyList())
            coEvery { grimmoryClient.getContinueReading(url, 7L, any()) } returns Result.success(emptyList())
            coEvery { grimmoryAppClient.getRecentlyAdded(url, 7L, any()) } returns Result.success(emptyList())
            coEvery { bookRepository.upsertGrimmorySummaries(server, emptyList()) } returns emptyList()
            coEvery { grimmoryAppClient.getFullLibraries(url, 7L) } returns Result.success(
                listOf(GrimmoryLibraryFull(id = 1, name = "Fiction"), GrimmoryLibraryFull(id = 2, name = "Comics"))
            )
            coEvery { grimmoryAppClient.getBooks(url, 7L, size = 1, libraryId = 1) } returns
                Result.success(GrimmoryAppPage(content = emptyList(), totalElements = 1))
            coEvery { grimmoryAppClient.getBooks(url, 7L, size = 1, libraryId = 2) } returns
                Result.failure(RuntimeException("boom"))

            val vm = rootCatalogue()
            val collecting = backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is CatalogUiState.GrimmorySuccess, "expected GrimmorySuccess, got $state")
            val subtitles = (state as CatalogUiState.GrimmorySuccess).catalog.entries
                .filter { it.type == CatalogEntryType.LIBRARY }
                .associate { it.title to it.subtitle }
            assertEquals(mapOf("Fiction" to "1 book", "Comics" to null), subtitles)
            collecting.cancel()
        }

    @Test
    fun `search is offered on a signed-in Grimmory server and not on a plain OPDS one`() =
        runTest(testDispatcher) {
            stubShortcuts(Result.success(emptyList()), Result.success(emptyList()))
            coEvery {
                bookRepository.upsertGrimmorySummaries(server, emptyList())
            } returns emptyList()
            val grimmory = rootCatalogue()
            advanceUntilIdle()
            assertTrue(grimmory.searchAvailable.value)

            val opdsServer = server.copy(id = 8L, isGrimmory = false)
            coEvery { serverRepository.getById(8L) } returns opdsServer
            every { catalogPreferencesRepository.observePreferences(8L) } returns emptyFlow()
            coEvery {
                opdsClient.fetchCatalog(any(), any(), any(), any())
            } returns Result.success(OpdsFeed(title = "Feed", entries = emptyList()))
            val opds = CatalogViewModel(
                savedStateHandle = SavedStateHandle(mapOf("serverId" to 8L, "path" to "")),
                serverRepository = serverRepository,
                opdsClient = opdsClient,
                grimmoryAppClient = grimmoryAppClient,
                grimmoryClient = grimmoryClient,
                bookRepository = bookRepository,
                grimmoryTokenManager = grimmoryTokenManager,
                catalogPreferencesRepository = catalogPreferencesRepository,
                catalogLayoutPreferencesRepository = catalogLayoutPreferencesRepository
            )
            advanceUntilIdle()
            assertFalse(opds.searchAvailable.value)
        }

    @Test
    fun `an open catalogue loads again from the new address when its server is edited`() =
        runTest(testDispatcher) {
            stubShortcuts(Result.success(emptyList()), Result.success(emptyList()))
            coEvery {
                bookRepository.upsertGrimmorySummaries(any(), emptyList())
            } returns emptyList()
            val edits = MutableSharedFlow<Unit>()
            every { serverRepository.observeConnectionChanges(7L) } returns edits
            val moved = server.copy(url = "http://moved:8080")
            for (url in listOf(server.url, moved.url)) {
                coEvery { grimmoryAppClient.getFullLibraries(url, 7L) } returns Result.success(emptyList())
                coEvery { grimmoryAppClient.getShelves(url, 7L) } returns Result.success(emptyList())
                coEvery { grimmoryAppClient.getMagicShelves(url, 7L) } returns Result.success(emptyList())
                coEvery { grimmoryClient.getContinueReading(url, 7L, any()) } returns Result.success(emptyList())
                coEvery { grimmoryAppClient.getRecentlyAdded(url, 7L, any()) } returns Result.success(emptyList())
            }

            rootCatalogue()
            advanceUntilIdle()
            coVerify(exactly = 0) { grimmoryAppClient.getFullLibraries(moved.url, 7L) }

            coEvery { serverRepository.getById(7L) } returns moved
            edits.emit(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { grimmoryAppClient.getFullLibraries(moved.url, 7L) }
        }
}
