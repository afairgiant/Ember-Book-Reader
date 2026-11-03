package com.ember.reader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.entity.ReadingProgressEntity
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.model.BookFormat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BookDaoStreamedBooksTest {

    private lateinit var db: EmberDatabase
    private lateinit var dao: BookDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EmberDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.bookDao()
        runBlocking {
            db.serverDao().insert(
                ServerEntity(
                    id = GRIMMORY,
                    name = "grimmory",
                    url = "http://localhost",
                    opdsUsername = "u",
                    kosyncUsername = "u",
                    isGrimmory = true,
                ),
            )
            db.serverDao().insert(
                ServerEntity(id = PLAIN_OPDS, name = "opds", url = "http://opds", opdsUsername = "u", kosyncUsername = "u"),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private var nextGrimmoryId = 1

    private suspend fun addBook(
        id: String,
        localPath: String? = null,
        opdsEntryId: String? = "urn:booklore:book:${nextGrimmoryId++}",
        format: BookFormat = BookFormat.EPUB,
        serverId: Long = GRIMMORY,
        percentage: Float? = 0.4f,
        lastReadAt: Long = 1,
        needsSync: Boolean = true,
        locatorJson: String? = null,
    ) {
        dao.insert(
            BookEntity(
                id = id,
                serverId = serverId,
                opdsEntryId = opdsEntryId,
                title = id,
                format = format,
                addedAt = Instant.EPOCH,
                localPath = localPath,
            ),
        )
        if (percentage != null) {
            db.readingProgressDao().upsert(
                ReadingProgressEntity(
                    bookId = id,
                    serverId = serverId,
                    percentage = percentage,
                    lastReadAt = Instant.ofEpochSecond(lastReadAt),
                    needsSync = needsSync,
                    locatorJson = locatorJson,
                ),
            )
        }
    }

    @Test
    fun `continue reading lists a streamed Grimmory book next to downloaded ones`() = runBlocking {
        addBook("downloaded", localPath = "/books/d.epub", lastReadAt = 1)
        addBook("streamed", lastReadAt = 2)
        addBook("streamedPdf", format = BookFormat.PDF, lastReadAt = 3)

        val ids = dao.observeRecentlyReading().first().map { it.id }

        assertEquals(listOf("streamedPdf", "streamed", "downloaded"), ids)
    }

    @Test
    fun `continue reading leaves out books that cannot be streamed or are not in progress`() = runBlocking {
        addBook("plainOpds", opdsEntryId = "urn:uuid:123")
        addBook("notANumber", opdsEntryId = "urn:booklore:book:abc")
        addBook("bareNumber", opdsEntryId = "123")
        addBook("notGrimmoryServer", serverId = PLAIN_OPDS)
        addBook("audiobook", format = BookFormat.AUDIOBOOK)
        addBook("unread", percentage = null)
        addBook("finished", percentage = 0.99f)

        assertEquals(emptyList<String>(), dao.observeRecentlyReading().first().map { it.id })
    }

    @Test
    fun `streamed books for sync are the undownloaded ones with progress not pushed yet`() = runBlocking {
        addBook("downloaded", localPath = "/books/d.epub")
        addBook("streamed")
        addBook("alreadyPushed", needsSync = false)
        addBook("neverOpened", percentage = null)

        assertEquals(listOf("streamed"), dao.getStreamedBooksForServer(GRIMMORY).map { it.id })
    }

    @Test
    fun `streamed books for highlight sync are the undownloaded ones opened on this device`() = runBlocking {
        addBook("downloaded", localPath = "/books/d.epub", locatorJson = "{}")
        addBook("read", locatorJson = "{}", needsSync = false)
        addBook("highlighted", percentage = null)
        db.highlightDao().insert(HighlightEntity(bookId = "highlighted", locatorJson = "{}", createdAt = Instant.EPOCH))
        addBook("bookmarked", percentage = null)
        db.bookmarkDao().insert(BookmarkEntity(bookId = "bookmarked", locatorJson = "{}", createdAt = Instant.EPOCH))
        addBook("progressFromServerOnly")
        addBook("plainOpds", opdsEntryId = "urn:uuid:123", locatorJson = "{}")

        assertEquals(
            setOf("read", "highlighted", "bookmarked"),
            dao.getOpenedStreamedBooksForServer(GRIMMORY).map { it.id }.toSet(),
        )
    }

    @Test
    fun `marking synced leaves progress saved since then still to push`() = runBlocking {
        addBook("streamed", lastReadAt = 5)
        val progressDao = db.readingProgressDao()

        progressDao.markSyncedIfUnchanged("streamed", Instant.ofEpochSecond(4), Instant.ofEpochSecond(9))
        assertEquals(true, progressDao.getByBookId("streamed")?.needsSync)

        progressDao.markSyncedIfUnchanged("streamed", Instant.ofEpochSecond(5), Instant.ofEpochSecond(9))
        assertEquals(false, progressDao.getByBookId("streamed")?.needsSync)
    }

    private companion object {
        const val GRIMMORY = 1L
        const val PLAIN_OPDS = 2L
    }
}
