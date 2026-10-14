package com.ember.reader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.database.entity.BookReaderPreferencesEntity
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.entity.ReadingProgressEntity
import com.ember.reader.core.database.entity.ReadingSessionEntity
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.model.BookFormat
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BookDaoRelinkTest {

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
                ServerEntity(id = 1L, name = "test", url = "http://localhost", opdsUsername = "u", kosyncUsername = "u")
            )
            dao.insert(book(LOCAL, serverId = null, localPath = "/books/local.epub", fileHash = "abc"))
            dao.insert(book(SERVER, serverId = 1L))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun book(id: String, serverId: Long?, localPath: String? = null, fileHash: String? = null) = BookEntity(
        id = id,
        serverId = serverId,
        title = "Dune",
        format = BookFormat.EPUB,
        addedAt = Instant.EPOCH,
        localPath = localPath,
        fileHash = fileHash,
    )

    private fun progress(bookId: String, percentage: Float, lastReadAt: Long) = ReadingProgressEntity(
        bookId = bookId,
        percentage = percentage,
        lastReadAt = Instant.ofEpochSecond(lastReadAt),
    )

    private suspend fun relink() = dao.relink(
        fromId = LOCAL,
        toId = SERVER,
        localPath = "/books/local.epub",
        downloadedAt = Instant.ofEpochSecond(5),
        fileHash = "abc",
    )

    @Test
    fun `relink moves highlights, bookmarks and reading history to the server book`() = runBlocking {
        db.highlightDao().insert(HighlightEntity(bookId = LOCAL, locatorJson = "{}", createdAt = Instant.EPOCH))
        db.bookmarkDao().insert(BookmarkEntity(bookId = LOCAL, locatorJson = "{}", createdAt = Instant.EPOCH))
        db.readingSessionDao().insert(
            ReadingSessionEntity(
                bookId = LOCAL,
                startTime = Instant.EPOCH,
                endTime = Instant.ofEpochSecond(60),
                durationSeconds = 60,
                startProgress = 0f,
                endProgress = 0.1f,
            )
        )

        relink()

        assertNull(dao.getById(LOCAL))
        assertEquals(1, db.highlightDao().getAllByBookId(SERVER).size)
        assertEquals(1, db.bookmarkDao().getAllByBookId(SERVER).size)
        assertEquals(1, db.readingSessionDao().getSessionCountForBook(SERVER))
        val server = dao.getById(SERVER)!!
        assertEquals("/books/local.epub", server.localPath)
        assertEquals("abc", server.fileHash)
    }

    @Test
    fun `relink keeps the local progress when it is newer, under the server's id`() = runBlocking {
        db.readingProgressDao().upsert(progress(LOCAL, percentage = 0.6f, lastReadAt = 200))
        db.readingProgressDao().upsert(progress(SERVER, percentage = 0.2f, lastReadAt = 100))

        relink()

        val moved = db.readingProgressDao().getByBookId(SERVER)!!
        assertEquals(0.6f, moved.percentage)
        assertEquals(1L, moved.serverId)
        assertNull(db.readingProgressDao().getByBookId(LOCAL))
    }

    @Test
    fun `relink keeps the server book's progress when it is newer`() = runBlocking {
        db.readingProgressDao().upsert(progress(LOCAL, percentage = 0.6f, lastReadAt = 100))
        db.readingProgressDao().upsert(progress(SERVER, percentage = 0.2f, lastReadAt = 200))

        relink()

        assertEquals(0.2f, db.readingProgressDao().getByBookId(SERVER)!!.percentage)
        assertNull(db.readingProgressDao().getByBookId(LOCAL))
    }

    @Test
    fun `relink moves reader settings unless the server book has its own`() = runBlocking {
        db.bookReaderPreferencesDao().upsert(BookReaderPreferencesEntity(LOCAL, "{\"local\":1}", Instant.EPOCH))

        relink()

        assertEquals("{\"local\":1}", db.bookReaderPreferencesDao().get(SERVER)!!.preferencesJson)
        assertNull(db.bookReaderPreferencesDao().get(LOCAL))
    }

    @Test
    fun `upsertServerBook updates the book's row instead of adding a second one`() = runBlocking {
        val first = dao.upsertServerBook(1L, "e1", onNew = { book("a", serverId = 1L).copy(opdsEntryId = "e1") }, onExisting = { it })
        val second = dao.upsertServerBook(
            1L,
            "e1",
            onNew = { book("b", serverId = 1L).copy(opdsEntryId = "e1") },
            onExisting = { it.copy(title = "Dune Messiah") },
        )

        assertEquals("a", first)
        assertEquals("a", second)
        assertEquals("Dune Messiah", dao.getByOpdsEntryId("e1", 1L)!!.title)
        assertNull(dao.getById("b"))
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun `a second row for the same server book is refused`() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO books(id,serverId,opdsEntryId,title,format,addedAt) VALUES('a',1,'e1','T','EPUB',0)"
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO books(id,serverId,opdsEntryId,title,format,addedAt) VALUES('b',1,'e1','T','EPUB',0)"
        )
    }

    private companion object {
        const val LOCAL = "local-book"
        const val SERVER = "server-book"
    }
}
