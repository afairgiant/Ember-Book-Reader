package com.ember.reader.core.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPreferencesDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "library_preferences")

enum class LibrarySortKey { RECENT, TITLE, AUTHOR, PROGRESS, DATE_ADDED, FILE_SIZE }
enum class LibraryGroupBy { NONE, AUTHOR, SERIES, FORMAT, STATUS, DATE_ADDED, SOURCE }
enum class LibraryFormat { ALL, BOOKS, AUDIOBOOKS }
enum class LibraryStatus { ALL, READING, UNREAD, FINISHED }
enum class LibraryViewMode { GRID, LIST, COMPACT_LIST }
enum class LibraryDensity { SMALL, MEDIUM, LARGE }

/**
 * The active library source filter.
 *
 * Persisted as a single string in DataStore:
 * - [All] -> "ALL"
 * - [Local] -> "LOCAL"
 * - [Server] -> "SERVER:<id>"
 *
 * Legacy value "SERVER" (meaning "any server" pre-multi-server) migrates to [All] on read.
 */
sealed class LibrarySourceFilter {
    object All : LibrarySourceFilter()
    object Local : LibrarySourceFilter()
    data class Server(val serverId: Long) : LibrarySourceFilter()

    fun toStoredString(): String = when (this) {
        All -> "ALL"
        Local -> "LOCAL"
        is Server -> "SERVER:$serverId"
    }

    companion object {
        private val SERVER_ID_PATTERN = Regex("""^SERVER:(\d+)$""")

        fun fromStoredString(value: String?): LibrarySourceFilter = when {
            value == null -> All
            value == "ALL" -> All
            value == "LOCAL" -> Local
            value == "SERVER" -> All // legacy "any server" -> All
            else -> SERVER_ID_PATTERN.matchEntire(value)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
                ?.let(::Server)
                ?: All
        }
    }
}

data class LibraryPrefs(
    val sortKey: LibrarySortKey = LibrarySortKey.RECENT,
    val sortReversed: Boolean = false,
    val groupBy: LibraryGroupBy = LibraryGroupBy.NONE,
    val sourceFilter: LibrarySourceFilter = LibrarySourceFilter.All,
    val formatFilter: LibraryFormat = LibraryFormat.ALL,
    val statusFilter: LibraryStatus = LibraryStatus.ALL,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val density: LibraryDensity = LibraryDensity.MEDIUM,
    val showContinueReading: Boolean = true,
    val cardShowProgress: Boolean = true,
    val cardShowAuthor: Boolean = true,
    val cardShowFormatBadge: Boolean = false
)

@Singleton
class LibraryPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SORT_KEY = stringPreferencesKey("sort_key")
        val SORT_REVERSED = booleanPreferencesKey("sort_reversed")
        val GROUP_BY = stringPreferencesKey("group_by")
        val SOURCE = stringPreferencesKey("source_filter")
        val FORMAT = stringPreferencesKey("format_filter")
        val STATUS = stringPreferencesKey("status_filter")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val DENSITY = stringPreferencesKey("density")
        val SHOW_CONTINUE_READING = booleanPreferencesKey("show_continue_reading")
        val CARD_PROGRESS = booleanPreferencesKey("card_show_progress")
        val CARD_AUTHOR = booleanPreferencesKey("card_show_author")
        val CARD_FORMAT_BADGE = booleanPreferencesKey("card_show_format_badge")
    }

    val prefsFlow: Flow<LibraryPrefs> = context.libraryPreferencesDataStore.data.map { p ->
        readPrefs(p)
    }

    suspend fun update(transform: (LibraryPrefs) -> LibraryPrefs) {
        context.libraryPreferencesDataStore.edit { p ->
            val next = transform(readPrefs(p))
            p[Keys.SORT_KEY] = next.sortKey.name
            p[Keys.SORT_REVERSED] = next.sortReversed
            p[Keys.GROUP_BY] = next.groupBy.name
            p[Keys.SOURCE] = next.sourceFilter.toStoredString()
            p[Keys.FORMAT] = next.formatFilter.name
            p[Keys.STATUS] = next.statusFilter.name
            p[Keys.VIEW_MODE] = next.viewMode.name
            p[Keys.DENSITY] = next.density.name
            p[Keys.SHOW_CONTINUE_READING] = next.showContinueReading
            p[Keys.CARD_PROGRESS] = next.cardShowProgress
            p[Keys.CARD_AUTHOR] = next.cardShowAuthor
            p[Keys.CARD_FORMAT_BADGE] = next.cardShowFormatBadge
        }
    }

    private fun readPrefs(p: Preferences): LibraryPrefs = LibraryPrefs(
        sortKey = p[Keys.SORT_KEY]?.let { runCatching { LibrarySortKey.valueOf(it) }.getOrNull() }
            ?: LibrarySortKey.RECENT,
        sortReversed = p[Keys.SORT_REVERSED] ?: false,
        groupBy = p[Keys.GROUP_BY]?.let { runCatching { LibraryGroupBy.valueOf(it) }.getOrNull() }
            ?: LibraryGroupBy.NONE,
        sourceFilter = LibrarySourceFilter.fromStoredString(p[Keys.SOURCE]),
        formatFilter = p[Keys.FORMAT]?.let { runCatching { LibraryFormat.valueOf(it) }.getOrNull() }
            ?: LibraryFormat.ALL,
        statusFilter = p[Keys.STATUS]?.let { runCatching { LibraryStatus.valueOf(it) }.getOrNull() }
            ?: LibraryStatus.ALL,
        viewMode = p[Keys.VIEW_MODE]?.let { runCatching { LibraryViewMode.valueOf(it) }.getOrNull() }
            ?: LibraryViewMode.GRID,
        density = p[Keys.DENSITY]?.let { runCatching { LibraryDensity.valueOf(it) }.getOrNull() }
            ?: LibraryDensity.MEDIUM,
        showContinueReading = p[Keys.SHOW_CONTINUE_READING] ?: true,
        cardShowProgress = p[Keys.CARD_PROGRESS] ?: true,
        cardShowAuthor = p[Keys.CARD_AUTHOR] ?: true,
        cardShowFormatBadge = p[Keys.CARD_FORMAT_BADGE] ?: false
    )
}
