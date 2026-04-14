package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAuthExpiredException
import com.ember.reader.core.grimmory.GrimmoryHttpException
import com.ember.reader.core.testutil.FakeSyncStatusDao
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncStatusRepositoryTest {

    private val fixedInstant = Instant.parse("2026-04-14T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val repo = SyncStatusRepository(FakeSyncStatusDao(), clock)

    @Test
    fun `get returns Unknown for unreported server`() = runTest {
        assertEquals(SyncStatus.Unknown, repo.get(1L))
    }

    @Test
    fun `observe emits Unknown for unreported server`() = runTest {
        assertEquals(SyncStatus.Unknown, repo.observe(1L).first())
    }

    @Test
    fun `reportSuccess transitions to Ok with current timestamp`() = runTest {
        repo.reportSuccess(1L)
        assertEquals(SyncStatus.Ok(fixedInstant), repo.get(1L))
    }

    @Test
    fun `reportFailure with GrimmoryAuthExpiredException classifies as AuthExpired`() = runTest {
        repo.reportFailure(1L, GrimmoryAuthExpiredException(1L))
        assertEquals(SyncStatus.AuthExpired(fixedInstant), repo.get(1L))
    }

    @Test
    fun `reportFailure with GrimmoryHttpException classifies as ServerError with status code`() = runTest {
        repo.reportFailure(1L, GrimmoryHttpException(500, "boom"))
        val status = repo.get(1L)
        assertTrue(status is SyncStatus.ServerError)
        assertEquals(500, (status as SyncStatus.ServerError).statusCode)
        assertEquals("boom", status.detail)
    }

    @Test
    fun `reportFailure with IOException classifies as NetworkError`() = runTest {
        repo.reportFailure(1L, UnknownHostException("grimmory.invalid"))
        val status = repo.get(1L)
        assertTrue(status is SyncStatus.NetworkError)
        assertEquals("grimmory.invalid", (status as SyncStatus.NetworkError).detail)
    }

    @Test
    fun `reportFailure with HttpRequestTimeoutException classifies as NetworkError with timeout detail`() = runTest {
        repo.reportFailure(1L, HttpRequestTimeoutException("GET /foo", 5000L))
        val status = repo.get(1L)
        assertTrue(status is SyncStatus.NetworkError)
        assertEquals("timeout", (status as SyncStatus.NetworkError).detail)
    }

    @Test
    fun `reportFailure with generic IOException subclass still maps to NetworkError`() = runTest {
        repo.reportFailure(1L, IOException("connection reset"))
        assertTrue(repo.get(1L) is SyncStatus.NetworkError)
    }

    @Test
    fun `reportFailure with unknown exception classifies as ServerError without status`() = runTest {
        repo.reportFailure(1L, IllegalStateException("something weird"))
        val status = repo.get(1L)
        assertTrue(status is SyncStatus.ServerError)
        assertNull((status as SyncStatus.ServerError).statusCode)
        assertEquals("something weird", status.detail)
    }

    @Test
    fun `success after failure clears the unhealthy state`() = runTest {
        repo.reportFailure(1L, GrimmoryAuthExpiredException(1L))
        assertTrue(repo.get(1L).isUnhealthy)

        repo.reportSuccess(1L)
        assertEquals(SyncStatus.Ok(fixedInstant), repo.get(1L))
    }

    @Test
    fun `clear removes server from map so observers see Unknown again`() = runTest {
        repo.reportSuccess(1L)
        assertEquals(SyncStatus.Ok(fixedInstant), repo.get(1L))

        repo.clear(1L)
        assertEquals(SyncStatus.Unknown, repo.get(1L))
        assertEquals(SyncStatus.Unknown, repo.observe(1L).first())
    }

    @Test
    fun `multiple servers tracked independently`() = runTest {
        repo.reportSuccess(1L)
        repo.reportFailure(2L, GrimmoryAuthExpiredException(2L))

        assertEquals(SyncStatus.Ok(fixedInstant), repo.get(1L))
        assertEquals(SyncStatus.AuthExpired(fixedInstant), repo.get(2L))
    }

    @Test
    fun `isUnhealthy is false for Unknown and Ok`() {
        assertTrue(!SyncStatus.Unknown.isUnhealthy)
        assertTrue(!SyncStatus.Ok(fixedInstant).isUnhealthy)
    }

    @Test
    fun `isUnhealthy is true for AuthExpired NetworkError and ServerError`() {
        assertTrue(SyncStatus.AuthExpired(fixedInstant).isUnhealthy)
        assertTrue(SyncStatus.NetworkError(fixedInstant).isUnhealthy)
        assertTrue(SyncStatus.ServerError(fixedInstant).isUnhealthy)
    }
}
