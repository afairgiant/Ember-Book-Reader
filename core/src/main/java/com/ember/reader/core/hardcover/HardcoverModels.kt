package com.ember.reader.core.hardcover

data class HardcoverUser(
    val id: Int,
    val username: String,
    val name: String?,
    val booksCount: Int,
)

data class HardcoverBook(
    val id: Int,
    val bookId: Int,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val statusId: Int,
    val rating: Float?,
    val dateAdded: String?,
    val pages: Int?,
    val slug: String?,
)

data class HardcoverBookDetail(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val slug: String,
    val pages: Int?,
    val releaseYear: Int?,
    val averageRating: Float?,
    val ratingsCount: Int,
    val coverUrl: String?,
    val authors: List<String>,
    val seriesName: String?,
    val seriesPosition: Float?,
) {
    val hardcoverUrl: String get() = "https://hardcover.app/books/$slug"
}

data class HardcoverSearchResult(
    val bookId: Int,
    val title: String,
    val slug: String,
    val averageRating: Float?,
    val ratingsCount: Int,
    val authors: List<String>,
)

data class HardcoverUserBookEntry(
    val statusId: Int,
    val rating: Float?,
    val dateAdded: String?,
)

object HardcoverStatus {
    const val WANT_TO_READ = 1
    const val CURRENTLY_READING = 2
    const val READ = 3
    const val PAUSED = 4
    const val DID_NOT_FINISH = 5

    fun label(statusId: Int): String = when (statusId) {
        WANT_TO_READ -> "Want to Read"
        CURRENTLY_READING -> "Currently Reading"
        READ -> "Read"
        PAUSED -> "Paused"
        DID_NOT_FINISH -> "Did Not Finish"
        else -> "Unknown"
    }
}
