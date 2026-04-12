package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

/**
 * Represents the response from `GET /api/v1/books/{id}` — the full (non-app) book
 * detail endpoint. Unlike [GrimmoryBookDetail] (which maps the flat app endpoint
 * `/api/v1/app/books/{id}`), this DTO mirrors Grimmory's native `Book` shape where
 * metadata lives inside a nested [metadata] object and file info is on [primaryFile].
 *
 * Only the fields needed for Organize Files are modelled; everything else is ignored
 * via `ignoreUnknownKeys = true` on the JSON config.
 */
@Serializable
data class GrimmoryFullBook(
    val id: Long,
    val title: String = "",
    val libraryId: Long? = null,
    val libraryName: String? = null,
    val libraryPath: GrimmoryFullBookLibraryPath? = null,
    val primaryFile: GrimmoryFullBookFile? = null,
    val metadata: GrimmoryFullBookMetadata? = null,
    val metadataMatchScore: Float? = null,
)

@Serializable
data class GrimmoryFullBookLibraryPath(
    val id: Long? = null,
    val libraryId: Long? = null,
    val path: String? = null,
)

@Serializable
data class GrimmoryFullBookFile(
    val id: Long? = null,
    val fileName: String? = null,
    val fileSubPath: String? = null,
    val filePath: String? = null,
)

@Serializable
data class GrimmoryFullBookMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val authors: List<String>? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val seriesName: String? = null,
    val seriesNumber: Float? = null,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val language: String? = null,
    val categories: List<String>? = null,
    val moods: List<String>? = null,
    val tags: List<String>? = null,
) {
    val categoryNames: Set<String>
        get() = categories?.toSet() ?: emptySet()
    val moodNames: Set<String>
        get() = moods?.toSet() ?: emptySet()
    val tagNames: Set<String>
        get() = tags?.toSet() ?: emptySet()
}
