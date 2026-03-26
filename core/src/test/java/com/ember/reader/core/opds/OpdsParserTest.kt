package com.ember.reader.core.opds

import com.ember.reader.core.model.BookFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpdsParserTest {

    companion object {

        private val NAVIGATION_FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>My Library</title>
              <entry>
                <id>urn:catalog:shelves</id>
                <title>Shelves</title>
                <link rel="subsection" href="/api/v1/opds/shelves" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
                <content>Browse by shelf</content>
              </entry>
              <entry>
                <id>urn:catalog:authors</id>
                <title>Authors</title>
                <link rel="subsection" href="/api/v1/opds/authors" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
              </entry>
            </feed>
        """.trimIndent()

        private val BOOK_FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:opds="http://opds-spec.org/2010/catalog"
                  xmlns:dc="http://purl.org/dc/terms/">
              <title>Science Fiction</title>
              <link rel="next" href="/api/v1/opds/shelves/scifi?page=2" type="application/atom+xml;profile=opds-catalog;kind=acquisition"/>
              <entry>
                <id>urn:book:123</id>
                <title>Dune</title>
                <author><name>Frank Herbert</name></author>
                <summary>A desert planet epic.</summary>
                <link rel="http://opds-spec.org/image/thumbnail" href="/covers/dune_thumb.jpg" type="image/jpeg"/>
                <link rel="http://opds-spec.org/image" href="/covers/dune.jpg" type="image/jpeg"/>
                <link rel="http://opds-spec.org/acquisition" href="/downloads/dune.epub" type="application/epub+zip"/>
              </entry>
              <entry>
                <id>urn:book:456</id>
                <title>Neuromancer</title>
                <author><name>William Gibson</name></author>
                <content>Cyberpunk classic.</content>
                <link rel="http://opds-spec.org/acquisition" href="/downloads/neuromancer.pdf" type="application/pdf"/>
              </entry>
            </feed>
        """.trimIndent()

        private val BOOK_FEED_NO_NEXT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Last Page</title>
              <entry>
                <id>urn:book:789</id>
                <title>Audiobook Example</title>
                <link rel="http://opds-spec.org/acquisition" href="/audio/example.mp3" type="audio/mpeg"/>
              </entry>
            </feed>
        """.trimIndent()

        private val EMPTY_FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Empty Shelf</title>
            </feed>
        """.trimIndent()

        private const val BASE_URL = "https://library.example.com"
    }

    // --- parseFeedTitle ---

    @Test
    fun `parseFeedTitle extracts title from navigation feed`() {
        assertEquals("My Library", OpdsParser.parseFeedTitle(NAVIGATION_FEED))
    }

    @Test
    fun `parseFeedTitle extracts title from empty feed`() {
        assertEquals("Empty Shelf", OpdsParser.parseFeedTitle(EMPTY_FEED))
    }

    @Test
    fun `parseFeedTitle returns null when no title`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
            </feed>
        """.trimIndent()
        assertNull(OpdsParser.parseFeedTitle(xml))
    }

    // --- parseFeed ---

    @Test
    fun `parseFeed extracts feed title`() {
        val feed = OpdsParser.parseFeed(NAVIGATION_FEED, BASE_URL)
        assertEquals("My Library", feed.title)
    }

    @Test
    fun `parseFeed extracts all entries`() {
        val feed = OpdsParser.parseFeed(NAVIGATION_FEED, BASE_URL)
        assertEquals(2, feed.entries.size)
    }

    @Test
    fun `parseFeed extracts entry id and title`() {
        val feed = OpdsParser.parseFeed(NAVIGATION_FEED, BASE_URL)
        val first = feed.entries[0]
        assertEquals("urn:catalog:shelves", first.id)
        assertEquals("Shelves", first.title)
    }

    @Test
    fun `parseFeed extracts entry href`() {
        val feed = OpdsParser.parseFeed(NAVIGATION_FEED, BASE_URL)
        assertEquals("/api/v1/opds/shelves", feed.entries[0].href)
    }

    @Test
    fun `parseFeed extracts entry content when present`() {
        val feed = OpdsParser.parseFeed(NAVIGATION_FEED, BASE_URL)
        assertEquals("Browse by shelf", feed.entries[0].content)
        assertNull(feed.entries[1].content)
    }

    @Test
    fun `parseFeed with empty feed returns no entries`() {
        val feed = OpdsParser.parseFeed(EMPTY_FEED, BASE_URL)
        assertEquals("Empty Shelf", feed.title)
        assertEquals(0, feed.entries.size)
    }

    // --- parseBookFeed ---

    @Test
    fun `parseBookFeed extracts books`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertEquals(2, page.books.size)
    }

    @Test
    fun `parseBookFeed extracts book title and author`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        val dune = page.books[0]
        assertEquals("Dune", dune.title)
        assertEquals("Frank Herbert", dune.author)
    }

    @Test
    fun `parseBookFeed extracts summary from summary element`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertEquals("A desert planet epic.", page.books[0].description)
    }

    @Test
    fun `parseBookFeed extracts summary from content element`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertEquals("Cyberpunk classic.", page.books[1].description)
    }

    @Test
    fun `parseBookFeed resolves cover URL`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        // thumbnail link comes first and should be used
        assertEquals("$BASE_URL/covers/dune_thumb.jpg", page.books[0].coverUrl)
    }

    @Test
    fun `parseBookFeed sets download URL and format for EPUB`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertEquals("/downloads/dune.epub", page.books[0].downloadUrl)
        assertEquals(BookFormat.EPUB, page.books[0].format)
    }

    @Test
    fun `parseBookFeed detects PDF format`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertEquals(BookFormat.PDF, page.books[1].format)
    }

    @Test
    fun `parseBookFeed detects audiobook format`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED_NO_NEXT, BASE_URL, serverId = 5L)
        assertEquals(BookFormat.AUDIOBOOK, page.books[0].format)
    }

    @Test
    fun `parseBookFeed sets serverId on all books`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 42L)
        page.books.forEach { assertEquals(42L, it.serverId) }
    }

    @Test
    fun `parseBookFeed sets opdsEntryId`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertEquals("urn:book:123", page.books[0].opdsEntryId)
        assertEquals("urn:book:456", page.books[1].opdsEntryId)
    }

    @Test
    fun `parseBookFeed extracts next page path`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertEquals("/api/v1/opds/shelves/scifi?page=2", page.nextPagePath)
    }

    @Test
    fun `parseBookFeed returns null next page when absent`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED_NO_NEXT, BASE_URL, serverId = 1L)
        assertNull(page.nextPagePath)
    }

    @Test
    fun `parseBookFeed assigns non-null UUID ids`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        page.books.forEach { book ->
            assertNotNull(book.id)
            assertEquals(36, book.id.length) // UUID length
        }
    }

    @Test
    fun `parseBookFeed with no cover sets null coverUrl`() {
        val page = OpdsParser.parseBookFeed(BOOK_FEED, BASE_URL, serverId = 1L)
        assertNull(page.books[1].coverUrl)
    }

    @Test
    fun `parseBookFeed with empty feed returns empty list`() {
        val page = OpdsParser.parseBookFeed(EMPTY_FEED, BASE_URL, serverId = 1L)
        assertEquals(0, page.books.size)
    }
}
