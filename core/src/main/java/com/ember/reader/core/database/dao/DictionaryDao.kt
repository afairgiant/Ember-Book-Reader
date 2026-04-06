package com.ember.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ember.reader.core.database.entity.DictionaryEntryEntity

@Dao
interface DictionaryDao {

    @Query("SELECT * FROM dictionary_cache WHERE LOWER(word) = LOWER(:word) LIMIT 1")
    suspend fun findByWord(word: String): DictionaryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DictionaryEntryEntity)

    @Query("SELECT COUNT(*) FROM dictionary_cache")
    suspend fun count(): Int

    @Query("DELETE FROM dictionary_cache")
    suspend fun clearAll()
}
