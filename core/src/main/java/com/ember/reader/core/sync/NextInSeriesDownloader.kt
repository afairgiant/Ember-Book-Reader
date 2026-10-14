package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAppBook
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.AppPreferencesRepository
import com.ember.reader.core.repository.BookRepository
import com.ember.reader.core.repository.ReadingProgressRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** How far into a book the reader has to be before the next in its series is fetched. */
const val NEXT_IN_SERIES_MIN_PROGRESS = 0.8f

/**
 * The books whose series should be looked at for a next one: of the [downloaded] books that
 * belong to a series and are nearly finished, the furthest along in each series. Ebooks
 * and audiobooks of one series are followed separately, as each is read on its own.
 */
fun seriesFrontiers(downloaded: List<Book>, progressOf: (bookId: String) -> Float?): List<Book> = downloaded
    .filter { it.series != null && it.seriesIndex != null }
    .filter { (progressOf(it.id) ?: 0f) >= NEXT_IN_SERIES_MIN_PROGRESS }
    .groupBy { it.series to it.format }
    .mapNotNull { (_, books) -> books.maxByOrNull { it.seriesIndex ?: 0f } }

/**
 * The book that follows number [after] in a series, from the series as the server lists it.
 * A book with no number has no place in the order and is passed over.
 */
fun nextInSeries(after: Float, seriesBooks: List<GrimmoryAppBook>): List<GrimmoryAppBook> = seriesBooks
    .filter { (it.seriesNumber ?: Float.NEGATIVE_INFINITY) > after }
    .sortedBy { it.seriesNumber }

/**
 * Downloads the next book of a series when the reader is near the end of one, so that it is on the device
 * by the time the current one is finished.
 */
@Singleton
class NextInSeriesDownloader @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val grimmoryAppClient: GrimmoryAppClient,
    private val appPreferencesRepository: AppPreferencesRepository
) {

    /**
     * Looks at each series under way among [server]'s downloaded books and downloads the
     * book after the one being read, when it is not on the device already.
     *
     * A book is only ever fetched this way once: if it is later removed from the device, it
     * is not brought back.
     *
     * @return the titles downloaded.
     */
    suspend fun run(server: Server): List<String> {
        val downloaded = bookRepository.getDownloadedBooksForServer(server.id)
        val progress = downloaded.associate { it.id to readingProgressRepository.getByBookId(it.id)?.percentage }
        val alreadyFetched = appPreferencesRepository.getSeriesAutoDownloaded(server.id)
        val titles = mutableListOf<String>()

        for (current in seriesFrontiers(downloaded, progress::get)) {
            val series = current.series ?: continue
            val index = current.seriesIndex ?: continue
            val seriesBooks = grimmoryAppClient.getSeriesBooks(server.url, server.id, series)
                .onFailure { Timber.w(it, "NextInSeries: could not list '%s'", series) }
                .getOrNull()?.content ?: continue

            // The first book after this one in the same form: an audiobook series may hold
            // the ebooks too.
            val next = nextInSeries(index, seriesBooks).firstNotNullOfOrNull { candidate ->
                val localId = bookRepository.upsertGrimmoryBooks(server, listOf(candidate)).firstOrNull()
                localId?.let { bookRepository.getById(it) }?.takeIf { it.format == current.format }
                    ?.let { candidate to it }
            } ?: continue
            val (remote, local) = next

            if (local.isDownloaded || remote.id.toString() in alreadyFetched) continue

            bookRepository.downloadBook(local, server)
                .onSuccess {
                    titles += remote.title
                    appPreferencesRepository.addSeriesAutoDownloaded(server.id, remote.id.toString())
                    Timber.d("NextInSeries: downloaded '%s' after '%s'", remote.title, current.title)
                }
                .onFailure { Timber.w(it, "NextInSeries: failed to download '%s'", remote.title) }
        }
        return titles
    }
}
