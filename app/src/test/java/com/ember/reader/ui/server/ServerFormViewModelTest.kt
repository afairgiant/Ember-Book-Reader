package com.ember.reader.ui.server

import androidx.lifecycle.SavedStateHandle
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockKExtension::class)
class ServerFormViewModelTest {

    @MockK
    private lateinit var serverRepository: ServerRepository

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(serverId: Long = -1L): ServerFormViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("serverId" to serverId))
        return ServerFormViewModel(savedStateHandle, serverRepository)
    }

    @Test
    fun `save with blank name sets validationError`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateUrl("http://localhost")
        // name is blank by default

        var onSuccessCalled = false
        viewModel.save { onSuccessCalled = true }
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.validationError)
        assertEquals("Name and URL are required", viewModel.uiState.value.validationError)
        assertTrue(!onSuccessCalled)
    }

    @Test
    fun `save with valid data calls serverRepository save and invokes onSuccess`() = runTest {
        coEvery { serverRepository.save(any()) } returns 1L

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateName("My Server")
        viewModel.updateUrl("http://localhost")
        viewModel.updateOpdsUsername("opds_user")
        viewModel.updateOpdsPassword("opds_pass")

        var onSuccessCalled = false
        viewModel.save { onSuccessCalled = true }
        advanceUntilIdle()

        coVerify { serverRepository.save(any()) }
        assertTrue(onSuccessCalled)
        assertNull(viewModel.uiState.value.validationError)
    }

    @Test
    fun `testOpdsConnection transitions Idle to Testing to Success`() = runTest {
        coEvery {
            serverRepository.testOpdsConnection(any(), any(), any())
        } returns Result.success("My Library")
        coEvery { serverRepository.detectGrimmory(any()) } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateUrl("http://localhost")
        viewModel.updateOpdsUsername("user")
        viewModel.updateOpdsPassword("pass")

        assertEquals(TestResult.Idle, viewModel.uiState.value.opdsTestResult)

        viewModel.testOpdsConnection()

        // After launching but before coroutine completes
        assertEquals(TestResult.Testing, viewModel.uiState.value.opdsTestResult)

        advanceUntilIdle()

        val result = viewModel.uiState.value.opdsTestResult
        assertTrue(result is TestResult.Success)
        assertEquals("My Library", (result as TestResult.Success).message)
    }

    @Test
    fun `testOpdsConnection transitions Idle to Testing to Error on failure`() = runTest {
        coEvery {
            serverRepository.testOpdsConnection(any(), any(), any())
        } returns Result.failure(RuntimeException("Connection refused"))
        coEvery { serverRepository.detectGrimmory(any()) } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateUrl("http://localhost")
        viewModel.updateOpdsUsername("user")
        viewModel.updateOpdsPassword("pass")

        viewModel.testOpdsConnection()
        assertEquals(TestResult.Testing, viewModel.uiState.value.opdsTestResult)

        advanceUntilIdle()

        val result = viewModel.uiState.value.opdsTestResult
        assertTrue(result is TestResult.Error)
        assertEquals("Connection refused", (result as TestResult.Error).message)
    }

    @Test
    fun `saveGrimmory with kosync disabled persists blank kosync credentials`() = runTest {
        val captured = slot<Server>()
        coEvery { serverRepository.save(capture(captured)) } returns 1L

        val viewModel = createViewModel()
        viewModel.updateName("g1")
        viewModel.updateUrl("https://g1")
        viewModel.updateGrimmoryUsername("alex")
        viewModel.updateGrimmoryPassword("pw")
        viewModel.setOpdsEnabled(false)
        viewModel.setKosyncEnabled(false)
        viewModel.updateKosyncUsername("stale") // should be discarded
        viewModel.updateKosyncPassword("stale")

        viewModel.saveGrimmory(
            useGrimmoryLoginForOpds = false,
            useGrimmoryLoginForKosync = false,
            onSuccess = {}
        )
        runCurrent()

        val saved = captured.captured
        assertFalse(saved.kosyncEnabled)
        assertEquals("", saved.kosyncUsername)
        assertEquals("", saved.kosyncPassword)
    }

    @Test
    fun `save (OPDS-only server) respects opdsEnabled flag`() = runTest {
        val captured = slot<Server>()
        coEvery { serverRepository.save(capture(captured)) } returns 1L

        val viewModel = createViewModel()
        viewModel.updateName("opds1")
        viewModel.updateUrl("https://opds.example")
        viewModel.setOpdsEnabled(true)
        viewModel.updateOpdsUsername("alex")
        viewModel.updateOpdsPassword("pw")
        viewModel.setKosyncEnabled(false)

        viewModel.save(onSuccess = {})
        runCurrent()

        val saved = captured.captured
        assertTrue(saved.opdsEnabled)
        assertEquals("alex", saved.opdsUsername)
        assertFalse(saved.kosyncEnabled)
        assertEquals("", saved.kosyncUsername)
    }

    @Test
    fun `saveGrimmory uses grimmory login for opds when opdsEnabled and delegation is on`() =
        runTest {
            val captured = slot<Server>()
            coEvery { serverRepository.save(capture(captured)) } returns 1L

            val viewModel = createViewModel()
            viewModel.updateName("g1")
            viewModel.updateUrl("https://g1")
            viewModel.updateGrimmoryUsername("alex")
            viewModel.updateGrimmoryPassword("gpass")
            viewModel.setOpdsEnabled(true)
            // opdsUsername/opdsPassword left blank — delegation should copy Grimmory values

            viewModel.saveGrimmory(
                useGrimmoryLoginForOpds = true,
                useGrimmoryLoginForKosync = true,
                onSuccess = {}
            )
            runCurrent()

            val saved = captured.captured
            assertEquals("alex", saved.opdsUsername)
            assertEquals("gpass", saved.opdsPassword)
            assertTrue(saved.opdsEnabled)
        }
}
