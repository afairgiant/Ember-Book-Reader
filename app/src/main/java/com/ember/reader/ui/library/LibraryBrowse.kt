package com.ember.reader.ui.library

import com.ember.reader.core.model.Book

/** The lists the Library screen can show: every book, or the series or authors they belong to. */
enum class LibraryTab { BOOKS, SERIES, AUTHORS }

/** A series or an author on the Series and Authors tabs. [cover] stands in for the whole group. */
data class LibraryGroup(val name: String, val count: Int, val cover: Book)

internal const val UNKNOWN_AUTHOR = "Unknown author"

/**
 * The series or author [this] is listed under on [tab]. Null on the Books tab, and for a book
 * with no series on the Series tab: such books are left off that tab.
 */
internal fun Book.groupName(tab: LibraryTab): String? = when (tab) {
    LibraryTab.BOOKS -> null
    LibraryTab.SERIES -> series?.takeIf { it.isNotBlank() }
    LibraryTab.AUTHORS -> author?.takeIf { it.isNotBlank() } ?: UNKNOWN_AUTHOR
}

internal fun libraryGroups(books: List<Book>, tab: LibraryTab): List<LibraryGroup> = books
    .mapNotNull { book -> book.groupName(tab)?.let { it to book } }
    .groupBy({ it.first }, { it.second })
    .map { (name, members) -> LibraryGroup(name, members.size, members.inSeriesOrder().first()) }
    .sortedBy { it.name.lowercase() }

/** By position in the series, then title; books with no position come last. */
internal fun List<Book>.inSeriesOrder(): List<Book> =
    sortedWith(compareBy<Book>({ it.seriesIndex ?: Float.MAX_VALUE }, { it.title.lowercase() }))
