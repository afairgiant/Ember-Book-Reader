package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ember.reader.core.database.entity.CatalogEntryPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogEntryPreferenceDao {

    @Query("SELECT * FROM catalog_entry_preferences WHERE serverId = :serverId")
    fun observeByServer(serverId: Long): Flow<List<CatalogEntryPreferenceEntity>>

    @Query("SELECT * FROM catalog_entry_preferences WHERE serverId = :serverId")
    suspend fun getByServer(serverId: Long): List<CatalogEntryPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogEntryPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CatalogEntryPreferenceEntity>)

    @Query("DELETE FROM catalog_entry_preferences WHERE serverId = :serverId")
    suspend fun deleteAllForServer(serverId: Long)
}
