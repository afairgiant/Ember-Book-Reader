package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ember.reader.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY name ASC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY name ASC")
    suspend fun getAll(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: Long): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id")
    fun observeById(id: Long): Flow<ServerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: ServerEntity): Long

    @Update
    suspend fun update(server: ServerEntity)

    @Delete
    suspend fun delete(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE servers SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: java.time.Instant)

    @Query("UPDATE servers SET canMoveOrganizeFiles = :canMove WHERE id = :id")
    suspend fun updateCanMoveOrganizeFiles(id: Long, canMove: Boolean)
}
