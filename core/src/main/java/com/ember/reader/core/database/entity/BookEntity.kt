package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ember.reader.core.model.BookFormat
import java.time.Instant

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("serverId"),
        Index("opdsEntryId"),
        Index(value = ["serverId", "title"]),
        Index(value = ["serverId", "addedAt"]),
        Index(value = ["serverId", "author"]),
        Index(value = ["serverId", "series", "seriesIndex"]),
        Index("localPath"),
        Index("format"),
    ]
)
data class BookEntity(
    @PrimaryKey
    val id: String,
    val serverId: Long? = null,
    val opdsEntryId: String? = null,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val downloadUrl: String? = null,
    val localPath: String? = null,
    val format: BookFormat,
    val fileHash: String? = null,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val addedAt: Instant,
    val downloadedAt: Instant? = null,
    val publisher: String? = null,
    val language: String? = null,
    val subjects: String? = null,
    val pageCount: Int? = null,
    val publishedDate: String? = null
)
