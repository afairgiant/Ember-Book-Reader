package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao,
) {

    fun observeByBookId(bookId: String): Flow<List<Highlight>> =
        highlightDao.observeByBookId(bookId).map { entities -> entities.map { it.toDomain() } }

    suspend fun addHighlight(
        bookId: String,
        locatorJson: String,
        color: HighlightColor,
        annotation: String? = null,
    ): Long = highlightDao.insert(
        HighlightEntity(
            bookId = bookId,
            locatorJson = locatorJson,
            color = color,
            annotation = annotation,
            createdAt = Instant.now(),
        ),
    )

    suspend fun updateAnnotation(highlight: Highlight, annotation: String?) {
        highlightDao.update(
            highlight.copy(annotation = annotation).let {
                HighlightEntity(
                    id = it.id,
                    bookId = it.bookId,
                    locatorJson = it.locatorJson,
                    color = it.color,
                    annotation = it.annotation,
                    createdAt = it.createdAt,
                )
            },
        )
    }

    suspend fun deleteHighlight(id: Long) {
        highlightDao.deleteById(id)
    }
}
