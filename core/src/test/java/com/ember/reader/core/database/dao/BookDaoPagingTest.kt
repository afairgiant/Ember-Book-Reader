package com.ember.reader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.database.query.LibraryQueryBuilder
import com.ember.reader.core.database.query.LibrarySortOrder
import com.ember.reader.core.model.BookFormat
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BookDaoPagingTest {

    private lateinit var db: EmberDatabase
    private lateinit var dao: BookDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EmberDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = db.bookDao()
        runBlocking {
            db.serverDao().insert(
                ServerEntity(
                    id = 1L,
                    name = "test",
                    url = "http://localhost",
                    opdsUsername = "u",
                    kosyncUsername = "u"
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun book(
        id: String,
        title: String,
        author: String? = null,
        series: String? = null,
        seriesIndex: Float? = null,
        addedAtEpoch: Long = 0L,
        format: BookFormat = BookFormat.EPUB,
        localPath: String? = null,
        serverId: Long? = 1L
    ) = BookEntity(
        id = id,
        serverId = serverId,
        title = title,
        author = author,
        series = series,
        seriesIndex = seriesIndex,
        format = format,
        addedAt = Instant.ofEpochSecond(addedAtEpoch),
        localPath = localPath
    )

    private suspend fun load(inputs: LibraryQueryBuilder.Inputs): List<BookEntity> {
        val source = dao.pageBooksForView(LibraryQueryBuilder.build(inputs))
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false
            )
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    @Test
    fun `title sort returns books ordered by title case-insensitive`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "banana"),
                book("2", "Apple"),
                book("3", "cherry")
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.TITLE,
                formatFilter = null,
                downloadedOnly = false,
                query = "",
                sessionIds = null
            )
        )
        assertEquals(listOf("Apple", "banana", "cherry"), out.map { it.title })
    }

    @Test
    fun `author sort places null authors last`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "A", author = null),
                book("2", "B", author = "Zed"),
                book("3", "C", author = "Alan")
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.AUTHOR,
                formatFilter = null,
                downloadedOnly = false,
                query = "",
                sessionIds = null
            )
        )
        assertEquals(listOf("C", "B", "A"), out.map { it.title })
    }

    @Test
    fun `recent sort returns newest first`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "old", addedAtEpoch = 100),
                book("2", "new", addedAtEpoch = 300),
                book("3", "mid", addedAtEpoch = 200)
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.RECENT,
                formatFilter = null,
                downloadedOnly = false,
                query = "",
                sessionIds = null
            )
        )
        assertEquals(listOf("new", "mid", "old"), out.map { it.title })
    }

    @Test
    fun `series sort orders by series then seriesIndex and pushes nulls last`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "Standalone", series = null),
                book("2", "HP2", series = "Harry Potter", seriesIndex = 2f),
                book("3", "HP1", series = "Harry Potter", seriesIndex = 1f),
                book("4", "Alpha3", series = "Alpha", seriesIndex = 3f)
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.SERIES,
                formatFilter = null,
                downloadedOnly = false,
                query = "",
                sessionIds = null
            )
        )
        assertEquals(listOf("Alpha3", "HP1", "HP2", "Standalone"), out.map { it.title })
    }

    @Test
    fun `format filter returns only matching books`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "E", format = BookFormat.EPUB),
                book("2", "P", format = BookFormat.PDF),
                book("3", "A", format = BookFormat.AUDIOBOOK)
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.TITLE,
                formatFilter = BookFormat.PDF,
                downloadedOnly = false,
                query = "",
                sessionIds = null
            )
        )
        assertEquals(listOf("P"), out.map { it.title })
    }

    @Test
    fun `downloadedOnly excludes books without localPath`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "downloaded", localPath = "/tmp/x.epub"),
                book("2", "stream", localPath = null)
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.TITLE,
                formatFilter = null,
                downloadedOnly = true,
                query = "",
                sessionIds = null
            )
        )
        assertEquals(listOf("downloaded"), out.map { it.title })
    }

    @Test
    fun `search matches title or author case-insensitive`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "The Fellowship", author = "Tolkien"),
                book("2", "Mistborn", author = "Sanderson"),
                book("3", "Harry Potter", author = "Rowling")
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.TITLE,
                formatFilter = null,
                downloadedOnly = false,
                query = "tolkien",
                sessionIds = null
            )
        )
        assertEquals(listOf("The Fellowship"), out.map { it.title })
    }

    @Test
    fun `sessionIds scopes results to the given id set`() = runBlocking {
        dao.insertAll(
            listOf(
                book("1", "A"),
                book("2", "B"),
                book("3", "C")
            )
        )
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.TITLE,
                formatFilter = null,
                downloadedOnly = false,
                query = "",
                sessionIds = setOf("1", "3")
            )
        )
        assertEquals(listOf("A", "C"), out.map { it.title })
    }

    @Test
    fun `empty sessionIds returns no results`() = runBlocking {
        dao.insertAll(listOf(book("1", "A")))
        val out = load(
            LibraryQueryBuilder.Inputs(
                serverId = 1L,
                sort = LibrarySortOrder.TITLE,
                formatFilter = null,
                downloadedOnly = false,
                query = "",
                sessionIds = emptySet()
            )
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `getByIds returns only the requested rows`() = runBlocking {
        dao.insertAll(
            listOf(
                book("a", "A"),
                book("b", "B"),
                book("c", "C")
            )
        )
        val out = dao.getByIds(setOf("a", "c"))
        assertEquals(setOf("A", "C"), out.map { it.title }.toSet())
    }
}
