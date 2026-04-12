package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

/**
 * Full Grimmory library DTO as returned by GET /api/v1/libraries.
 *
 * Distinct from [GrimmoryAppLibrary], which is the lightweight variant returned
 * by /api/v1/app/libraries and does not include [paths] or [fileNamingPattern].
 * The Organize Files feature needs both fields, so it uses this full variant.
 */
@Serializable
data class GrimmoryLibraryFull(
    val id: Long,
    val name: String,
    val fileNamingPattern: String? = null,
    val paths: List<GrimmoryLibraryPath> = emptyList(),
)

@Serializable
data class GrimmoryLibraryPath(
    val id: Long,
    val libraryId: Long? = null,
    val path: String,
)

/**
 * Request body for POST /api/v1/files/move. Each item in [moves] must reference a
 * book ID that also appears in [bookIds]; Grimmory uses the set for validation and
 * the list for per-book destination routing.
 */
@Serializable
data class FileMoveRequest(
    val bookIds: Set<Long>,
    val moves: List<FileMoveItem>,
)

@Serializable
data class FileMoveItem(
    val bookId: Long,
    val targetLibraryId: Long,
    val targetLibraryPathId: Long,
)
