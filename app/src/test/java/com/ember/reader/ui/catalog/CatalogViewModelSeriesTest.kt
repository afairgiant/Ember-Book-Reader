package com.ember.reader.ui.catalog

import androidx.lifecycle.SavedStateHandle
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryAppPage
import com.ember.reader.core.grimmory.GrimmoryAppSeries
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.grimmory.SeriesCoverBook
import com.ember.reader.core.model.Server
import com.ember.reader.core.opds.OpdsClient
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

/**
 * Guards the fix that keeps `coverBooks` in the UI state: the old code flattened each
 * `GrimmoryAppSeries` into an `OpdsFeedEntry`, which had no place to stash the cover
 * book list. If the mapping ever slips back into that shape, this test fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class CatalogViewModelSeriesTest {

    @MockK
    private lateinit var serverRepository: ServerRepository

    @MockK
    private lateinit var opdsClient: OpdsClient

    @MockK
    private lateinit var grimmoryAppClient: GrimmoryAppClient

    @MockK
    private lateinit var grimmoryTokenManager: GrimmoryTokenManager

    @MockK
    private lateinit var catalogPreferencesRepository: CatalogPreferencesRepository

    @MockK
    private lateinit var catalogLayoutPreferencesRepository: CatalogLayoutPreferencesRepository

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchGrimmorySeries emits SeriesSuccess preserving coverBooks`() = runTest(testDispatcher) {
        val server = Server(
            id = 7L,
            name = "Grimmory",
            url = "http://localhost:8080",
            opdsUsername = "u",
            opdsPassword = "p",
            kosyncUsername = "ku",
            kosyncPassword = "kp",
            isGrimmory = true,
        )
        val cover = SeriesCoverBook(bookId = 42L, coverUpdatedOn = "2026-04-20T12:00:00Z", seriesNumber = 1f)
        val page = GrimmoryAppPage(
            content = listOf(
                GrimmoryAppSeries(
                    seriesName = "Wheel of Time",
                    bookCount = 14,
                    authors = listOf("Robert Jordan"),
                    coverBooks = listOf(cover),
                )
            ),
            hasNext = false,
        )

        coEvery { serverRepository.getById(7L) } returns server
        coEvery {
            grimmoryAppClient.getSeries(
                baseUrl = server.url,
                serverId = server.id,
                page = 0,
                size = 100,
                sort = SeriesSortOption.NAME.key,
                dir = SeriesSortOption.NAME.dir,
            )
        } returns Result.success(page)
        every { catalogPreferencesRepository.observePreferences(7L) } returns emptyFlow()
        every { catalogLayoutPreferencesRepository.seriesViewModeFlow } returns flowOf(CatalogSeriesViewMode.GRID)

        val vm = CatalogViewModel(
            savedStateHandle = SavedStateHandle(mapOf("serverId" to 7L, "path" to "grimmory:series")),
            serverRepository = serverRepository,
            opdsClient = opdsClient,
            grimmoryAppClient = grimmoryAppClient,
            grimmoryTokenManager = grimmoryTokenManager,
            catalogPreferencesRepository = catalogPreferencesRepository,
            catalogLayoutPreferencesRepository = catalogLayoutPreferencesRepository,
        )

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is CatalogUiState.SeriesSuccess, "expected SeriesSuccess, got $state")
        state as CatalogUiState.SeriesSuccess
        assertEquals(server.url, state.serverUrl)
        assertEquals(1, state.entries.size)
        val entry = state.entries.single()
        assertEquals("Wheel of Time", entry.seriesName)
        assertEquals(14, entry.bookCount)
        assertEquals("Robert Jordan", entry.firstAuthor)
        assertEquals(listOf(cover), entry.coverBooks)
    }
}
