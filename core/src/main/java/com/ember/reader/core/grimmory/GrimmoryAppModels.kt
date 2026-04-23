package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

@Serializable
data class GrimmoryAppPage<T>(
    val content: List<T>,
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

@Serializable
data class GrimmoryAppBook(
    val id: Long,
    val title: String,
    val libraryId: Long? = null,
    /**
     * Grimmory v3.0.0+ dropped `libraryName` from `AppBookSummary` — the field
     * survives here as nullable so older servers that still send it deserialize
     * cleanly. Fetch the detail endpoint for the current library name.
     */
    val libraryName: String? = null,
    val readStatus: ReadStatus? = null,
    val personalRating: Int? = null,
    val authors: List<String> = emptyList(),
    val primaryFileType: String? = null,
    val coverUpdatedOn: String? = null,
    val audiobookCoverUpdatedOn: String? = null,
    val addedOn: String? = null,
    val lastReadTime: String? = null,
    val seriesName: String? = null,
    val seriesNumber: Float? = null,
    // v3.0.0 — metadata exposed on the summary for richer list rows / client-side filtering.
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val ageRating: Int? = null,
    val contentRating: String? = null,
    val metadataMatchScore: Float? = null,
    val fileSizeKb: Long? = null,
    val isPhysical: Boolean? = null
)

@Serializable
data class GrimmoryAppShelf(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val bookCount: Int = 0,
    val publicShelf: Boolean = false
)

@Serializable
data class GrimmoryAppMagicShelf(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val iconType: String? = null,
    val publicShelf: Boolean = false
)

@Serializable
data class GrimmoryAppSeries(
    val seriesName: String,
    val bookCount: Int = 0,
    val seriesTotal: Int? = null,
    val authors: List<String> = emptyList(),
    val booksRead: Int = 0,
    val latestAddedOn: String? = null,
    val coverBooks: List<SeriesCoverBook> = emptyList()
)

@Serializable
data class SeriesCoverBook(
    val bookId: Long? = null,
    val coverUpdatedOn: String? = null,
    val seriesNumber: Float? = null,
    val primaryFileType: String? = null
)

@Serializable
data class GrimmoryAppAuthor(
    val id: Long,
    val name: String,
    val bookCount: Int = 0,
    val hasPhoto: Boolean = false
)

@Serializable
data class GrimmoryAppFilterOptions(
    val authors: List<GrimmoryFilterItem> = emptyList(),
    val languages: List<GrimmoryLanguageItem> = emptyList(),
    val readStatuses: List<String> = emptyList(),
    val fileTypes: List<String> = emptyList()
)

@Serializable
data class GrimmoryFilterItem(
    val name: String,
    val count: Int = 0
)

@Serializable
data class GrimmoryLanguageItem(
    val code: String,
    val label: String? = null,
    val count: Int = 0
)
