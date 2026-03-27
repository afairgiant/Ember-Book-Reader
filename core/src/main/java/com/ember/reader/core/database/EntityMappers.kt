package com.ember.reader.core.database

import com.ember.reader.core.database.entity.BookEntity
import com.ember.reader.core.database.entity.BookmarkEntity
import com.ember.reader.core.database.entity.HighlightEntity
import com.ember.reader.core.database.entity.ReadingProgressEntity
import com.ember.reader.core.database.entity.ReadingSessionEntity
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.model.Book
import com.ember.reader.core.model.Bookmark
import com.ember.reader.core.model.Highlight
import com.ember.reader.core.model.ReadingProgress
import com.ember.reader.core.model.ReadingSession
import com.ember.reader.core.model.Server

fun ServerEntity.toDomain(
    opdsPassword: String = "",
    kosyncPassword: String = "",
    grimmoryPassword: String = "",
): Server = Server(
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
    lastConnected = lastConnected,
)

fun Server.toEntity(): ServerEntity = ServerEntity(
    id = id,
    name = name,
    url = url,
    opdsUsername = opdsUsername,
    kosyncUsername = kosyncUsername,
    grimmoryUsername = grimmoryUsername,
    isGrimmory = isGrimmory,
    lastConnected = lastConnected,
)

fun BookEntity.toDomain(): Book = Book(
    id = id,
    serverId = serverId,
    opdsEntryId = opdsEntryId,
    title = title,
    author = author,
    description = description,
    coverUrl = coverUrl,
    downloadUrl = downloadUrl,
    localPath = localPath,
    format = format,
    fileHash = fileHash,
    series = series,
    seriesIndex = seriesIndex,
    addedAt = addedAt,
    downloadedAt = downloadedAt,
    publisher = publisher,
    language = language,
    subjects = subjects,
    pageCount = pageCount,
    publishedDate = publishedDate,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    serverId = serverId,
    opdsEntryId = opdsEntryId,
    title = title,
    author = author,
    description = description,
    coverUrl = coverUrl,
    downloadUrl = downloadUrl,
    localPath = localPath,
    format = format,
    fileHash = fileHash,
    series = series,
    seriesIndex = seriesIndex,
    addedAt = addedAt,
    downloadedAt = downloadedAt,
    publisher = publisher,
    language = language,
    subjects = subjects,
    pageCount = pageCount,
    publishedDate = publishedDate,
)

fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
    bookId = bookId,
    serverId = serverId,
    percentage = percentage,
    locatorJson = locatorJson,
    kosyncProgress = kosyncProgress,
    lastReadAt = lastReadAt,
    syncedAt = syncedAt,
    needsSync = needsSync,
)

fun ReadingProgress.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
    bookId = bookId,
    serverId = serverId,
    percentage = percentage,
    locatorJson = locatorJson,
    kosyncProgress = kosyncProgress,
    lastReadAt = lastReadAt,
    syncedAt = syncedAt,
    needsSync = needsSync,
)

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    title = title,
    createdAt = createdAt,
)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    title = title,
    createdAt = createdAt,
)

fun HighlightEntity.toDomain(): Highlight = Highlight(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    color = color,
    annotation = annotation,
    createdAt = createdAt,
)

fun Highlight.toEntity(): HighlightEntity = HighlightEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    color = color,
    annotation = annotation,
    createdAt = createdAt,
)

fun ReadingSessionEntity.toDomain(): ReadingSession = ReadingSession(
    id = id,
    bookId = bookId,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    startProgress = startProgress,
    endProgress = endProgress,
)

fun ReadingSession.toEntity(): ReadingSessionEntity = ReadingSessionEntity(
    id = id,
    bookId = bookId,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    startProgress = startProgress,
    endProgress = endProgress,
)
