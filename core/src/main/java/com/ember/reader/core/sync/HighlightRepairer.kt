package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.grimmory.CreateAnnotationRequest
import com.ember.reader.core.grimmory.GrimmoryAnnotation
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.Server
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Gives real EPUB CFIs to highlights that lack one.
 *
 * Before Ember could compute CFIs it pushed highlights to Grimmory with a progression
 * number in the CFI field, which the web reader cannot draw. The server cannot change a
 * stored CFI, so each of those is replaced: a copy with the real CFI is created, then the
 * old annotation is deleted. Highlights that never synced only need the CFI stored; the
 * normal sync pushes them afterwards.
 */
@Singleton
class HighlightRepairer @Inject constructor(
    private val highlightDao: HighlightDao,
    private val grimmoryClient: GrimmoryClient,
) {

    /** @param unresolved highlights that still have no real CFI, usually because their text was not found. */
    data class Summary(val repaired: Int, val unresolved: Int)

    /**
     * @param server the book's Grimmory server and [grimmoryBookId], or null for a book that
     * does not sync; its highlights then only get a CFI stored.
     * @param computeCfis finds each highlight's passage in the book and returns its real CFI
     * by highlight id. A highlight it cannot place is left out.
     */
    suspend fun repair(
        server: Server?,
        bookId: String,
        grimmoryBookId: Long?,
        computeCfis: suspend (List<Highlight>) -> Map<Long, String>,
    ): Result<Summary> = runCatching {
        val lacking = highlightDao.getAllByBookId(bookId)
            .filter { it.deletedAt == null && !EpubCfi.isRealCfi(it.cfi) }
        if (lacking.isEmpty()) return@runCatching Summary(repaired = 0, unresolved = 0)

        val remote = server != null && grimmoryBookId != null
        val annotations = if (server != null && grimmoryBookId != null) {
            grimmoryClient.getAnnotations(server.url, server.id, grimmoryBookId).getOrThrow()
        } else {
            emptyList()
        }
        val annotationsById = annotations.associateBy { it.id }

        var repaired = 0
        // A highlight made in the web reader already has a real CFI there: just take it.
        val needCfi = lacking.filter { local ->
            val serverCfi = local.remoteId?.let { annotationsById[it] }?.cfi
            if (EpubCfi.isRealCfi(serverCfi)) {
                highlightDao.update(local.withCfi(serverCfi!!))
                repaired++
                false
            } else {
                true
            }
        }

        val cfis = if (needCfi.isEmpty()) emptyMap() else computeCfis(needCfi.map { it.toDomain() })
        for (local in needCfi) {
            val cfi = cfis[local.id]?.takeIf { EpubCfi.isRealCfi(it) } ?: continue
            val old = local.remoteId?.let { annotationsById[it] }
            val done = when {
                // Never synced, or its annotation is gone: the normal sync takes it from here.
                !remote || old == null -> {
                    highlightDao.update(local.withCfi(cfi))
                    true
                }
                else -> replaceOnServer(server!!, grimmoryBookId!!, local, old, cfi, annotations)
            }
            if (done) repaired++
        }
        Summary(repaired = repaired, unresolved = lacking.size - repaired)
    }.onFailure { Timber.w(it, "HighlightRepair: failed for book=%s", bookId) }

    /**
     * Stores the real CFI. A highlight that came from the server and has not been placed in
     * the book keeps its CFI in its locator too, where the old fake one still is; that is
     * replaced as well, so the highlight can be placed the next time its chapter opens.
     */
    private fun HighlightEntity.withCfi(cfi: String): HighlightEntity {
        if (!locatorJson.contains("\"href\":\"\"")) return copy(cfi = cfi)
        return copy(
            cfi = cfi,
            locatorJson = CfiLocatorConverter.buildLocatorJson(
                cfi = cfi,
                selectedText = selectedText,
                chapterTitle = CfiLocatorConverter.extractTitle(locatorJson),
            ),
        )
    }

    /**
     * Creates the replacement first and deletes the old annotation last, so a failure in
     * between leaves a duplicate on the server, never a missing highlight. Running the
     * repair again cleans that duplicate up: it finds the replacement by its CFI.
     */
    private suspend fun replaceOnServer(
        server: Server,
        grimmoryBookId: Long,
        local: HighlightEntity,
        old: GrimmoryAnnotation,
        cfi: String,
        annotations: List<GrimmoryAnnotation>,
    ): Boolean {
        val existing = annotations.firstOrNull { it.cfi == cfi }
        val replacementId = existing?.id ?: run {
            val text = (local.selectedText ?: old.text)?.takeIf { it.isNotBlank() } ?: return false
            grimmoryClient.createAnnotation(
                server.url,
                server.id,
                CreateAnnotationRequest(
                    bookId = grimmoryBookId,
                    cfi = cfi,
                    text = text,
                    color = local.color.hex,
                    note = local.annotation,
                    chapterTitle = old.chapterTitle,
                ),
            ).getOrElse {
                Timber.w(it, "HighlightRepair: could not create a replacement for highlight %d", local.id)
                return false
            }.id
        }

        val alreadyLinked = highlightDao.getAllByBookId(local.bookId)
            .any { it.id != local.id && it.remoteId == replacementId }
        if (alreadyLinked) {
            // This row is the leftover copy of an annotation that was already replaced.
            highlightDao.deleteById(local.id)
        } else {
            highlightDao.update(local.withCfi(cfi).copy(remoteId = replacementId))
        }

        grimmoryClient.deleteAnnotation(server.url, server.id, old.id).onFailure {
            Timber.w(it, "HighlightRepair: replaced annotation %d but could not delete it", old.id)
        }
        return true
    }
}
