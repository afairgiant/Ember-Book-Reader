package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.CredentialEncryption
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.sync.KosyncClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ServerRepositoryTest {

    @MockK
    private lateinit var serverDao: ServerDao

    @MockK
    private lateinit var opdsClient: OpdsClient

    @MockK
    private lateinit var kosyncClient: KosyncClient

    @MockK
    private lateinit var credentialEncryption: CredentialEncryption

    private lateinit var repository: ServerRepository

    @BeforeEach
    fun setUp() {
        repository = ServerRepository(serverDao, opdsClient, kosyncClient, credentialEncryption)
    }

    @Test
    fun `save stores passwords via CredentialEncryption`() = runTest {
        val server = Server(
            id = 0,
            name = "Test Server",
            url = "http://localhost",
            opdsUsername = "opds_user",
            opdsPassword = "opds_pass",
            kosyncUsername = "kosync_user",
            kosyncPassword = "kosync_pass",
        )
        coEvery { serverDao.insert(any()) } returns 5L
        every { credentialEncryption.storePassword(any(), any()) } returns Unit

        val id = repository.save(server)

        assertEquals(5L, id)
        verify {
            credentialEncryption.storePassword(
                CredentialEncryption.opdsPasswordKey(5L),
                "opds_pass",
            )
        }
        verify {
            credentialEncryption.storePassword(
                CredentialEncryption.kosyncPasswordKey(5L),
                "kosync_pass",
            )
        }
    }

    @Test
    fun `delete removes passwords from CredentialEncryption`() = runTest {
        coEvery { serverDao.deleteById(10L) } returns Unit
        every { credentialEncryption.removePassword(any()) } returns Unit

        repository.delete(10L)

        coVerify { serverDao.deleteById(10L) }
        verify { credentialEncryption.removePassword(CredentialEncryption.opdsPasswordKey(10L)) }
        verify { credentialEncryption.removePassword(CredentialEncryption.kosyncPasswordKey(10L)) }
    }

    @Test
    fun `getById hydrates passwords from CredentialEncryption`() = runTest {
        val entity = ServerEntity(
            id = 7L,
            name = "My Server",
            url = "http://example.com",
            opdsUsername = "opds_user",
            kosyncUsername = "kosync_user",
        )
        coEvery { serverDao.getById(7L) } returns entity
        every {
            credentialEncryption.getPassword(CredentialEncryption.opdsPasswordKey(7L))
        } returns "stored_opds_pass"
        every {
            credentialEncryption.getPassword(CredentialEncryption.kosyncPasswordKey(7L))
        } returns "stored_kosync_pass"

        val server = repository.getById(7L)

        assertNotNull(server)
        assertEquals("stored_opds_pass", server!!.opdsPassword)
        assertEquals("stored_kosync_pass", server.kosyncPassword)
    }

    @Test
    fun `testOpdsConnection delegates to OpdsClient`() = runTest {
        coEvery {
            opdsClient.testConnection("http://example.com", "user", "pass")
        } returns Result.success("Library")

        val result = repository.testOpdsConnection("http://example.com", "user", "pass")

        assertTrue(result.isSuccess)
        assertEquals("Library", result.getOrNull())
        coVerify { opdsClient.testConnection("http://example.com", "user", "pass") }
    }

    @Test
    fun `testKosyncConnection delegates to KosyncClient`() = runTest {
        coEvery {
            kosyncClient.authenticate("http://example.com", "user", "pass")
        } returns Result.success(Unit)

        val result = repository.testKosyncConnection("http://example.com", "user", "pass")

        assertTrue(result.isSuccess)
        coVerify { kosyncClient.authenticate("http://example.com", "user", "pass") }
    }
}
