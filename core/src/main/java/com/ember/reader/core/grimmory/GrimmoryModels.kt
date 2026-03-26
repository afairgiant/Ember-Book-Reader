package com.ember.reader.core.grimmory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GrimmoryLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class GrimmoryTokens(
    val accessToken: String,
    val refreshToken: String,
    val isDefaultPassword: String? = null,
)

@Serializable
data class GrimmoryRefreshRequest(
    val refreshToken: String,
)

@Serializable
enum class ReadStatus {
    UNREAD,
    READING,
    READ,
    DNF,
}

@Serializable
data class GrimmoryBookSummary(
    val id: Long,
    val title: String,
    val readStatus: ReadStatus? = null,
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
    val ttsPositionCfi: String? = null,
)

@Serializable
data class GrimmoryFileProgress(
    val bookFileId: Long? = null,
    val positionData: String? = null,
    val positionHref: String? = null,
    val progressPercent: Float? = null,
)

@Serializable
data class GrimmoryBookDetail(
    val id: Long,
    val title: String,
    val readStatus: ReadStatus? = null,
    val readProgress: Float? = null,
    val epubProgress: GrimmoryEpubProgress? = null,
    val authors: List<String> = emptyList(),
    val primaryFileType: String? = null,
)

@Serializable
data class GrimmoryProgressRequest(
    val bookId: Long,
    val fileProgress: GrimmoryFileProgress? = null,
    val epubProgress: GrimmoryEpubProgress? = null,
    val dateFinished: String? = null,
)

@Serializable
data class GrimmoryStatusRequest(
    val status: ReadStatus,
)
