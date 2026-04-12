package com.ember.reader.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "catalog_entry_preferences",
    primaryKeys = ["serverId", "entryId"],
)
data class CatalogEntryPreferenceEntity(
    val serverId: Long,
    val entryId: String,
    val hidden: Boolean = false,
    val sortOrder: Int = Int.MAX_VALUE,
)
