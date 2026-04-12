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
    RE_READING,
    READ,
    PARTIALLY_READ,
    PAUSED,
    WONT_READ,
    ABANDONED,
    UNSET,
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
    val fileType: String? = null,
    val fileSubPath: String? = null,
    val filePath: String? = null,
)

@Serializable
data class GrimmoryBookDetail(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val readStatus: ReadStatus? = null,
    /** Overall/latest reading progress (0-1 scale). Reflects whichever client pushed last. */
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
    val libraryId: Long? = null,
    val libraryName: String? = null,
    val libraryPath: GrimmoryLibraryPath? = null,
    val primaryFile: GrimmoryBookFile? = null,
    val primaryFileType: String? = null,
    val fileTypes: List<String>? = null,
    val addedOn: String? = null,
    val lastReadTime: String? = null,
    val epubProgress: GrimmoryEpubProgress? = null,
    val koreaderProgress: GrimmoryKoreaderProgress? = null,
    val shelves: List<GrimmoryShelfSummary>? = null,
    val hardcoverBookId: Long? = null,
)

@Serializable
data class GrimmoryKoreaderProgress(
    val percentage: Float? = null,
    val device: String? = null,
    val deviceId: String? = null,
    val lastSyncTime: String? = null,
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

// Annotation/Highlight sync
@Serializable
data class GrimmoryAnnotation(
    val id: Long,
    val cfi: String? = null,
    val text: String? = null,
    val color: String? = null,
    val style: String? = null,
    val note: String? = null,
    val chapterTitle: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateAnnotationRequest(
    val bookId: Long,
    val cfi: String,
    val text: String? = null,
    val color: String? = null,
    val style: String = "highlight",
    val note: String? = null,
    val chapterTitle: String? = null,
)

@Serializable
data class UpdateAnnotationRequest(
    val color: String? = null,
    val style: String? = null,
    val note: String? = null,
)

// Bookmark sync
@Serializable
data class GrimmoryBookmark(
    val id: Long,
    val cfi: String? = null,
    val title: String? = null,
    val color: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateBookmarkRequest(
    val bookId: Long,
    val cfi: String,
    val title: String? = null,
)

@Serializable
data class UpdateBookmarkRequest(
    val title: String? = null,
    val color: String? = null,
    val notes: String? = null,
)

// Reading stats responses

@Serializable
data class GrimmoryStreakResponse(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalReadingDays: Int,
    val last52Weeks: List<GrimmoryStreakDay>,
)

@Serializable
data class GrimmoryStreakDay(
    val date: String,
    val active: Boolean,
)

@Serializable
data class GrimmoryDateCount(
    val date: String,
    val count: Int,
)

@Serializable
data class GrimmoryPeakHour(
    val hourOfDay: Int,
    val sessionCount: Int,
    val totalDurationSeconds: Long,
)

@Serializable
data class GrimmoryFavoriteDay(
    val dayOfWeek: Int,
    val dayName: String,
    val sessionCount: Int,
    val totalDurationSeconds: Long,
)

@Serializable
data class GrimmoryBookDistributions(
    val ratingDistribution: List<GrimmoryRatingEntry>,
    val progressDistribution: List<GrimmoryProgressEntry>,
    val statusDistribution: List<GrimmoryStatusEntry>,
)

@Serializable
data class GrimmoryRatingEntry(
    val rating: Int,
    val count: Int,
)

@Serializable
data class GrimmoryProgressEntry(
    val range: String,
    val min: Int,
    val max: Int,
    val count: Int,
)

@Serializable
data class GrimmoryStatusEntry(
    val status: String,
    val count: Int,
)

@Serializable
data class GrimmoryGenreStat(
    val genre: String,
    val bookCount: Int,
    val totalSessions: Int,
    val totalDurationSeconds: Long,
    val averageSessionsPerBook: Double,
)

@Serializable
data class GrimmoryTimelineEntry(
    val bookId: Long,
    val bookTitle: String,
    val bookType: String,
    val startDate: String,
    val endDate: String,
    val totalSessions: Int,
    val totalDurationSeconds: Long,
)

@Serializable
data class GrimmoryPageTurnerScore(
    val bookId: Long,
    val bookTitle: String,
    val categories: List<String> = emptyList(),
    val pageCount: Int,
    val personalRating: Int? = null,
    val gripScore: Int,
    val totalSessions: Int,
    val avgSessionDurationSeconds: Double,
    val sessionAcceleration: Double,
    val gapReduction: Double,
    val finishBurst: Boolean,
)

@Serializable
data class GrimmorySessionScatter(
    val hourOfDay: Double,
    val durationMinutes: Double,
    val dayOfWeek: Int,
)
