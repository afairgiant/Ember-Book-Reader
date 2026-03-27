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
    val libraryName: String? = null,
    val readStatus: ReadStatus? = null,
    val personalRating: Int? = null,
    val authors: List<String> = emptyList(),
    val primaryFileType: String? = null,
    val coverUpdatedOn: String? = null,
    val addedOn: String? = null
)

@Serializable
data class GrimmoryAppLibrary(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val bookCount: Int = 0
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
