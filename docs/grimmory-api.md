# Grimmory API Reference

Reverse-engineered from the Grimmory source code (Spring Boot 4 / Java 25).
Base URL: `http(s)://<server>:<port>` (default port 6060).

---

## Table of Contents

1. [Authentication](#authentication)
2. [Mobile App API](#mobile-app-api-apiv1app) (recommended for Ember)
3. [Books & Downloads](#books--downloads)
4. [Reading Progress](#reading-progress)
5. [Annotations](#annotations)
6. [Bookmarks](#bookmarks)
7. [Book Notes (V2 - CFI-based)](#book-notes-v2---cfi-based)
8. [Shelves](#shelves)
9. [OPDS Catalog](#opds-catalog)
10. [KoReader / Kosync](#koreader--kosync)
11. [EPUB Reader](#epub-reader)
12. [PDF Reader](#pdf-reader)
13. [Kobo Sync](#kobo-sync)
14. [Komga Compatibility](#komga-compatibility)
15. [Settings & Admin](#settings--admin)
16. [WebSocket (STOMP)](#websocket-stomp)
17. [Enums & Types](#enums--types)
18. [Error Handling](#error-handling)

---

## Authentication

### JWT Token Flow

Grimmory uses JWT Bearer tokens for most endpoints. Access tokens expire in **10 hours**, refresh tokens in **30 days**.

#### Login

```
POST /api/v1/auth/login
```

Request:
```json
{
  "username": "string",
  "password": "string"
}
```

Response `200`:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "isDefaultPassword": "false"
}
```

> **Note:** `isDefaultPassword` is a string ("true"/"false"), not boolean.

#### Refresh Token

```
POST /api/v1/auth/refresh
```

Request:
```json
{
  "refreshToken": "string"
}
```

Response `200`:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc..."
}
```

> **Note:** Refresh response does NOT include `isDefaultPassword`.

#### Remote (Header-Based) Auth

```
GET /api/v1/auth/remote
```

Headers: `X-Remote-Name`, `X-Remote-User`, `X-Remote-Email`, etc.
Only available when `REMOTE_AUTH_ENABLED=true`.

Response `200`: Same as login.

#### OIDC (OpenID Connect)

```
GET  /api/v1/auth/oidc/state              # Generate OIDC state token
POST /api/v1/auth/oidc/callback            # Handle OIDC callback (web)
POST /api/v1/auth/oidc/mobile/callback     # Handle OIDC callback (mobile)
GET  /api/v1/auth/oidc/redirect            # Browser-based OIDC redirect
POST /api/v1/auth/oidc/backchannel-logout  # OpenID Connect back-channel logout
```

### Auth Header

All authenticated endpoints require:
```
Authorization: Bearer <accessToken>
```

### OPDS Auth

OPDS endpoints support **both** JWT Bearer and **HTTP Basic Auth**.

---

## Mobile App API (`/api/v1/app/`)

Dedicated lightweight endpoints for mobile clients. All require JWT auth.

### Current User

```
GET /api/v1/app/users/me
```

Response `200`:
```json
{
  "isAdmin": true,
  "canUpload": true,
  "canDownload": true,
  "canAccessBookdrop": true,
  "maxFileUploadSizeMb": 500
}
```

### List Books (Paginated)

```
GET /api/v1/app/books
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Zero-based page index |
| `size` | int | 50 | Items per page |
| `sort` | string | `addedOn` | Sort field |
| `dir` | string | `desc` | Sort direction (`asc`/`desc`) |
| `libraryId` | long | - | Filter by library |
| `shelfId` | long | - | Filter by shelf |
| `status` | string | - | Filter by ReadStatus |
| `search` | string | - | Search query |
| `fileType` | string | - | Filter by file type |
| `minRating` | int | - | Minimum rating |
| `maxRating` | int | - | Maximum rating |
| `authors` | string | - | Filter by author name |
| `language` | string | - | Filter by language code |

Response `200`:
```json
{
  "content": [
    {
      "id": 123,
      "title": "Book Title",
      "libraryId": 1,
      "libraryName": "Library Name",
      "coverUpdatedOn": "2026-01-15T10:30:00Z",
      "readStatus": "READING",
      "personalRating": 4,
      "authors": ["Author Name"],
      "primaryFileType": "EPUB",
      "addedOn": "2026-01-01T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 150,
  "totalPages": 3,
  "hasNext": true,
  "hasPrevious": false
}
```

### Book Detail

```
GET /api/v1/app/books/{bookId}
```

Response `200`:
```json
{
  "id": 123,
  "libraryId": 1,
  "libraryName": "Library Name",
  "title": "Book Title",
  "subtitle": "Optional Subtitle",
  "authors": ["Author Name"],
  "thumbnailUrl": null,
  "readStatus": "READING",
  "personalRating": 5,
  "seriesName": "Series Name",
  "seriesNumber": 1.0,
  "lastReadTime": "2026-03-20T14:00:00Z",
  "description": "Book description...",
  "categories": ["Fiction"],
  "publisher": "Publisher Name",
  "publishedDate": "2025-01-01",
  "pageCount": 350,
  "isbn13": "978-...",
  "language": "en",
  "goodreadsRating": 4.2,
  "goodreadsReviewCount": 1500,
  "primaryFileType": "EPUB",
  "fileTypes": ["EPUB", "PDF"],
  "files": [
    { "id": 456, "fileName": "book.epub", "fileType": "EPUB" }
  ],
  "readProgress": 0.42,
  "epubProgress": {
    "cfi": "epubcfi(/6/4...)",
    "href": "chapter1.xhtml",
    "percentage": 0.42,
    "ttsPositionCfi": null
  },
  "pdfProgress": {
    "page": 50,
    "percentage": 0.17
  },
  "cbxProgress": {
    "page": 10,
    "percentage": 0.05
  },
  "audiobookProgress": {
    "positionMs": 360000,
    "trackIndex": 2,
    "trackPositionMs": 60000,
    "percentage": 0.25
  },
  "koreaderProgress": {
    "percentage": 0.42,
    "document": "hash",
    "timestamp": 1711440600
  },
  "shelves": [{ "id": 1, "name": "Favorites" }],
  "dateFinished": null,
  "alternativeFormats": [
    { "id": 789, "fileName": "book.pdf", "fileType": "PDF" }
  ],
  "supplementaryFiles": [],
  "coverUpdatedOn": "2026-01-15T10:30:00Z",
  "audiobookCoverUpdatedOn": null,
  "addedOn": "2026-01-01T00:00:00Z",
  "isPhysical": false
}
```

### Search Books

```
GET /api/v1/app/books/search?q=string
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `q` | string | required | Search query |
| `page` | int | 0 | Page index |
| `size` | int | 20 | Page size |

Response: Same paginated format as List Books.

### Continue Reading

```
GET /api/v1/app/books/continue-reading?limit=10
```

Response `200`: Array of `AppBookSummary` (books with active reading progress).

### Continue Listening

```
GET /api/v1/app/books/continue-listening?limit=10
```

Response `200`: Array of `AppBookSummary` (audiobooks with active progress).

### Recently Added

```
GET /api/v1/app/books/recently-added?limit=10
```

Response `200`: Array of `AppBookSummary`.

### Recently Scanned

```
GET /api/v1/app/books/recently-scanned?limit=10
```

Response `200`: Array of `AppBookSummary`.

### Random Books

```
GET /api/v1/app/books/random
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page index |
| `size` | int | 20 | Page size |
| `libraryId` | long | - | Filter by library |

### Update Read Status

```
PUT /api/v1/app/books/{bookId}/status
```

Request:
```json
{
  "status": "READING"
}
```

Values: `UNREAD`, `READING`, `READ`, `DNF`

Response: `200 OK`

### Update Rating

```
PUT /api/v1/app/books/{bookId}/rating
```

Request:
```json
{
  "rating": 4
}
```

Values: 1-5 integer.

Response: `200 OK`

### Libraries

```
GET /api/v1/app/libraries
```

Response `200`:
```json
[
  {
    "id": 1,
    "name": "Library Name",
    "icon": "library_books",
    "bookCount": 150,
    "allowedFormats": ["EPUB", "PDF"],
    "paths": ["/books/fiction"]
  }
]
```

### Authors (Paginated)

```
GET /api/v1/app/authors
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page index |
| `size` | int | 30 | Page size |
| `sort` | string | `name` | Sort field |
| `dir` | string | `asc` | Sort direction |
| `libraryId` | long | - | Filter by library |
| `search` | string | - | Search query |
| `hasPhoto` | boolean | - | Only authors with photos |

Response `200`: Paginated list with `id`, `name`, `asin`, `bookCount`, `hasPhoto`.

### Author Detail

```
GET /api/v1/app/authors/{authorId}
```

Response `200`:
```json
{
  "id": 1,
  "name": "Author Name",
  "description": "Bio text...",
  "asin": "B00...",
  "bookCount": 12,
  "hasPhoto": true
}
```

### Series (Paginated)

```
GET /api/v1/app/series
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page index |
| `size` | int | 20 | Page size |
| `sort` | string | `recentlyAdded` | Sort field |
| `dir` | string | `desc` | Sort direction |
| `libraryId` | long | - | Filter by library |
| `search` | string | - | Search query |
| `status` | string | - | `in-progress` filter |

Response `200`:
```json
{
  "content": [
    {
      "seriesName": "Series Name",
      "bookCount": 5,
      "seriesTotal": 10,
      "authors": ["Author"],
      "booksRead": 3,
      "latestAddedOn": "2026-03-01T00:00:00Z",
      "coverBooks": [123, 456]
    }
  ]
}
```

### Books in Series

```
GET /api/v1/app/series/{seriesName}/books
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page index |
| `size` | int | 20 | Page size |
| `sort` | string | `seriesNumber` | Sort field |
| `dir` | string | `asc` | Sort direction |
| `libraryId` | long | - | Filter by library |

### Filter Options

```
GET /api/v1/app/filter-options
```

| Param | Type | Description |
|-------|------|-------------|
| `libraryId` | long | Filter by library |
| `shelfId` | long | Filter by shelf |
| `magicShelfId` | long | Filter by magic shelf |

Response `200`:
```json
{
  "authors": [{ "name": "Author", "count": 12 }],
  "languages": [{ "code": "en", "label": "English", "count": 100 }],
  "readStatuses": ["UNREAD", "READING", "READ", "DNF"],
  "fileTypes": ["EPUB", "PDF"]
}
```

### Shelves

```
GET /api/v1/app/shelves
```

Response `200`:
```json
[
  {
    "id": 1,
    "name": "Favorites",
    "icon": "star",
    "bookCount": 25,
    "publicShelf": false
  }
]
```

### Magic Shelves

```
GET /api/v1/app/shelves/magic
```

Response `200`: Array with `id`, `name`, `icon`, `iconType`, `publicShelf`.

### Books in Magic Shelf

```
GET /api/v1/app/shelves/magic/{magicShelfId}/books?page=0&size=20
```

### Notebook - Books with Annotations

```
GET /api/v1/app/notebook/books
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page index |
| `size` | int | 20 | Page size |
| `search` | string | - | Search query |

Response `200`:
```json
[
  {
    "bookId": 123,
    "bookTitle": "Book Title",
    "noteCount": 15,
    "authors": ["Author"],
    "coverUpdatedOn": "2026-01-15T10:30:00Z"
  }
]
```

### Notebook - Entries for a Book

```
GET /api/v1/app/notebook/books/{bookId}/entries
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Page index |
| `size` | int | 20 | Page size |
| `types` | string | - | Filter by type |
| `search` | string | - | Search in text/note |
| `sort` | string | `date_desc` | Sort order |

Response `200`:
```json
{
  "content": [
    {
      "id": 1,
      "type": "ANNOTATION",
      "bookId": 123,
      "text": "Selected text...",
      "note": "User's note...",
      "color": "#FFFF00",
      "style": "highlight",
      "chapterTitle": "Chapter 1",
      "cfi": "epubcfi(/6/4...)",
      "createdAt": "2026-01-15T10:30:00Z",
      "updatedAt": "2026-01-15T10:30:00Z"
    }
  ]
}
```

### Notebook - Update Entry

```
PUT /api/v1/app/notebook/entries/{entryId}?type=ANNOTATION
```

Request:
```json
{
  "note": "Updated note (max 5000 chars)",
  "color": "#RRGGBB"
}
```

### Notebook - Delete Entry

```
DELETE /api/v1/app/notebook/entries/{entryId}?type=ANNOTATION
```

Response: `204 No Content`

---

## Books & Downloads

### Get Cover Image

```
GET /api/v1/media/book/{bookId}/cover
```

Response: JPEG image with `Cache-Control` header.

### Get Audiobook Cover

```
GET /api/v1/media/book/{bookId}/audiobook-cover
```

Response: JPEG image with `Cache-Control` header.

### Download Book

```
GET /api/v1/books/{bookId}/download
```

Requires: `canDownload` permission or admin.
Response: Binary file with `Content-Disposition` header.

### Download All Formats (ZIP)

```
GET /api/v1/books/{bookId}/download-all
```

Response: ZIP archive or single file if only one format exists.

### Stream Book Content (Range Requests)

```
GET /api/v1/books/{bookId}/content
```

| Param | Type | Description |
|-------|------|-------------|
| `bookType` | string | Optional file type filter |

Supports HTTP `Range` header for partial content (206).

### Upload Book

```
POST /api/v1/files/upload
```

Multipart form data with the book file.
Requires: `canUpload` permission.

---

## Reading Progress

### Update Progress

```
POST /api/v1/books/progress
```

Request:
```json
{
  "bookId": 123,
  "fileProgress": {
    "bookFileId": 456,
    "positionData": "epubcfi(/6/4[chap1]!/4/2/16,/1:0,/1:100)",
    "positionHref": "chapter1.xhtml",
    "progressPercent": 0.42,
    "ttsPositionCfi": null
  },
  "epubProgress": {
    "cfi": "epubcfi(/6/4[chap1]!/4/2/16,/1:0,/1:100)",
    "href": "chapter1.xhtml",
    "percentage": 0.42,
    "ttsPositionCfi": null
  },
  "dateFinished": null
}
```

The request has two progress mechanisms (use one or both):

**`fileProgress`** (generic, per-file format — `BookFileProgress`):
- `bookFileId` (required) — which file format
- `positionData` — position string (CFI, page number, etc.)
- `positionHref` — current resource href
- `progressPercent` (required) — 0.0-1.0
- `ttsPositionCfi` — TTS cursor position

**Format-specific progress** (siblings of `fileProgress`, not nested):
- `epubProgress`: `cfi` (required), `href`, `percentage` (required, 0-1), `ttsPositionCfi`
- `pdfProgress`: `page` (required), `percentage` (required, 0-1)
- `cbxProgress`: `page` (required), `percentage` (required, 0-1)
- `audiobookProgress`: `positionMs` (required), `trackIndex`, `trackPositionMs`, `percentage` (required, 0-1)

Response: `204 No Content`

### Reading Sessions

```
POST /api/v1/reading-sessions
```

Request:
```json
{
  "bookId": 123,
  "bookType": "EPUB",
  "startTime": "2026-03-26T10:00:00Z",
  "endTime": "2026-03-26T11:00:00Z",
  "durationSeconds": 3600,
  "startProgress": 0.40,
  "endProgress": 0.45,
  "progressDelta": 0.05,
  "startLocation": "epubcfi(...)",
  "endLocation": "epubcfi(...)"
}
```

Response: `202 Accepted`

### Get Reading Sessions

```
GET /api/v1/reading-sessions/book/{bookId}?page=0&size=5
```

Response: Paginated reading session list.

---

## Annotations

### List Annotations for Book

```
GET /api/v1/annotations/book/{bookId}
```

Response `200`:
```json
[
  {
    "id": 1,
    "bookId": 123,
    "cfi": "epubcfi(/6/4...)",
    "text": "Highlighted text...",
    "color": "#FFFF00",
    "style": "highlight",
    "note": "My note about this passage",
    "chapterTitle": "Chapter 1",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  }
]
```

### Create Annotation

```
POST /api/v1/annotations
```

Request:
```json
{
  "bookId": 123,
  "cfi": "epubcfi(/6/4[chap1]!/4/2/16,/1:0,/1:100)",
  "text": "Selected text (max 5000)",
  "color": "#FFFF00",
  "style": "highlight",
  "note": "Optional note (max 5000)",
  "chapterTitle": "Chapter 1 (max 500)"
}
```

Styles: `highlight`, `underline`, `strikethrough`, `squiggly`

### Update Annotation

```
PUT /api/v1/annotations/{annotationId}
```

Request:
```json
{
  "color": "#FF0000",
  "style": "underline",
  "note": "Updated note"
}
```

### Delete Annotation

```
DELETE /api/v1/annotations/{annotationId}
```

Response: `204 No Content`

---

## Bookmarks

### List Bookmarks for Book

```
GET /api/v1/bookmarks/book/{bookId}
```

### Create Bookmark

```
POST /api/v1/bookmarks
```

Request:
```json
{
  "bookId": 123,
  "cfi": "epubcfi(/6/4...)",
  "title": "Bookmark name"
}
```

For audiobooks, use `positionMs` and `trackIndex` instead of `cfi`.

### Update Bookmark

```
PUT /api/v1/bookmarks/{bookmarkId}
```

Request:
```json
{
  "title": "New name",
  "cfi": "epubcfi(...)",
  "color": "#RRGGBB",
  "notes": "Notes text",
  "priority": 3
}
```

### Delete Bookmark

```
DELETE /api/v1/bookmarks/{bookmarkId}
```

Response: `204 No Content`

---

## Book Notes (V2 - CFI-based)

The V2 notes API ties notes to specific locations in EPUBs via CFI.

### List Notes for Book

```
GET /api/v2/book-notes/book/{bookId}
```

Response `200`:
```json
[
  {
    "id": 1,
    "bookId": 123,
    "cfi": "epubcfi(/6/4...)",
    "selectedText": "The selected passage...",
    "noteContent": "My detailed note...",
    "color": "#FFFF00",
    "chapterTitle": "Chapter 1",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  }
]
```

### Create Note

```
POST /api/v2/book-notes
```

Request:
```json
{
  "bookId": 123,
  "cfi": "epubcfi(/6/4[chap1]!/4/2/16,/1:0,/1:100)",
  "selectedText": "The passage (max 5000)",
  "noteContent": "My note (required)",
  "color": "#FFFF00",
  "chapterTitle": "Chapter 1 (max 500)"
}
```

### Update Note

```
PUT /api/v2/book-notes/{noteId}
```

Request:
```json
{
  "noteContent": "Updated note",
  "color": "#FF0000",
  "chapterTitle": "Chapter 1"
}
```

### Delete Note

```
DELETE /api/v2/book-notes/{noteId}
```

Response: `204 No Content`

---

## Shelves

### List Shelves

```
GET /api/v1/shelves
```

### Create Shelf

```
POST /api/v1/shelves
```

Request:
```json
{
  "name": "Favorites",
  "icon": "star",
  "publicShelf": false
}
```

### Get Shelf Books

```
GET /api/v1/shelves/{shelfId}/books
```

### Add Book to Shelf

```
POST /api/v1/shelves/{shelfId}/books
```

Request:
```json
{
  "bookId": 123
}
```

### Remove Book from Shelf

```
DELETE /api/v1/shelves/{shelfId}/books/{bookId}
```

---

## OPDS Catalog

OPDS 1.2 Atom XML format. Supports both JWT Bearer and HTTP Basic Auth.

### Root Catalog

```
GET /api/v1/opds
```

Content-Type: `application/atom+xml;profile=opds-catalog;kind=navigation`

### Navigation Feeds

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/opds/libraries` | Libraries |
| `GET /api/v1/opds/shelves` | User shelves |
| `GET /api/v1/opds/magic-shelves` | Auto-generated shelves |
| `GET /api/v1/opds/authors` | Authors |
| `GET /api/v1/opds/series` | Series |

### Acquisition Feeds

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/opds/catalog` | All books |
| `GET /api/v1/opds/recent` | Recently added |
| `GET /api/v1/opds/surprise` | Random books |

### Search

```
GET /api/v1/opds/search.opds
```

Content-Type: `application/opensearchdescription+xml`

> **Note:** `search.opds` is **publicly accessible** (no auth required). The search template points to `/api/v1/opds/catalog?q={searchTerms}`.

V2 search also available at `GET /api/v2/opds/search.opds`.

### OPDS Download & Cover

```
GET /api/v1/opds/{bookId}/download
GET /api/v1/opds/{bookId}/download?fileId=789    # specific format
GET /api/v1/opds/{bookId}/cover
```

### OPDS Entry Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"
      xmlns:opds="http://opds-spec.org/2010/catalog">
  <title>Booklore OPDS Catalog</title>
  <entry>
    <title>Book Title</title>
    <author><name>Author Name</name></author>
    <link href="/api/v1/opds/123/download"
          rel="http://opds-spec.org/acquisition"
          type="application/epub+zip"/>
    <link href="/api/v1/opds/123/cover"
          rel="http://opds-spec.org/image"
          type="image/jpeg"/>
  </entry>
</feed>
```

---

## KoReader / Kosync

KoReader-compatible sync endpoints. Authentication uses custom headers.

### Auth Headers

```
x-auth-user: <username>
x-auth-key: <MD5 of password>
```

### Authorize

```
GET /api/koreader/users/auth
```

Response `200`:
```json
{
  "key": "user_key"
}
```

### Get Progress

```
GET /api/koreader/syncs/progress/{bookHash}
```

`bookHash` is a partial MD5 of the document (matching KOReader's algorithm).

Response `200`:
```json
{
  "timestamp": 1711440600,
  "document": "abc123hash",
  "percentage": 0.67,
  "progress": "{\"chapter\":\"Chapter 5\"}",
  "device": "KOReader",
  "device_id": "uuid-string"
}
```

### Update Progress

```
PUT /api/koreader/syncs/progress
```

Request:
```json
{
  "timestamp": 1711440600,
  "document": "abc123hash",
  "percentage": 0.67,
  "progress": "{\"readium_locator_json\": \"...\"}",
  "device": "Ember",
  "device_id": "uuid-string"
}
```

Response `200`:
```json
{
  "status": "progress updated"
}
```

### KoReader User Settings

```
GET /api/v1/koreader-users/me          # Get current kosync user
PUT /api/v1/koreader-users/me          # Create/update kosync user
PATCH /api/v1/koreader-users/me/sync?enabled=true     # Toggle sync
PATCH /api/v1/koreader-users/me/sync-progress-with-booklore?enabled=true  # Bridge to web reader
```

---

## EPUB Reader

Server-side EPUB streaming for web reader.

### Get EPUB Info

```
GET /api/v1/epub/{bookId}/info
```

Query: `bookType` (optional)

Response includes spine, manifest, TOC, and metadata.

### Stream EPUB File

```
GET /api/v1/epub/{bookId}/file/{path}
```

Serves individual files from inside the EPUB (HTML chapters, CSS, images, fonts).
Caching headers included.

---

## PDF Reader

### Get PDF Info

```
GET /api/v1/pdf/{bookId}/info
```

Response `200`:
```json
{
  "id": 123,
  "title": "Book Title",
  "pageCount": 300,
  "outline": [...]
}
```

### List Pages

```
GET /api/v1/pdf/{bookId}/pages
```

Response `200`: `[1, 2, 3, ... 300]`

---

## Kobo Sync

Kobo e-reader compatibility endpoints. Token-based auth via URL path.

### Device Auth

```
POST /api/kobo/{token}/v1/auth/device
```

### Initialize

```
GET /api/kobo/{token}/v1/initialization
```

### Library Sync

```
GET /api/kobo/{token}/v1/library/sync
```

### Reading State

```
GET  /api/kobo/{token}/v1/library/{bookId}/state
POST /api/kobo/{token}/v1/library/{bookId}/state
```

### Thumbnails

```
GET /api/kobo/{token}/v1/books/{imageId}/thumbnail/{width}/{height}/*
```

---

## Komga Compatibility

Full Komga API v1 compatibility for comic reader apps.

```
GET /komga/api/v1/libraries
GET /komga/api/v1/libraries/{libraryId}
GET /komga/api/v1/series
GET /komga/api/v1/series/{seriesId}
GET /komga/api/v1/series/{seriesId}/books
GET /komga/api/v1/books
GET /komga/api/v1/books/{bookId}
GET /komga/api/v1/books/{bookId}/file
```

Supports `?clean=true` query parameter to strip null values, empty arrays, and `*Lock` fields from responses.

Auth: HTTP Basic Auth.

---

## Settings & Admin

### App Settings

```
GET /api/v1/settings        # Get all settings (admin)
PUT /api/v1/settings        # Update settings (admin)
GET /api/v1/public-settings  # Public settings (OIDC config, etc.)
```

### Version & Health

```
GET /api/v1/version          # API version
GET /api/v1/healthcheck      # Health check
```

### User Management (Admin)

```
GET    /api/v1/users                         # List users
POST   /api/v1/users                         # Create user
GET    /api/v1/users/{userId}                # Get user
PUT    /api/v1/users/{userId}                # Update user
DELETE /api/v1/users/{userId}                # Delete user
POST   /api/v1/users/{userId}/change-password # Change password
GET    /api/v1/users/{userId}/permissions     # Get permissions
```

### User Stats

```
GET /api/v1/user-stats
```

Reading statistics and heatmaps.

---

## WebSocket (STOMP)

Endpoint: `ws://<server>:<port>/ws`

Auth: JWT token in STOMP `Authorization` header.

Destinations:
- `/topic/*` - Broadcast (library scan progress, metadata updates)
- `/queue/*` - Point-to-point messages
- `/user/*` - User-specific notifications

---

## Enums & Types

### ReadStatus

`UNREAD` | `READING` | `READ` | `DNF`

### BookFileType

`EPUB` | `PDF` | `CBX` | `MOBI` | `AUDIOBOOK` | `AZW3` | `KEPUB` | `KFX`

### Annotation Style

`highlight` | `underline` | `strikethrough` | `squiggly`

### Color Format

Hex string: `#RRGGBB` (e.g., `#FFFF00`)

### Percentage

Float: `0.0` to `1.0`

### CFI (Canonical Fragment Identifier)

EPUB location format: `epubcfi(/6/4[chap1]!/4/2/16,/1:0,/1:100)`

### Timestamps

ISO 8601 format: `2026-03-26T10:30:00Z` (or Unix timestamp for kosync)

### Pagination

Zero-based pages. Response always includes:
- `content` - Array of items
- `page` - Current page (0-based)
- `size` - Items per page
- `totalElements` - Total item count
- `totalPages` - Total page count
- `hasNext` / `hasPrevious` - Navigation flags

---

## Error Handling

### Standard Error Response

```json
{
  "status": 400,
  "message": "Validation error",
  "errors": ["field: error message"]
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Created |
| 202 | Accepted (async) |
| 204 | No Content |
| 206 | Partial Content (range) |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict (duplicate) |
| 429 | Rate Limited |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

### Error Types

- `BOOK_NOT_FOUND`, `LIBRARY_NOT_FOUND`, `SHELF_NOT_FOUND`
- `INVALID_CREDENTIALS`, `GENERIC_UNAUTHORIZED`
- `PERMISSION_DENIED`, `FORBIDDEN`
- `METADATA_LOCKED` - Field is locked from editing
- `RATE_LIMITED` - Login rate limiting
- `FORMAT_NOT_ALLOWED` - File type not supported
- `FILE_TOO_LARGE` - Upload size exceeded
- `TASK_ALREADY_RUNNING` - Concurrent task prevented

---

## Security Filter Chain (Order)

1. OPDS Basic Auth Filter
2. Komga Basic Auth Filter
3. KoReader Auth Filter
4. Kobo Auth Filter
5. Cover JWT Filter
6. Custom Font JWT Filter
7. EPUB Streaming JWT Filter
8. Audiobook Streaming JWT Filter
9. Dual JWT Authentication Filter

---

## Permissions

User-level permissions checked by `@CheckBookAccess` / `@CheckLibraryAccess` aspects:

- `canUpload` - Upload files
- `canDownload` - Download files
- `canAccessBookdrop` - Access bookdrop folder
- `canEditMetadata` - Edit book metadata
- `canDeleteBook` - Delete books
- `canManageLibrary` - Manage library settings
- `canSyncKoReader` - Use KoReader sync
- `isAdmin` - Full admin access
