package com.ember.reader.core.testutil

import com.ember.reader.core.database.dao.SyncStatusDao
import com.ember.reader.core.database.entity.SyncStatusEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SyncStatusDao] for app-module ViewModel tests. Mirrors the
 * fake in `:core`'s test source; duplicated here because Gradle module
 * boundaries prevent sharing without a test-fixtures setup, and the
 * class is small enough to not justify one.
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
