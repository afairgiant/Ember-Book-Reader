package com.ember.reader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ember.reader.core.database.EmberDatabase
import com.ember.reader.core.database.entity.BookEntity
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
                ServerEntity(id = 1L, name = "test", url = "http://localhost", opdsUsername = "u", kosyncUsername = "u"),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addBook(
        id: String,
        localPath: String? = null,
        opdsEntryId: String? = "urn:booklore:book:${id.hashCode()}",
        format: BookFormat = BookFormat.EPUB,
        percentage: Float? = 0.4f,
        lastReadAt: Long = 1,
    ) {
        dao.insert(
            BookEntity(
                id = id,
                serverId = 1L,
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
                    serverId = 1L,
                    percentage = percentage,
                    lastReadAt = Instant.ofEpochSecond(lastReadAt),
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
        addBook("audiobook", format = BookFormat.AUDIOBOOK)
        addBook("unread", percentage = null)
        addBook("finished", percentage = 0.99f)

        assertEquals(emptyList<String>(), dao.observeRecentlyReading().first().map { it.id })
    }

    @Test
    fun `streamed books for sync are the undownloaded ones with progress`() = runBlocking {
        addBook("downloaded", localPath = "/books/d.epub")
        addBook("streamed")
        addBook("neverOpened", percentage = null)

        assertEquals(listOf("streamed"), dao.getStreamedBooksForServer(1L).map { it.id })
    }
}
