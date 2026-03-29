package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

@Serializable
data class AudiobookInfo(
    val bookId: Long,
    val bookFileId: Long? = null,
    val title: String? = null,
    val author: String? = null,
    val narrator: String? = null,
    val durationMs: Long? = null,
    val bitrate: Int? = null,
    val codec: String? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val totalSizeBytes: Long? = null,
    val folderBased: Boolean = false,
    val chapters: List<AudiobookChapter>? = null,
    val tracks: List<AudiobookTrack>? = null
)

@Serializable
data class AudiobookChapter(
    val index: Int,
    val title: String? = null,
    val startTimeMs: Long = 0,
    val endTimeMs: Long = 0,
    val durationMs: Long = 0
)

@Serializable
data class AudiobookTrack(
    val index: Int,
    val fileName: String? = null,
    val title: String? = null,
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val cumulativeStartMs: Long = 0
)

@Serializable
data class AudiobookProgressData(
    val positionMs: Long,
    val trackIndex: Int? = null,
    val trackPositionMs: Long? = null,
    val percentage: Float
)
