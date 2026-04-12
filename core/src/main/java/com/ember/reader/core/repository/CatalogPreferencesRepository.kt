package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.CatalogEntryPreferenceDao
import com.ember.reader.core.database.entity.CatalogEntryPreferenceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogPreferencesRepository @Inject constructor(
    private val dao: CatalogEntryPreferenceDao,
) {

    fun observePreferences(serverId: Long): Flow<List<CatalogEntryPreferenceEntity>> =
        dao.observeByServer(serverId)

    suspend fun hideEntry(serverId: Long, entryId: String) {
        val existing = dao.getByServer(serverId).find { it.entryId == entryId }
        dao.upsert(
            (existing ?: CatalogEntryPreferenceEntity(serverId = serverId, entryId = entryId))
                .copy(hidden = true),
        )
    }

    suspend fun unhideEntry(serverId: Long, entryId: String) {
        val existing = dao.getByServer(serverId).find { it.entryId == entryId }
        if (existing != null) {
            dao.upsert(existing.copy(hidden = false))
        }
    }

    suspend fun reorder(serverId: Long, orderedEntryIds: List<String>) {
        val existing = dao.getByServer(serverId).associateBy { it.entryId }
        val entities = orderedEntryIds.mapIndexed { index, entryId ->
            (existing[entryId] ?: CatalogEntryPreferenceEntity(serverId = serverId, entryId = entryId))
                .copy(sortOrder = index)
        }
        dao.upsertAll(entities)
    }

    suspend fun resetForServer(serverId: Long) {
        dao.deleteAllForServer(serverId)
    }
}
