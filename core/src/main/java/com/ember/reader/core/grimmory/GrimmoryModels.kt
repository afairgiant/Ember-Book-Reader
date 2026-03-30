package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

@Serializable
data class GrimmoryLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class GrimmoryTokens(
    val accessToken: String,
    val refreshToken: String,
    val isDefaultPassword: String? = null
)

@Serializable
data class GrimmoryRefreshRequest(
    val refreshToken: String
)

@Serializable
enum class ReadStatus {
    UNREAD,
    READING,
    READ,
    DNF
}

@Serializable
data class GrimmoryBookSummary(
    val id: Long,
    val title: String,
    val readStatus: ReadStatus? = null,
    /** Overall progress from Grimmory — may reflect kosync/KOReader, not native Grimmory progress. */
    val readProgress: Float? = null,
    val authors: List<String> = emptyList(),
    val primaryFileType: String? = null,
    val coverUpdatedOn: String? = null,
)

@Serializable
data class GrimmoryEpubProgress(
    val cfi: String? = null,
    val href: String? = null,
    val percentage: Float? = null,
    val ttsPositionCfi: String? = null
)

@Serializable
data class GrimmoryFileProgress(
    val bookFileId: Long? = null,
    val positionData: String? = null,
    val positionHref: String? = null,
    val progressPercent: Float? = null
)

@Serializable
data class GrimmoryBookFile(
    val id: Long,
    val fileName: String? = null,
    val fileType: String? = null
)

@Serializable
data class GrimmoryBookDetail(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val readStatus: ReadStatus? = null,
    /** Overall progress from Grimmory — may reflect kosync/KOReader, not native Grimmory progress. */
    val readProgress: Float? = null,
    val personalRating: Int? = null,
    val authors: List<String> = emptyList(),
    val categories: Set<String>? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val isbn13: String? = null,
    val language: String? = null,
    val goodreadsRating: Double? = null,
    val goodreadsReviewCount: Int? = null,
    val seriesName: String? = null,
    val seriesNumber: Float? = null,
    val libraryName: String? = null,
    val primaryFileType: String? = null,
    val fileTypes: List<String>? = null,
    val addedOn: String? = null,
    val lastReadTime: String? = null,
    val epubProgress: GrimmoryEpubProgress? = null,
    val files: List<GrimmoryBookFile> = emptyList(),
    val shelves: List<GrimmoryShelfSummary>? = null
)

@Serializable
data class GrimmoryShelfSummary(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
data class GrimmoryProgressRequest(
    val bookId: Long,
    val fileProgress: GrimmoryFileProgress? = null,
    val epubProgress: GrimmoryEpubProgress? = null,
    val dateFinished: String? = null
)

@Serializable
data class GrimmoryStatusRequest(
    val status: ReadStatus
)

@Serializable
data class GrimmoryReadingSessionRequest(
    val bookId: Long,
    val bookType: String,
    val startTime: String,
    val endTime: String,
    val durationSeconds: Long,
    val startProgress: Float,
    val endProgress: Float,
    val progressDelta: Float,
    val startLocation: String? = null,
    val endLocation: String? = null
)
