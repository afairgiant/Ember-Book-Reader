package com.ember.reader.core.testutil

import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.grimmory.GrimmoryAnnotation
import com.ember.reader.core.grimmory.GrimmoryBookDetail
import com.ember.reader.core.grimmory.GrimmoryBookFile
import com.ember.reader.core.grimmory.GrimmoryBookmark
import com.ember.reader.core.grimmory.GrimmoryKoreaderProgress
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.HighlightColor
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.Server
import java.time.Instant

object TestFixtures {

    fun server(
        id: Long = 1L,
        name: String = "Test Server",
        url: String = "http://localhost:8080",
        opdsUsername: String = "opds-user",
        opdsPassword: String = "opds-pass",
        kosyncUsername: String = "kosync-user",
        kosyncPassword: String = "kosync-pass",
        grimmoryUsername: String = "admin",
        grimmoryPassword: String = "admin-pass",
        isGrimmory: Boolean = true,
    ) = Server(
        id = id,
        name = name,
        url = url,
        opdsUsername = opdsUsername,
        opdsPassword = opdsPassword,
        kosyncUsername = kosyncUsername,
        kosyncPassword = kosyncPassword,
        grimmoryUsername = grimmoryUsername,
        grimmoryPassword = grimmoryPassword,
        isGrimmory = isGrimmory,
    )

    fun book(
        id: String = "book-1",
        serverId: Long? = 1L,
        opdsEntryId: String? = "urn:booklore:book:101",
        title: String = "Test Book",
        author: String? = "Test Author",
        format: BookFormat = BookFormat.EPUB,
        fileHash: String? = "abc123hash",
        localPath: String? = "/data/books/test.epub",
        downloadedAt: Instant? = Instant.parse("2026-01-01T00:00:00Z"),
    ) = Book(
        id = id,
        serverId = serverId,
        opdsEntryId = opdsEntryId,
        title = title,
        author = author,
        format = format,
        fileHash = fileHash,
        localPath = localPath,
        downloadedAt = downloadedAt,
    )

    fun bookmarkEntity(
        id: Long = 0,
        bookId: String = "book-1",
        locatorJson: String = """{"href":"/chapter1.xhtml","type":"application/xhtml+xml"}""",
        title: String? = "Chapter 1",
        remoteId: Long? = null,
        createdAt: Instant = Instant.parse("2026-01-01T12:00:00Z"),
        updatedAt: Instant = createdAt,
        deletedAt: Instant? = null,
    ) = BookmarkEntity(
        id = id,
        bookId = bookId,
        locatorJson = locatorJson,
        title = title,
        remoteId = remoteId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    fun highlightEntity(
        id: Long = 0,
        bookId: String = "book-1",
        locatorJson: String = """{"href":"/chapter1.xhtml","type":"application/xhtml+xml","locations":{"fragments":["epubcfi(/6/4)"]}}""",
        color: HighlightColor = HighlightColor.YELLOW,
        annotation: String? = null,
        selectedText: String? = "highlighted text",
        remoteId: Long? = null,
        createdAt: Instant = Instant.parse("2026-01-01T12:00:00Z"),
        updatedAt: Instant = createdAt,
        deletedAt: Instant? = null,
    ) = HighlightEntity(
        id = id,
        bookId = bookId,
        locatorJson = locatorJson,
        color = color,
        annotation = annotation,
        selectedText = selectedText,
        remoteId = remoteId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    fun readingProgress(
        bookId: String = "book-1",
        serverId: Long? = 1L,
        percentage: Float = 0.5f,
        locatorJson: String? = null,
        needsSync: Boolean = false,
    ) = ReadingProgress(
        bookId = bookId,
        serverId = serverId,
        percentage = percentage,
        locatorJson = locatorJson,
        needsSync = needsSync,
    )

    fun grimmoryBookmark(
        id: Long = 1L,
        cfi: String? = "/6/4!/4/2",
        title: String? = "Chapter 1",
        createdAt: String? = "2026-01-01T12:00:00Z",
        updatedAt: String? = "2026-01-01T12:00:00Z",
    ) = GrimmoryBookmark(
        id = id,
        cfi = cfi,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun grimmoryAnnotation(
        id: Long = 1L,
        cfi: String? = "/6/4!/4/2",
        text: String? = "highlighted text",
        color: String? = "FACC15",
        note: String? = null,
        chapterTitle: String? = "Chapter 1",
        createdAt: String? = "2026-01-01T12:00:00Z",
        updatedAt: String? = "2026-01-01T12:00:00Z",
    ) = GrimmoryAnnotation(
        id = id,
        cfi = cfi,
        text = text,
        color = color,
        note = note,
        chapterTitle = chapterTitle,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun grimmoryBookDetail(
        id: Long = 101L,
        title: String = "Test Book",
        readProgress: Float? = null,
        primaryFile: GrimmoryBookFile? = GrimmoryBookFile(id = 1L, fileName = "test.epub"),
        koreaderProgress: GrimmoryKoreaderProgress? = null,
    ) = GrimmoryBookDetail(
        id = id,
        title = title,
        readProgress = readProgress,
        primaryFile = primaryFile,
        koreaderProgress = koreaderProgress,
    )
}
