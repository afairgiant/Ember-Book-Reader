package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_cache",
    indices = [Index("word")]
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val phonetic: String? = null,
    // JSON array of definitions
    val definitions: String,
    val cachedAt: Long = System.currentTimeMillis()
)
