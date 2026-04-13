package com.ember.reader.core.model

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val trackIndex: Int? = null,
    val trackCount: Int? = null,
    val trackBytesDownloaded: Long? = null,
    val trackTotalBytes: Long? = null
)
