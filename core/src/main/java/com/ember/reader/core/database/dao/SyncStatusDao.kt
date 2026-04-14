package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ember.reader.core.database.entity.SyncStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStatusDao {

    @Query("SELECT * FROM sync_status")
    fun observeAll(): Flow<List<SyncStatusEntity>>

    @Query("SELECT * FROM sync_status WHERE serverId = :serverId LIMIT 1")
    fun observeByServer(serverId: Long): Flow<SyncStatusEntity?>

    @Query("SELECT * FROM sync_status WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServer(serverId: Long): SyncStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncStatusEntity)

    @Query("DELETE FROM sync_status WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: Long)
}
