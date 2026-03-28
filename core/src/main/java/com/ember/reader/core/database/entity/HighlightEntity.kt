package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ember.reader.core.model.HighlightColor
import java.time.Instant

@Entity(
    tableName = "highlights",
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
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val color: HighlightColor = HighlightColor.YELLOW,
    val annotation: String? = null,
    val selectedText: String? = null,
    val createdAt: Instant
)
