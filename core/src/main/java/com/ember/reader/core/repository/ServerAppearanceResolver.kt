package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Lightweight per-server identity exposed to UI callers.
 *
 * [colorSlot] is an index into the UI-layer palette; this type stays Compose-free so it
 * can live in `:core`. Callers resolve the actual [androidx.compose.ui.graphics.Color]
 * at render time via `ServerAccentPalette[colorSlot % size]`.
 */
data class ServerAppearance(
    val name: String,
    val colorSlot: Int
)

/**
 * Resolves a per-server [ServerAppearance] keyed by server id, self-healing any server
 * row whose `accentColorSlot` is null by auto-assigning the lowest unused palette slot
 * and persisting that assignment back to the database.
 *
 * Determinism: unassigned servers are processed in ascending id order so concurrent
 * inserts produce predictable assignments.
 *
 * Re-emission safety: the `distinctUntilChanged` on the (id -> slot) projection prevents
 * the database write from re-triggering the flow with unchanged content.
 */
@Singleton
class ServerAppearanceResolver @Inject constructor(
    private val serverDao: ServerDao
) {
    private val paletteSize: Int = DEFAULT_PALETTE_SIZE

    val appearances: Flow<Map<Long, ServerAppearance>> = serverDao.observeAll()
        .distinctUntilChanged { old, new ->
            old.map { it.id to it.accentColorSlot } == new.map { it.id to it.accentColorSlot }
        }
        .onEach { servers -> assignMissingSlots(servers) }
        .map { servers ->
            servers.associate { server ->
                server.id to ServerAppearance(
                    name = server.name,
                    colorSlot = (server.accentColorSlot ?: pickSlotInMemory(server, servers))
                        .mod(paletteSize)
                )
            }
        }

    private suspend fun assignMissingSlots(servers: List<ServerEntity>) {
        val taken = servers.mapNotNullTo(mutableSetOf()) { it.accentColorSlot?.mod(paletteSize) }
        servers
            .asSequence()
            .filter { it.accentColorSlot == null }
            .sortedBy { it.id }
            .forEach { server ->
                val slot = lowestUnusedSlot(taken)
                taken += slot
                serverDao.updateAccentColorSlot(server.id, slot)
            }
    }

    private fun pickSlotInMemory(target: ServerEntity, servers: List<ServerEntity>): Int {
        val taken = mutableSetOf<Int>()
        servers
            .asSequence()
            .filter { it.accentColorSlot != null }
            .forEach { taken += it.accentColorSlot!!.mod(paletteSize) }
        servers
            .asSequence()
            .filter { it.accentColorSlot == null && it.id < target.id }
            .sortedBy { it.id }
            .forEach { taken += lowestUnusedSlot(taken) }
        return lowestUnusedSlot(taken)
    }

    private fun lowestUnusedSlot(taken: Set<Int>): Int {
        for (i in 0 until paletteSize) if (i !in taken) return i
        return 0
    }

    companion object {
        const val DEFAULT_PALETTE_SIZE = 6

        /** Sentinel appearance for books that have no linked server (local-only). */
        val LocalAppearance = ServerAppearance(name = "Local", colorSlot = -1)
    }
}
