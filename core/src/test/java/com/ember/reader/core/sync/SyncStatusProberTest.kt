package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryUser
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.testutil.FakeSyncStatusDao
import com.ember.reader.core.testutil.TestFixtures.server
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SyncStatusProberTest {

    private val grimmoryAppClient: GrimmoryAppClient = mockk(relaxed = true)
    private val opdsClient: OpdsClient = mockk(relaxed = true)
    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-04-14T12:00:00Z"), ZoneOffset.UTC)
    private val syncStatusRepository = SyncStatusRepository(FakeSyncStatusDao(), clock)
    private val prober = SyncStatusProber(
        grimmoryAppClient,
        opdsClient,
        syncStatusRepository,
        Lazy { serverRepository }
    )

    @Test
    fun `probe reports Ok when Grimmory getCurrentUser succeeds`() = runTest {
        val grimmoryServer = server(id = 1L, isGrimmory = true)
        coEvery { grimmoryAppClient.getCurrentUser(any(), any()) } returns Result.success(
            GrimmoryUser(id = 1L, username = "me")
        )

        prober.probe(grimmoryServer)

        assertTrue(syncStatusRepository.get(1L) is SyncStatus.Ok)
    }

    @Test
    fun `probe reports AuthExpired when Grimmory auth is dead`() = runTest {
        val grimmoryServer = server(id = 1L, isGrimmory = true)
        coEvery { grimmoryAppClient.getCurrentUser(any(), any()) } returns
            Result.failure(GrimmoryAuthExpiredException(1L))

        prober.probe(grimmoryServer)

        assertTrue(syncStatusRepository.get(1L) is SyncStatus.AuthExpired)
    }

    @Test
    fun `probe reports NetworkError when Grimmory is unreachable`() = runTest {
        val grimmoryServer = server(id = 1L, isGrimmory = true)
        coEvery { grimmoryAppClient.getCurrentUser(any(), any()) } returns
            Result.failure(UnknownHostException("grimmory.invalid"))

        prober.probe(grimmoryServer)

        assertTrue(syncStatusRepository.get(1L) is SyncStatus.NetworkError)
    }

    @Test
    fun `probe reports Ok when OPDS testConnection succeeds`() = runTest {
        val opdsServer = server(id = 2L, isGrimmory = false)
        coEvery { opdsClient.testConnection(any(), any(), any()) } returns Result.success("Catalog")

        prober.probe(opdsServer)

        assertTrue(syncStatusRepository.get(2L) is SyncStatus.Ok)
    }

    @Test
    fun `probe leaves status untouched when OPDS server has no credentials`() = runTest {
        val opdsServer = server(id = 3L, isGrimmory = false, opdsUsername = "", opdsPassword = "")

        prober.probe(opdsServer)

        // No probe was issued, so no status was reported — stays Unknown.
        assertTrue(syncStatusRepository.get(3L) is SyncStatus.Unknown)
    }
}
