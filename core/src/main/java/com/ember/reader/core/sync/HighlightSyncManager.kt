package com.ember.reader.core.sync

import com.ember.reader.core.database.dao.HighlightDao
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.grimmory.CreateAnnotationRequest
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.UpdateAnnotationRequest
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.model.Server
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class HighlightSyncManager @Inject constructor(
    private val highlightDao: HighlightDao,
    private val grimmoryClient: GrimmoryClient,
    private val syncStatusRepository: SyncStatusRepository,
    private val bookSyncLocks: BookSyncLocks,
) {

    suspend fun syncHighlightsForBook(server: Server, bookId: String, grimmoryBookId: Long) =
        bookSyncLocks.withHighlightsLock(bookId) { sync(server, bookId, grimmoryBookId) }

    private suspend fun sync(server: Server, bookId: String, grimmoryBookId: Long) {
        Timber.d("HighlightSync: syncing book=%s grimmoryId=%d", bookId, grimmoryBookId)

        val serverAnnotations = grimmoryClient.getAnnotations(server.url, server.id, grimmoryBookId)
            .onFailure { syncStatusRepository.reportFailure(server.id, it) }
            .getOrElse {
                Timber.e(it, "HighlightSync: failed to fetch annotations")
                return
            }

        val localHighlights = highlightDao.getAllByBookId(bookId)
        val localByRemoteId = localHighlights.filter { it.remoteId != null }.associateBy { it.remoteId!! }
        val serverById = serverAnnotations.associateBy { it.id }
        Timber.d("HighlightSync: server=%d local=%d (withRemoteId=%d)", serverAnnotations.size, localHighlights.size, localByRemoteId.size)

        // Process server annotations
        for (serverAnnotation in serverAnnotations) {
            val local = localByRemoteId[serverAnnotation.id]

            if (local != null) {
                if (local.deletedAt != null) {
                    // Local tombstoned → delete on server. The tombstone goes only once the
                    // server's copy has, or the next sync would bring the highlight back.
                    grimmoryClient.deleteAnnotation(server.url, server.id, serverAnnotation.id)
                        .onSuccess {
                            highlightDao.deleteById(local.id)
                            Timber.d("HighlightSync: deleted remote annotation %d", serverAnnotation.id)
                        }
                        .onFailure {
                            Timber.w(it, "HighlightSync: failed to delete remote annotation %d, retrying next sync", serverAnnotation.id)
                        }
                } else {
                    // Always refresh color and locator from server data
                    val cfi = serverAnnotation.cfi
                    val serverColor = HighlightColor.fromHex(serverAnnotation.color)
                    var updated = local

                    if (cfi != null && local.locatorJson.contains("\"href\":\"\"")) {
                        val fixedLocator = CfiLocatorConverter.buildLocatorJson(
                            cfi = cfi,
                            selectedText = serverAnnotation.text ?: local.selectedText,
                            chapterTitle = serverAnnotation.chapterTitle
                        )
                        updated = updated.copy(locatorJson = fixedLocator)
                    }
                    if (updated.color != serverColor) {
                        updated = updated.copy(color = serverColor)
                    }
                    if (updated.cfi == null && EpubCfi.isRealCfi(cfi)) {
                        updated = updated.copy(cfi = cfi)
                    }
                    if (updated !== local) {
                        highlightDao.update(updated)
                        Timber.d("HighlightSync: refreshed highlight %d (color=%s)", local.id, serverColor)
                    }

                    // Both active → compare timestamps, update loser
                    val serverTime = parseTimestamp(serverAnnotation.updatedAt)
                    if (serverTime != null && serverTime.isAfter(local.updatedAt)) {
                        // Server is newer → update local
                        highlightDao.update(
                            updated.copy(
                                annotation = serverAnnotation.note,
                                selectedText = serverAnnotation.text ?: local.selectedText,
                                updatedAt = serverTime
                            )
                        )
                        Timber.d("HighlightSync: updated local highlight %d from server", local.id)
                    } else if (serverTime != null && local.updatedAt.isAfter(serverTime)) {
                        // Local is newer → update server
                        grimmoryClient.updateAnnotation(
                            server.url, server.id, serverAnnotation.id,
                            UpdateAnnotationRequest(
                                color = local.color.hex,
                                note = local.annotation
                            )
                        ).onSuccess { Timber.d("HighlightSync: updated remote annotation %d", serverAnnotation.id) }
                    }
                }
            } else {
                // Not matched locally. It may still be one made here at the same place and
                // not yet linked (a lost create response, a 409); that one is linked below,
                // so it is not made a second time.
                if (localHighlights.any { it.remoteId == null && it.deletedAt == null && it.cfi == serverAnnotation.cfi }) {
                    continue
                }
                // New from server
                val cfi = serverAnnotation.cfi ?: continue
                val locatorJson = CfiLocatorConverter.buildLocatorJson(
                    cfi = cfi,
                    selectedText = serverAnnotation.text,
                    chapterTitle = serverAnnotation.chapterTitle
                )
                val now = Instant.now()
                highlightDao.insert(
                    HighlightEntity(
                        bookId = bookId,
                        locatorJson = locatorJson,
                        color = HighlightColor.fromHex(serverAnnotation.color),
                        annotation = serverAnnotation.note,
                        selectedText = serverAnnotation.text,
                        cfi = cfi.takeIf { EpubCfi.isRealCfi(it) },
                        createdAt = parseTimestamp(serverAnnotation.createdAt) ?: now,
                        remoteId = serverAnnotation.id,
                        updatedAt = parseTimestamp(serverAnnotation.updatedAt) ?: now
                    )
                )
                Timber.d("HighlightSync: created local highlight from remote %d", serverAnnotation.id)
            }
        }

        // Process local highlights
        for (local in localHighlights) {
            if (local.remoteId == null && local.deletedAt == null) {
                // New local → push to server. Grimmory's web reader can only draw a
                // highlight from a real CFI, and a stored CFI cannot be changed later, so a
                // highlight without one stays on this device until it has one.
                val cfi = local.cfi?.takeIf { EpubCfi.isRealCfi(it) }
                if (cfi == null) {
                    Timber.d("HighlightSync: holding local highlight %d, no real CFI yet", local.id)
                    continue
                }

                // Check if server already has this CFI (match by position)
                val existingOnServer = serverAnnotations.find { it.cfi == cfi }
                if (existingOnServer != null) {
                    highlightDao.update(local.copy(remoteId = existingOnServer.id))
                    Timber.d("HighlightSync: linked local highlight %d → existing remote %d", local.id, existingOnServer.id)
                    continue
                }

                // Grimmory rejects annotations without text (400), so pushing would fail on every sync.
                if (local.selectedText.isNullOrBlank()) {
                    Timber.w("HighlightSync: skipping push of local highlight %d — no selected text", local.id)
                    continue
                }

                val chapterTitle = CfiLocatorConverter.extractTitle(local.locatorJson)
                grimmoryClient.createAnnotation(
                    server.url, server.id,
                    CreateAnnotationRequest(
                        bookId = grimmoryBookId,
                        cfi = cfi,
                        text = local.selectedText,
                        color = local.color.hex,
                        note = local.annotation,
                        chapterTitle = chapterTitle
                    )
                ).onSuccess { created ->
                    highlightDao.update(local.copy(remoteId = created.id))
                    Timber.d("HighlightSync: pushed local highlight %d → remote %d", local.id, created.id)
                }.onFailure {
                    if (it.message?.contains("409") == true) {
                        val refreshed = grimmoryClient.getAnnotations(server.url, server.id, grimmoryBookId).getOrNull()
                        val match = refreshed?.find { a -> a.cfi == cfi }
                        if (match != null) {
                            highlightDao.update(local.copy(remoteId = match.id))
                            Timber.d("HighlightSync: linked local highlight %d → remote %d after 409", local.id, match.id)
                        }
                    } else {
                        Timber.w(it, "HighlightSync: failed to push highlight %d", local.id)
                    }
                }
            } else if (local.remoteId == null && local.deletedAt != null) {
                // Tombstoned but never synced → just clean up
                highlightDao.deleteById(local.id)
            } else if (local.remoteId != null && !serverById.containsKey(local.remoteId)) {
                // Had remoteId but not on server → server deleted it
                highlightDao.deleteById(local.id)
                Timber.d("HighlightSync: removed local highlight %d (server-deleted)", local.id)
            }
        }

        syncStatusRepository.reportSuccess(server.id)
        Timber.d("HighlightSync: completed for book=%s", bookId)
    }

    private fun parseTimestamp(timestamp: String?): Instant? = runCatching {
        if (timestamp == null) return null
        Instant.parse(timestamp)
    }.getOrNull()
}
