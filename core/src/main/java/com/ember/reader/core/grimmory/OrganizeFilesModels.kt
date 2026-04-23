package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

/**
 * Full Grimmory library DTO as returned by GET /api/v1/libraries.
 *
 * Ember uses this for both Organize Files (needs [paths] and
 * [fileNamingPattern]) and the catalog browser (needs [icon]). The
 * `/api/v1/app/libraries` variant was dropped — on Grimmory v3.0.0+ it
 * throws a 500 because its DTO walks a lazy relation outside a
 * transaction, and the upstream team plans to deprecate app endpoints
 * anyway.
 */
@Serializable
data class GrimmoryLibraryFull(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val fileNamingPattern: String? = null,
    val paths: List<GrimmoryLibraryPath> = emptyList()
)

@Serializable
data class GrimmoryLibraryPath(
    val id: Long,
    val libraryId: Long? = null,
    val path: String
)

/**
 * Request body for POST /api/v1/files/move. Each item in [moves] must reference a
 * book ID that also appears in [bookIds]; Grimmory uses the set for validation and
 * the list for per-book destination routing.
 */
@Serializable
data class FileMoveRequest(
    val bookIds: Set<Long>,
    val moves: List<FileMoveItem>
)

@Serializable
data class FileMoveItem(
    val bookId: Long,
    val targetLibraryId: Long,
    val targetLibraryPathId: Long
)
