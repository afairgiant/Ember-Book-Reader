package com.ember.reader.ui.organize

import com.ember.reader.core.grimmory.FileMoveRequest
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryFullBook
import com.ember.reader.core.grimmory.GrimmoryFullBookFile
import com.ember.reader.core.grimmory.GrimmoryFullBookLibraryPath
import com.ember.reader.core.grimmory.GrimmoryFullBookMetadata
import com.ember.reader.core.grimmory.GrimmoryLibraryFull
import com.ember.reader.core.grimmory.GrimmoryLibraryPath
import com.ember.reader.core.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class OrganizeFilesViewModelTest {

    @MockK
    private lateinit var appClient: GrimmoryAppClient

    @MockK(relaxed = true)
    private lateinit var serverRepository: ServerRepository

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleLibraries() = listOf(
        GrimmoryLibraryFull(
            id = 1L,
            name = "Sci-Fi",
            fileNamingPattern = "{authors:sort}/{title}",
            paths = listOf(GrimmoryLibraryPath(10L, 1L, "/mnt/sci-fi"))
        ),
        GrimmoryLibraryFull(
            id = 2L,
            name = "Fiction",
            fileNamingPattern = "{authors:sort}/{title} ({year})",
            paths = listOf(
                GrimmoryLibraryPath(20L, 2L, "/mnt/books/fiction"),
                GrimmoryLibraryPath(21L, 2L, "/mnt/archive/fiction")
            )
        )
    )

    private fun sampleBook(
        id: Long = 101L,
        libraryId: Long = 1L,
        pathId: Long = 10L,
        libraryPath: String = "/mnt/sci-fi",
        fileName: String = "Dune.epub",
        title: String = "Dune",
        authors: List<String> = listOf("Frank Herbert"),
        year: String = "1965"
    ): GrimmoryFullBook = GrimmoryFullBook(
        id = id,
        title = title,
        libraryId = libraryId,
        libraryPath = GrimmoryFullBookLibraryPath(id = pathId, libraryId = libraryId, path = libraryPath),
        primaryFile = GrimmoryFullBookFile(id = 100L + id, fileName = fileName),
        metadata = GrimmoryFullBookMetadata(
            title = title,
            authors = authors,
            publishedDate = "$year-08-01"
        )
    )

    private fun makeVm(
        bookIds: List<Long> = listOf(101L),
        testScope: TestScope
    ): OrganizeFilesViewModel = OrganizeFilesViewModel(
        appClient = appClient,
        serverRepository = serverRepository,
        baseUrl = "http://grimmory.test",
        serverId = 1L,
        bookIds = bookIds,
        scope = CoroutineScope(dispatcher)
    )

    @Test
    fun `init loads libraries and book details then transitions to Ready`() = runTest(dispatcher) {
        coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
        coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())

        val vm = makeVm(testScope = this)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is OrganizeFilesUiState.Ready)
        val ready = state as OrganizeFilesUiState.Ready
        assertEquals(2, ready.libraries.size)
        assertEquals(1, ready.previews.size)
        assertEquals("Dune", ready.previews[0].title)
    }

    @Test
    fun `selecting a single-path library auto-picks the path and does not show path picker`() =
        runTest(dispatcher) {
            coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
            coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())

            val vm = makeVm(testScope = this)
            advanceUntilIdle()

            vm.onLibrarySelected(1L)
            advanceUntilIdle()

            val ready = vm.state.value as OrganizeFilesUiState.Ready
            assertEquals(10L, ready.selectedPathId)
            assertFalse(ready.showPathPicker)
        }

    @Test
    fun `selecting a multi-path library shows path picker and auto-picks first`() =
        runTest(dispatcher) {
            coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
            coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())

            val vm = makeVm(testScope = this)
            advanceUntilIdle()

            vm.onLibrarySelected(2L)
            advanceUntilIdle()

            val ready = vm.state.value as OrganizeFilesUiState.Ready
            assertTrue(ready.showPathPicker)
            assertEquals(20L, ready.selectedPathId)
        }

    @Test
    fun `preview recomputes when target library changes`() = runTest(dispatcher) {
        coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
        coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())

        val vm = makeVm(testScope = this)
        advanceUntilIdle()
        vm.onLibrarySelected(2L)
        advanceUntilIdle()

        val ready = vm.state.value as OrganizeFilesUiState.Ready
        // Fiction pattern "{authors:sort}/{title} ({year})" → "Herbert, Frank/Dune (1965).epub"
        assertTrue(
            ready.previews[0].newPath.endsWith("Herbert, Frank/Dune (1965).epub"),
            "Expected preview to end with resolved pattern, got: ${ready.previews[0].newPath}"
        )
        assertTrue(ready.previews[0].newPath.startsWith("/mnt/books/fiction"))
    }

    @Test
    fun `row is marked isNoChange when target equals source`() = runTest(dispatcher) {
        coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
        coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())

        val vm = makeVm(testScope = this)
        advanceUntilIdle()
        vm.onLibrarySelected(1L) // same as current
        advanceUntilIdle()

        val ready = vm.state.value as OrganizeFilesUiState.Ready
        assertTrue(ready.previews[0].isNoChange)
        assertFalse(ready.anythingToMove)
    }

    @Test
    fun `submit posts the expected FileMoveRequest and transitions to Success`() =
        runTest(dispatcher) {
            coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
            coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())
            val captured = slot<FileMoveRequest>()
            coEvery { appClient.moveFiles(any(), any(), capture(captured)) } returns Result.success(Unit)

            val vm = makeVm(testScope = this)
            advanceUntilIdle()
            vm.onLibrarySelected(2L)
            advanceUntilIdle()
            vm.onConfirm()
            advanceUntilIdle()

            assertEquals(setOf(101L), captured.captured.bookIds)
            assertEquals(1, captured.captured.moves.size)
            assertEquals(2L, captured.captured.moves[0].targetLibraryId)
            assertEquals(20L, captured.captured.moves[0].targetLibraryPathId)

            val state = vm.state.value
            assertTrue(state is OrganizeFilesUiState.Success)
            assertEquals(1, (state as OrganizeFilesUiState.Success).movedCount)
            assertEquals("Fiction", state.targetLibraryName)
        }

    @Test
    fun `submit 403 transitions to Error Permission and refreshes server permissions`() =
        runTest(dispatcher) {
            coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
            coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())
            coEvery { appClient.moveFiles(any(), any(), any()) } returns
                Result.failure(IllegalStateException("Move files failed: 403 Forbidden"))
            coEvery { serverRepository.refreshGrimmoryPermissions(1L) } returns Unit

            val vm = makeVm(testScope = this)
            advanceUntilIdle()
            vm.onLibrarySelected(2L)
            advanceUntilIdle()
            vm.onConfirm()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state is OrganizeFilesUiState.Error)
            assertEquals(OrganizeFilesUiState.Error.Kind.Permission, (state as OrganizeFilesUiState.Error).kind)
            coVerify { serverRepository.refreshGrimmoryPermissions(1L) }
        }

    @Test
    fun `submit 500 transitions to Error Server`() = runTest(dispatcher) {
        coEvery { appClient.getFullLibraries(any(), any()) } returns Result.success(sampleLibraries())
        coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())
        coEvery { appClient.moveFiles(any(), any(), any()) } returns
            Result.failure(IllegalStateException("Move files failed: 500 Internal Server Error"))

        val vm = makeVm(testScope = this)
        advanceUntilIdle()
        vm.onLibrarySelected(2L)
        advanceUntilIdle()
        vm.onConfirm()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is OrganizeFilesUiState.Error)
        assertEquals(OrganizeFilesUiState.Error.Kind.Server, (state as OrganizeFilesUiState.Error).kind)
    }

    @Test
    fun `libraries fetch failure transitions to Error Loading`() = runTest(dispatcher) {
        coEvery { appClient.getFullLibraries(any(), any()) } returns
            Result.failure(IllegalStateException("Network down"))
        coEvery { appClient.getBookDetailFull(any(), any(), 101L) } returns Result.success(sampleBook())

        val vm = makeVm(testScope = this)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is OrganizeFilesUiState.Error)
        assertEquals(OrganizeFilesUiState.Error.Kind.Loading, (state as OrganizeFilesUiState.Error).kind)
    }
}
