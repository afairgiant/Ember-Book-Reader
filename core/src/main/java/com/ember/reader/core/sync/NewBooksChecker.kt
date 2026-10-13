package com.ember.reader.core.sync

import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryBookSummary
import com.ember.reader.core.model.Server
import com.ember.reader.core.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * The server's most recently added books as they were last seen: their ids, and the latest
 * time any of them was added.
 */
data class SeenBooks(val newestAddedOn: String?, val ids: Set<Long>) {

    /** As one string for the preferences: the time, then the ids. */
    fun encode(): String = "${newestAddedOn.orEmpty()}|${ids.joinToString(",")}"

    companion object {
        fun decode(encoded: String?): SeenBooks? {
            val (addedOn, ids) = encoded?.split("|", limit = 2)?.takeIf { it.size == 2 } ?: return null
            return SeenBooks(
                newestAddedOn = addedOn.ifEmpty { null },
                ids = ids.split(",").mapNotNull { it.toLongOrNull() }.toSet()
            )
        }
    }
}

/**
 * The books in [current] that have been added since [seen]. Nothing counts as new the first
 * time, when there is nothing to compare with.
 *
 * A book is new when it was not among those seen and was added after the latest of them.
 * The time matters: when a book is removed from the server an older one takes its place in
 * the list, and that one is not new.
 */
fun newBooksSince(seen: SeenBooks?, current: List<GrimmoryBookSummary>): List<GrimmoryBookSummary> {
    seen ?: return emptyList()
    return current.filter { book ->
        val addedOn = book.addedOn
        book.id !in seen.ids && (seen.newestAddedOn == null || addedOn == null || addedOn > seen.newestAddedOn)
    }
}

/** What to remember once [current] has been seen, given what was remembered before. */
fun seenAfter(seen: SeenBooks?, current: List<GrimmoryBookSummary>): SeenBooks = SeenBooks(
    // The times are ISO-8601 and from one server, so they sort as text.
    newestAddedOn = (current.mapNotNull { it.addedOn } + listOfNotNull(seen?.newestAddedOn)).maxOrNull(),
    ids = current.map { it.id }.toSet()
)

/** Finds the books added to a Grimmory server since the user last saw its newest ones. */
@Singleton
class NewBooksChecker @Inject constructor(
    private val grimmoryAppClient: GrimmoryAppClient,
    private val appPreferencesRepository: AppPreferencesRepository
) {

    /**
     * Asks [server] for its newest books and returns those not seen before, remembering
     * them all as seen. Empty when the server cannot be asked, and the first time.
     */
    suspend fun check(server: Server): List<GrimmoryBookSummary> {
        val current = grimmoryAppClient.getRecentlyAdded(server.url, server.id, limit = CHECK_LIMIT)
            .getOrElse {
                Timber.w(it, "NewBooks: could not ask %s", server.name)
                return emptyList()
            }
        val seen = SeenBooks.decode(appPreferencesRepository.getSeenNewBooks(server.id))
        val new = newBooksSince(seen, current)
        appPreferencesRepository.updateSeenNewBooks(server.id, seenAfter(seen, current).encode())
        Timber.d("NewBooks: %d new of %d on %s", new.size, current.size, server.name)
        return new
    }

    /** Remembers [shown] as seen, for when the app itself has just listed them. */
    suspend fun markSeen(server: Server, shown: List<GrimmoryBookSummary>) {
        val seen = SeenBooks.decode(appPreferencesRepository.getSeenNewBooks(server.id))
        // What the app lists is shorter than what is checked; keep the ids already known.
        val merged = seenAfter(seen, shown).let { it.copy(ids = it.ids + seen?.ids.orEmpty()) }
        appPreferencesRepository.updateSeenNewBooks(server.id, merged.encode())
    }

    private companion object {
        const val CHECK_LIMIT = 20
    }
}

/** The notification's heading: "New book on Home" or "3 new books on Home". */
fun newBooksTitle(count: Int, serverName: String): String =
    if (count == 1) "New book on $serverName" else "$count new books on $serverName"
