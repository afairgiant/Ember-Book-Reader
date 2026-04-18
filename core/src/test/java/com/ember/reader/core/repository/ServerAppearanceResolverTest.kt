package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.ServerEntity
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAppearanceResolverTest {

    @Test
    fun `unassigned server gets lowest unused slot`() = runTest {
        val dao = FakeServerDao(
            listOf(
                server(id = 1, slot = 0),
                server(id = 2, slot = 2),
                server(id = 3, slot = null)
            )
        )
        val resolver = ServerAppearanceResolver(dao)

        val appearances = resolver.appearances.first()

        assertEquals(1, appearances[3]?.colorSlot)
        assertEquals(listOf(3L to 1), dao.slotUpdates)
    }

    @Test
    fun `explicit slot preserved when others auto-assign around it`() = runTest {
        val dao = FakeServerDao(
            listOf(
                server(id = 1, slot = 3),
                server(id = 2, slot = null),
                server(id = 3, slot = null)
            )
        )
        val resolver = ServerAppearanceResolver(dao)

        resolver.appearances.first()

        assertEquals(listOf(2L to 0, 3L to 1), dao.slotUpdates)
    }

    @Test
    fun `slot is recycled when a server is deleted`() = runTest {
        val dao = FakeServerDao(
            listOf(
                server(id = 1, slot = 0),
                server(id = 2, slot = 1),
                server(id = 3, slot = 2)
            )
        )
        val resolver = ServerAppearanceResolver(dao)
        resolver.appearances.first()

        dao.emit(
            listOf(
                server(id = 1, slot = 0),
                server(id = 3, slot = 2),
                server(id = 4, slot = null)
            )
        )
        val appearances = resolver.appearances.first()

        assertEquals(1, appearances[4]?.colorSlot)
    }

    @Test
    fun `seventh server wraps to slot zero`() = runTest {
        val dao = FakeServerDao(
            (1L..6L).map { server(id = it, slot = (it - 1).toInt()) } +
                server(id = 7, slot = null)
        )
        val resolver = ServerAppearanceResolver(dao)

        val appearances = resolver.appearances.first()

        assertTrue(
            appearances[7]?.colorSlot in 0..5,
            "expected wrap to a slot in palette range, got ${appearances[7]?.colorSlot}"
        )
        assertEquals(listOf(7L to 0), dao.slotUpdates)
    }

    @Test
    fun `stable emission after assignment does not retrigger writes`() = runTest {
        val dao = FakeServerDao(
            listOf(
                server(id = 1, slot = null),
                server(id = 2, slot = null)
            )
        )
        val resolver = ServerAppearanceResolver(dao)

        resolver.appearances.first()
        val callsAfterFirst = dao.slotUpdates.size
        // Simulate the DB re-emitting after the writes — same (id -> slot) projection, so the
        // resolver's distinctUntilChanged must swallow it and not write again.
        dao.emit(
            listOf(
                server(id = 1, slot = 0),
                server(id = 2, slot = 1)
            )
        )
        resolver.appearances.first()

        assertEquals(callsAfterFirst, dao.slotUpdates.size)
    }

    private fun server(id: Long, slot: Int?): ServerEntity = ServerEntity(
        id = id,
        name = "Server $id",
        url = "https://s$id",
        opdsUsername = "u",
        kosyncUsername = "u",
        grimmoryUsername = "",
        isGrimmory = false,
        opdsEnabled = true,
        kosyncEnabled = true,
        lastConnected = Instant.EPOCH,
        canMoveOrganizeFiles = false,
        accentColorSlot = slot
    )

    /**
     * Minimal in-memory ServerDao that reacts to [updateAccentColorSlot] by updating its
     * internal state and re-emitting on [observeAll]. Records every slot update.
     */
    private class FakeServerDao(initial: List<ServerEntity>) : ServerDao {
        private val state = MutableStateFlow(initial)
        val slotUpdates = mutableListOf<Pair<Long, Int?>>()

        fun emit(next: List<ServerEntity>) {
            state.value = next
        }

        override fun observeAll(): Flow<List<ServerEntity>> = state

        override suspend fun updateAccentColorSlot(id: Long, slot: Int?) {
            slotUpdates += (id to slot)
            state.value = state.value.map { if (it.id == id) it.copy(accentColorSlot = slot) else it }
        }

        override suspend fun getAll(): List<ServerEntity> = state.value
        override suspend fun getById(id: Long): ServerEntity? =
            state.value.firstOrNull { it.id == id }
        override fun observeById(id: Long): Flow<ServerEntity?> = throw NotImplementedError()
        override suspend fun insert(server: ServerEntity): Long = throw NotImplementedError()
        override suspend fun update(server: ServerEntity) = throw NotImplementedError()
        override suspend fun delete(server: ServerEntity) = throw NotImplementedError()
        override suspend fun deleteById(id: Long) = throw NotImplementedError()
        override suspend fun updateLastConnected(id: Long, timestamp: Instant) =
            throw NotImplementedError()
        override suspend fun updateCanMoveOrganizeFiles(id: Long, canMove: Boolean) =
            throw NotImplementedError()
        override suspend fun updateGrimmoryPermissions(
            id: Long,
            canMoveOrganizeFiles: Boolean,
            canDownload: Boolean,
            canUpload: Boolean,
            canAccessBookdrop: Boolean,
            isAdmin: Boolean,
            fetchedAt: Instant
        ) = throw NotImplementedError()
    }
}
