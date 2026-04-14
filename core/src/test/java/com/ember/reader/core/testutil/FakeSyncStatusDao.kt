package com.ember.reader.core.testutil

import com.ember.reader.core.database.dao.SyncStatusDao
import com.ember.reader.core.database.entity.SyncStatusEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SyncStatusDao] for exercising [com.ember.reader.core.sync.SyncStatusRepository]
 * without spinning up Room. Captures upsert-replace and
 * delete-by-serverId semantics so the repository's state transitions can
 * be asserted end to end.
 */
class FakeSyncStatusDao : SyncStatusDao {

    private val state = MutableStateFlow<Map<Long, SyncStatusEntity>>(emptyMap())

    override fun observeAll(): Flow<List<SyncStatusEntity>> =
        state.map { it.values.toList() }

    override fun observeByServer(serverId: Long): Flow<SyncStatusEntity?> =
        state.map { it[serverId] }

    override suspend fun getByServer(serverId: Long): SyncStatusEntity? =
        state.value[serverId]

    override suspend fun upsert(entity: SyncStatusEntity) {
        state.value = state.value + (entity.serverId to entity)
    }

    override suspend fun deleteByServer(serverId: Long) {
        state.value = state.value - serverId
    }
}
