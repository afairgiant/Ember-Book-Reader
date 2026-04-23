package com.ember.reader.ui.catalog

import com.ember.reader.core.grimmory.SeriesCoverBook

data class CatalogSeriesEntry(
    val seriesName: String,
    val bookCount: Int,
    val seriesTotal: Int?,
    val booksRead: Int,
    val firstAuthor: String?,
    val coverBooks: List<SeriesCoverBook>,
)
