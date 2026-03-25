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
) {
    val isDownloaded: Boolean get() = localPath != null
    val isLocal: Boolean get() = serverId == null
}
