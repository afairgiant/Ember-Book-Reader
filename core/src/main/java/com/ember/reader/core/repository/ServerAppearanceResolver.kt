package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Lightweight per-server identity exposed to UI callers.
 *
 * [colorSlot] is an index into the UI-layer palette; this type stays Compose-free so it
 * can live in `:core`. Callers resolve the actual [androidx.compose.ui.graphics.Color]
 * at render time via the palette lookup helper.
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
    val appearances: Flow<Map<Long, ServerAppearance>> = serverDao.observeAll()
        .distinctUntilChanged { old, new ->
            old.size == new.size &&
                old.zip(new).all { (a, b) -> a.id == b.id && a.accentColorSlot == b.accentColorSlot }
        }
        .map { servers ->
            val resolved = resolveSlots(servers)
            servers.associate { server ->
                val slot = resolved.getValue(server.id).mod(PALETTE_SIZE)
                server.id to ServerAppearance(name = server.name, colorSlot = slot)
            }
        }

    /**
     * Returns a map of `serverId -> slot` covering every server in [servers]. For servers
     * already carrying an explicit slot the existing value is preserved; servers with a
     * null slot are auto-assigned the lowest unused slot (ascending-id order) and the
     * assignment is written back via [ServerDao.updateAccentColorSlot] so the next
     * emission sees it as persisted state.
     */
    private suspend fun resolveSlots(servers: List<ServerEntity>): Map<Long, Int> {
        val taken = servers.mapNotNullTo(mutableSetOf()) { it.accentColorSlot?.mod(PALETTE_SIZE) }
        val result = HashMap<Long, Int>(servers.size)
        for (server in servers) {
            val existing = server.accentColorSlot
            if (existing != null) {
                result[server.id] = existing
            }
        }
        servers.asSequence()
            .filter { it.accentColorSlot == null }
            .sortedBy { it.id }
            .forEach { server ->
                val slot = lowestUnusedSlot(taken)
                taken += slot
                result[server.id] = slot
                serverDao.updateAccentColorSlot(server.id, slot)
            }
        return result
    }

    private fun lowestUnusedSlot(taken: Set<Int>): Int {
        for (i in 0 until PALETTE_SIZE) if (i !in taken) return i
        return 0
    }

    companion object {
        const val PALETTE_SIZE = 6
    }
}
