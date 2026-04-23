package com.ember.reader.core.model

import java.time.Instant

data class Book(
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
    val addedAt: Instant = Instant.now(),
    val downloadedAt: Instant? = null,
    val publisher: String? = null,
    val language: String? = null,
    val subjects: String? = null,
    val pageCount: Int? = null,
    val publishedDate: String? = null,
    val fileSizeKb: Long? = null,
    val ageRating: Int? = null,
    val contentRating: String? = null
) {
    val isDownloaded: Boolean get() = localPath != null
    val isLocal: Boolean get() = serverId == null

    /** Extracts Grimmory's numeric book ID from opdsEntryId like "urn:booklore:book:123" */
    val grimmoryBookId: Long?
        get() = opdsEntryId
            ?.removePrefix("urn:booklore:book:")
            ?.toLongOrNull()
}
