package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Stores per-book reader preference overrides as a JSON blob. Presence of a
 * row means the book has its own settings that take priority over the global
 * defaults in DataStore. Absence means the book inherits global defaults.
 */
@Entity(tableName = "book_reader_preferences")
data class BookReaderPreferencesEntity(
    @PrimaryKey val bookId: String,
    val preferencesJson: String,
    val updatedAt: Instant,
)
