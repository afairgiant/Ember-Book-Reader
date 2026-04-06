package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary_cache",
    indices = [Index("word")],
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val phonetic: String? = null,
    val definitions: String, // JSON array of definitions
    val cachedAt: Long = System.currentTimeMillis(),
)
