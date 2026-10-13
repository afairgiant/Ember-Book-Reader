package com.ember.reader.ui.notes

import android.content.Context
import com.ember.reader.core.epub.EpubCfi
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.model.Book
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ServerRepository
import com.ember.reader.core.sync.BookmarkSyncManager
import com.ember.reader.core.sync.HighlightSyncManager
import com.ember.reader.ui.book.BookMarkItem
import com.ember.reader.ui.book.RemoteMark
import com.ember.reader.ui.download.DownloadService
import com.ember.reader.ui.reader.common.ReaderTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What opening a listed bookmark, highlight or note in the reader takes. */
class NotesEntryOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val serverRepository: ServerRepository,
    private val grimmoryClient: GrimmoryClient,
    private val highlightSyncManager: HighlightSyncManager,
    private val bookmarkSyncManager: BookmarkSyncManager,
    private val appPreferencesRepository: AppPreferencesRepository
) {

    /**
     * Where the reader should open for [mark]. Null when it has no place to open at: a
     * Grimmory book note, which belongs to the book as a whole, or one whose place the
     * server could not give. The book then opens where it was left.
     *
     * For one only Grimmory holds, the place is asked of the server. When the user syncs
     * that kind of mark, the book's are synced first, so that it is also drawn in the book.
     */
    suspend fun targetFor(book: Book, mark: BookMarkItem): ReaderTarget? {
        mark.target?.let { return it }
        val remote = mark.remote ?: return null
        val server = book.serverId?.let { serverRepository.getById(it) } ?: return null
        val grimmoryBookId = book.grimmoryBookId ?: return null
        val cfi = when (remote.kind) {
            RemoteMark.Kind.BOOKMARK -> {
                if (appPreferencesRepository.getSyncBookmarks()) {
                    bookmarkSyncManager.syncBookmarksForBook(server, book.id, grimmoryBookId)
                }
                grimmoryClient.getBookmarks(server.url, server.id, grimmoryBookId).getOrNull()
                    ?.find { it.id == remote.id }?.cfi
            }
            RemoteMark.Kind.ANNOTATION -> {
                if (appPreferencesRepository.getSyncHighlights()) {
                    highlightSyncManager.syncHighlightsForBook(server, book.id, grimmoryBookId)
                }
                grimmoryClient.getAnnotations(server.url, server.id, grimmoryBookId).getOrNull()
                    ?.find { it.id == remote.id }?.cfi
            }
            RemoteMark.Kind.BOOK_NOTE -> null
        }
        return cfi?.takeIf { EpubCfi.isRealCfi(it) }?.let(ReaderTarget::Cfi)
    }

    /**
     * Downloads [book] the way the book page does, and waits for it.
     *
     * @return the book once it is on the device, or null if the download failed.
     */
    suspend fun download(book: Book): Book? {
        val serverId = book.serverId ?: return null
        DownloadService.start(context, book.id, serverId)
        // The service lists the book once it has the request; a quick failure can come and
        // go unseen, which the wait below then finds already over.
        withTimeoutOrNull(QUEUED_TIMEOUT_MS) { DownloadService.downloadingBookIds.first { book.id in it } }
        DownloadService.downloadingBookIds.first { book.id !in it }
        return bookRepository.getById(book.id)?.takeIf { it.isDownloaded }
    }

    private companion object {
        const val QUEUED_TIMEOUT_MS = 5_000L
    }
}
