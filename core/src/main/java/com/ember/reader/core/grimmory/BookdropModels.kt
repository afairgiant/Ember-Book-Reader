package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

@Serializable
data class BookdropFile(
    val id: Long,
    val fileName: String? = null,
    val filePath: String? = null,
    val fileSize: Long? = null,
    val originalMetadata: BookdropMetadata? = null,
    val fetchedMetadata: BookdropMetadata? = null,
    val status: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class BookdropMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val seriesName: String? = null,
    val seriesNumber: Float? = null,
    val seriesTotal: Int? = null,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val asin: String? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val authors: List<String>? = null,
    val categories: Set<String>? = null,
    val thumbnailUrl: String? = null,
    val googleId: String? = null,
    val goodreadsId: String? = null,
    val goodreadsRating: Double? = null,
    val goodreadsReviewCount: Int? = null,
    val amazonRating: Double? = null,
    val amazonReviewCount: Int? = null,
    val hardcoverId: String? = null,
    val hardcoverBookId: String? = null,
    val hardcoverRating: Double? = null,
    val hardcoverReviewCount: Int? = null,
)

@Serializable
data class BookdropNotification(
    val pendingCount: Int = 0,
    val totalCount: Int = 0,
)

@Serializable
data class BookdropFinalizeRequest(
    val selectAll: Boolean? = null,
    val excludedIds: List<Long>? = null,
    val files: List<BookdropFinalizeFile>,
    val defaultLibraryId: Long? = null,
    val defaultPathId: Long? = null,
)

@Serializable
data class BookdropFinalizeFile(
    val fileId: Long,
    val libraryId: Long,
    val pathId: Long? = null,
    val metadata: BookdropMetadata? = null,
)

@Serializable
data class BookdropFinalizeResult(
    val totalFiles: Int = 0,
    val successfullyImported: Int = 0,
    val failed: Int = 0,
)

@Serializable
data class BookdropPage<T>(
    val content: List<T> = emptyList(),
    val page: BookdropPageInfo = BookdropPageInfo(),
)

@Serializable
data class BookdropPageInfo(
    val size: Int = 20,
    val number: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class BookdropDiscardRequest(
    val selectAll: Boolean = false,
    val excludedIds: List<Long>? = null,
    val selectedIds: List<Long>? = null,
)

@Serializable
data class LibraryPathSummary(
    val id: Long,
    val path: String,
)

@Serializable
data class GrimmoryAppLibraryWithPaths(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val bookCount: Int = 0,
    val paths: List<LibraryPathSummary> = emptyList(),
)
