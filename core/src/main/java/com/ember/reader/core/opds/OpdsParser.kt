package com.ember.reader.core.opds

import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.StringReader
import java.time.Instant
import java.util.UUID

/**
 * Parses OPDS 1.2 Atom XML feeds from Grimmory.
 */
object OpdsParser {

    private const val NS_ATOM = "http://www.w3.org/2005/Atom"
    private const val NS_OPDS = "http://opds-spec.org/2010/catalog"
    private const val NS_DC = "http://purl.org/dc/terms/"

    fun parseFeedTitle(xml: String): String? {
        val parser = createParser(xml)
        var depth = 0
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (depth == 2 && parser.name == "title") {
                        return parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
            event = parser.next()
        }
        return null
    }

    fun parseFeed(xml: String, baseUrl: String): OpdsFeed {
        val parser = createParser(xml)
        var feedTitle = ""
        val entries = mutableListOf<OpdsFeedEntry>()
        var inEntry = false
        var entryId = ""
        var entryTitle = ""
        var entryHref = ""
        var entryContent: String? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "title" -> {
                            val text = parser.nextText()
                            if (inEntry) entryTitle = text else feedTitle = text
                        }
                        "entry" -> {
                            inEntry = true
                            entryId = ""
                            entryTitle = ""
                            entryHref = ""
                            entryContent = null
                        }
                        "id" -> {
                            if (inEntry) entryId = parser.nextText()
                        }
                        "link" -> {
                            if (inEntry) {
                                val rel = parser.getAttributeValue(null, "rel")
                                if (rel == "subsection" || rel == null) {
                                    entryHref = parser.getAttributeValue(null, "href") ?: ""
                                }
                            }
                        }
                        "content" -> {
                            if (inEntry) entryContent = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry") {
                        entries.add(
                            OpdsFeedEntry(
                                id = entryId,
                                title = entryTitle,
                                href = entryHref,
                                content = entryContent,
                            ),
                        )
                        inEntry = false
                    }
                }
            }
            event = parser.next()
        }

        return OpdsFeed(title = feedTitle, entries = entries)
    }

    fun parseBookFeed(xml: String, baseUrl: String, serverId: Long): OpdsBookPage {
        val parser = createParser(xml)
        val books = mutableListOf<Book>()
        var nextPagePath: String? = null
        var inEntry = false
        var entryId = ""
        var title = ""
        var author: String? = null
        var summary: String? = null
        var coverUrl: String? = null
        var downloadUrl: String? = null
        var format = BookFormat.EPUB
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "entry" -> {
                            inEntry = true
                            entryId = ""
                            title = ""
                            author = null
                            summary = null
                            coverUrl = null
                            downloadUrl = null
                            format = BookFormat.EPUB
                        }
                        "id" -> {
                            if (inEntry) entryId = parser.nextText()
                        }
                        "title" -> {
                            if (inEntry) title = parser.nextText()
                        }
                        "name" -> {
                            if (inEntry && author == null) author = parser.nextText()
                        }
                        "summary", "content" -> {
                            if (inEntry && summary == null) summary = parser.nextText()
                        }
                        "link" -> {
                            val rel = parser.getAttributeValue(null, "rel") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val type = parser.getAttributeValue(null, "type") ?: ""

                            if (!inEntry) {
                                if (rel == "next") nextPagePath = href
                            } else {
                                when {
                                    rel.contains("opds-spec.org/image/thumbnail") ||
                                        rel.contains("opds-spec.org/image") -> {
                                        coverUrl = resolveUrl(baseUrl, href)
                                    }
                                    rel.contains("opds-spec.org/acquisition") -> {
                                        downloadUrl = href
                                        format = mimeToFormat(type)
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        books.add(
                            Book(
                                id = UUID.randomUUID().toString(),
                                serverId = serverId,
                                opdsEntryId = entryId,
                                title = title,
                                author = author,
                                description = summary,
                                coverUrl = coverUrl,
                                downloadUrl = downloadUrl,
                                format = format,
                                addedAt = Instant.now(),
                            ),
                        )
                        inEntry = false
                    }
                }
            }
            event = parser.next()
        }

        return OpdsBookPage(books = books, nextPagePath = nextPagePath)
    }

    private fun resolveUrl(baseUrl: String, href: String): String =
        if (href.startsWith("http")) href else "${baseUrl.trimEnd('/')}$href"

    private fun mimeToFormat(mime: String): BookFormat = when {
        mime.contains("epub") -> BookFormat.EPUB
        mime.contains("pdf") -> BookFormat.PDF
        mime.contains("audio") -> BookFormat.AUDIOBOOK
        else -> BookFormat.EPUB
    }

    private val parserFactory: XmlPullParserFactory by lazy {
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
    }

    private fun createParser(xml: String): XmlPullParser =
        parserFactory.newPullParser().apply { setInput(StringReader(xml)) }
}
