package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class ReadingProgressEntity(
    @PrimaryKey
    val bookId: String,
    val serverId: Long? = null,
    val percentage: Float = 0f,
    val locatorJson: String? = null,
    val kosyncProgress: String? = null,
    val lastReadAt: Instant,
    val syncedAt: Instant? = null,
    val needsSync: Boolean = false
)
