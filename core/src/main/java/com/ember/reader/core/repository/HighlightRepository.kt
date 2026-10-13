package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.HighlightColor
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao,
    private val appPreferencesRepository: AppPreferencesRepository
) {

    fun observeAll(): Flow<List<Highlight>> =
        highlightDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeByBookId(bookId: String): Flow<List<Highlight>> =
        highlightDao.observeByBookId(bookId).map { entities -> entities.map { it.toDomain() } }

    suspend fun addHighlight(
        bookId: String,
        locatorJson: String,
        color: HighlightColor,
        annotation: String? = null,
        selectedText: String? = null,
        cfi: String? = null,
    ): Long {
        val now = Instant.now()
        return highlightDao.insert(
            HighlightEntity(
                bookId = bookId,
                locatorJson = locatorJson,
                color = color,
                annotation = annotation,
                selectedText = selectedText,
                cfi = cfi,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateHighlight(highlight: Highlight, annotation: String?, color: HighlightColor) {
        highlightDao.update(
            highlight.copy(annotation = annotation, color = color, updatedAt = Instant.now()).toEntity()
        )
    }

    /** Stores where synced highlights were found in the book; see `SyncedPositionPlacer`. */
    suspend fun placeHighlights(locatorsById: Map<Long, String>) {
        locatorsById.forEach { (id, locatorJson) -> highlightDao.updateLocator(id, locatorJson) }
    }

    suspend fun deleteHighlight(id: Long) {
        if (appPreferencesRepository.getSyncHighlights()) {
            highlightDao.softDeleteById(id, Instant.now().toEpochMilli())
        } else {
            highlightDao.deleteById(id)
        }
    }
}
