# Grimmory API Reference

Reverse-engineered from Grimmory source (Spring Boot / Java 25) at tag **v3.0.0**.
Base URL: `http(s)://<server>:<port>` (default port 6060).

> **v3.0.0 highlights** (vs previous doc):
> - Entire `/api/v1/user-stats/**` subsystem now documented (previously undocumented, already used by Ember).
> - `AppBookDetail` per-format progress now includes `updatedAt`; removed `ttsPositionCfi` from the response and `trackPositionMs` from audiobook progress.
> - `koreaderProgress` on book detail is now `{percentage, device, deviceId, lastSyncTime}` (was `{percentage, document, timestamp}`).
> - `ReadStatus` is a 9-value enum — `UNREAD`, `READING`, `RE_READING`, `READ`, `PARTIALLY_READ`, `PAUSED`, `WONT_READ`, `ABANDONED`, `UNSET`. The old doc's `DNF` was never a valid value.
> - `POST /api/v1/books/progress` request now uses generic `fileProgress` as the primary shape; per-format siblings (`epubProgress`, `pdfProgress`, …) still accepted but marked `@Deprecated`.
> - New endpoints: `POST /api/v1/auth/logout`, `GET /api/v1/app/books/ids`, `GET /api/v1/app/books/{id}/progress`, `PUT /api/v1/app/books/{id}/progress`, `GET /api/v1/version/changelog`, all `/api/v1/user-stats/**`, `/api/v2/opds-users` admin CRUD.
> - `GET /api/koreader/users/auth` returns `{"username": "<username>"}` (the old `{"key": "..."}` shape was wrong).
> - `AppBookSummary` no longer carries `libraryName`, `subtitle`, `description`, `categories`, `publisher`, or `isbn13` — those live only on detail now. `alternativeFormats`, `supplementaryFiles`, and `dateFinished` are removed from `AppBookDetail`.
> - `AppLibrarySummary` adds `allowedFormats: List<BookFileType>` and `paths: [{id, path}]`.
> - `BookListRequest` (list query) adds ~25 new filter params (`series`, `tag`, `mood`, `narrator`, `ageRating`, `contentRating`, `publishedDate`, `fileSize`, per-provider rating buckets, comic-specific fields, `filterMode`, `magicShelfId`, `unshelved`, etc.).

---

## Table of Contents

1. [Authentication](#authentication)
2. [Users](#users)
3. [Mobile App API](#mobile-app-api-apiv1app) (recommended for Ember)
4. [Books & Downloads](#books--downloads)
5. [Reading Progress](#reading-progress)
6. [Annotations](#annotations)
7. [Bookmarks](#bookmarks)
8. [Book Notes (V2 - CFI-based)](#book-notes-v2---cfi-based)
9. [Shelves](#shelves)
10. [OPDS Catalog](#opds-catalog)
11. [KoReader / Kosync](#koreader--kosync)
12. [User Stats](#user-stats)
13. [EPUB / PDF / CBX Readers](#epub--pdf--cbx-readers)
14. [Audiobook Reader](#audiobook-reader)
15. [Uploads & Bookdrop](#uploads--bookdrop)
16. [File Organization](#file-organization)
17. [Metadata Management](#metadata-management)
18. [Covers & Media](#covers--media)
19. [Kobo Sync](#kobo-sync)
20. [Komga Compatibility](#komga-compatibility)
21. [Settings & Admin](#settings--admin)
22. [WebSocket (STOMP)](#websocket-stomp)
23. [Enums & Types](#enums--types)
24. [Error Handling](#error-handling)
25. [Security Filter Chain](#security-filter-chain)
26. [Permissions](#permissions)

---

## Authentication

### JWT Token Flow

JWT Bearer tokens for most endpoints. Access tokens expire in **10 hours**, refresh tokens in **30 days**.

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

> `isDefaultPassword` is a string ("true"/"false"), not a boolean. Omitted on refresh.

#### Refresh Token

```
POST /api/v1/auth/refresh
```

Request: `{"refreshToken": "string"}`

Response `200`: `{"accessToken": "...", "refreshToken": "..."}`

#### Logout

```
POST /api/v1/auth/logout
```

Request (optional body):
```json
{ "refreshToken": "eyJhbGc..." }
```

Response `200`: `LogoutResponse`.

#### Register

```
POST /api/v1/auth/register
```

Admin-only. Creates an internal user. Response `204 No Content`.

#### Remote (Header-Based) Auth

```
GET /api/v1/auth/remote
```

Reads `X-Remote-Name`, `X-Remote-User`, `X-Remote-Email`, `X-Remote-Groups` headers (configurable names). Only available when remote auth is enabled.

Response `200`: `{"accessToken": "...", "refreshToken": "..."}`

#### OIDC (OpenID Connect)

```
GET  /api/v1/auth/oidc/state                    # Generate OIDC state token
POST /api/v1/auth/oidc/callback                 # Web callback (JSON body: state, code, codeVerifier, redirectUri, nonce)
POST /api/v1/auth/oidc/mobile/callback          # Mobile callback (query params: code, code_verifier, redirect_uri, nonce, state)
GET  /api/v1/auth/oidc/redirect                 # Browser redirect; passes tokens via fragment of app_redirect_uri
POST /api/v1/auth/oidc/backchannel-logout       # OpenID Connect back-channel logout (form-urlencoded logout_token)
```

### Auth Header

All authenticated endpoints require:
```
Authorization: Bearer <accessToken>
```

### OPDS Auth

OPDS endpoints accept **JWT Bearer** OR **HTTP Basic Auth**.

---

## Users

### Current User (full)

```
GET /api/v1/users/me
```

Response `200 BookLoreUser`:
- `id: Long`
- `username: String`
- `isDefaultPassword: boolean`
- `name: String`
- `email: String`
- `provisioningMethod: ProvisioningMethod` (enum)
- `assignedLibraries: List<Library>`
- `permissions: UserPermissions` (serialized with `is` prefix stripped, e.g. `"admin": true` — see [Permissions](#permissions))
- `userSettings: UserSettings` (nested reader settings, preferences, etc.)

### User Admin

```
GET    /api/v1/users                            # admin only
GET    /api/v1/users/{id}
PUT    /api/v1/users/{id}                       # admin only
DELETE /api/v1/users/{id}                       # admin only
PUT    /api/v1/users/change-password            # self
PUT    /api/v1/users/change-user-password       # admin only
PUT    /api/v1/users/{id}/settings              # self only
```

---

## Mobile App API (`/api/v1/app/`)

Lightweight endpoints for mobile clients. All require JWT auth.

### Current App User

```
GET /api/v1/app/users/me
```

Response `200 AppUserInfo`:
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

Query params (all optional; values bind via `BookListRequest`):

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | server default | Zero-based page |
| `size` | int | server default | Items per page |
| `sort` | string | — | Sort field |
| `dir` | string | — | `asc` or `desc` |
| `libraryId` | long | — | Filter by library |
| `shelfId` | long | — | Filter by shelf |
| `magicShelfId` | long | — | Filter by magic shelf |
| `unshelved` | boolean | — | Books not on any shelf |
| `search` | string | — | Free-text search |
| `status` | `List<String>` | — | `ReadStatus` values (max 20) |
| `fileType` | `List<String>` | — | `BookFileType` values (max 20) |
| `minRating`, `maxRating` | int | — | Personal rating bounds |
| `authors`, `language`, `series`, `category`, `publisher`, `tag`, `mood`, `narrator`, `shelves`, `libraries` | `List<String>` | — | Multi-select facet filters (each max 20) |
| `ageRating`, `contentRating`, `matchScore`, `publishedDate`, `fileSize`, `personalRating`, `pageCount`, `shelfStatus` | `List<String>` | — | Bucket filters (max 20) |
| `amazonRating`, `goodreadsRating`, `hardcoverRating`, `lubimyczytacRating`, `ranobedbRating`, `audibleRating` | `List<String>` | — | Per-provider rating buckets |
| `comicCharacter`, `comicTeam`, `comicLocation`, `comicCreator` | `List<String>` | — | Comic-specific facets |
| `filterMode` | string | `or` | Combine-mode across filters: `or`, `and`, `not` |

Response `200 AppPageResponse<AppBookSummary>`:
```json
{
  "content": [
    {
      "id": 123,
      "title": "Book Title",
      "authors": ["Author Name"],
      "thumbnailUrl": null,
      "readStatus": "READING",
      "personalRating": 4,
      "seriesName": "Series",
      "seriesNumber": 1.0,
      "libraryId": 1,
      "addedOn": "2026-01-01T00:00:00Z",
      "lastReadTime": "2026-03-20T14:00:00Z",
      "readProgress": 0.42,
      "primaryFileType": "EPUB",
      "coverUpdatedOn": "2026-01-15T10:30:00Z",
      "audiobookCoverUpdatedOn": null,
      "isPhysical": false,
      "publishedDate": "2025-01-01",
      "pageCount": 350,
      "ageRating": null,
      "contentRating": null,
      "metadataMatchScore": 0.95,
      "fileSizeKb": 1024
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

> `AppBookSummary` no longer carries `libraryName`, `subtitle`, `description`, `categories`, `publisher`, or `isbn13`. Fetch the detail endpoint for those.

### All Book IDs Matching Filters

```
GET /api/v1/app/books/ids
```

Same query params as List Books. Returns `200 List<Long>` — every book ID matching the filter, unpaged. Use for bulk selection.

### Book Detail

```
GET /api/v1/app/books/{bookId}
```

Response `200 AppBookDetail`:
```json
{
  "id": 123,
  "title": "Book Title",
  "authors": ["Author Name"],
  "thumbnailUrl": null,
  "readStatus": "READING",
  "personalRating": 5,
  "seriesName": "Series Name",
  "seriesNumber": 1.0,
  "libraryId": 1,
  "addedOn": "2026-01-01T00:00:00Z",
  "lastReadTime": "2026-03-20T14:00:00Z",
  "subtitle": "Optional Subtitle",
  "description": "Book description...",
  "categories": ["Fiction"],
  "publisher": "Publisher Name",
  "publishedDate": "2025-01-01",
  "pageCount": 350,
  "isbn13": "978-...",
  "language": "en",
  "goodreadsRating": 4.2,
  "goodreadsReviewCount": 1500,
  "libraryName": "Library Name",
  "shelves": [{ "id": 1, "name": "Favorites", "icon": "star", "bookCount": 25, "publicShelf": false }],
  "readProgress": 0.42,
  "primaryFileType": "EPUB",
  "fileTypes": ["EPUB", "PDF"],
  "files": [{ "id": 456, "fileName": "book.epub", "fileType": "EPUB" }],
  "coverUpdatedOn": "2026-01-15T10:30:00Z",
  "audiobookCoverUpdatedOn": null,
  "isPhysical": false,
  "epubProgress": {
    "cfi": "epubcfi(/6/4...)",
    "href": "chapter1.xhtml",
    "percentage": 0.42,
    "updatedAt": "2026-03-20T14:00:00Z"
  },
  "pdfProgress": { "page": 50, "percentage": 0.17, "updatedAt": "..." },
  "cbxProgress": { "page": 10, "percentage": 0.05, "updatedAt": "..." },
  "audiobookProgress": {
    "positionMs": 360000,
    "trackIndex": 2,
    "percentage": 0.25,
    "updatedAt": "..."
  },
  "koreaderProgress": {
    "percentage": 0.42,
    "device": "KOReader",
    "deviceId": "uuid-string",
    "lastSyncTime": "2026-03-20T14:00:00Z"
  }
}
```

> The per-format progress response shapes **do not** include `ttsPositionCfi` or `trackPositionMs`. Those remain on the write-side DTOs (`EpubProgress`, `AudiobookProgress`) but not on the read-side.

### Book Progress (Standalone)

```
GET /api/v1/app/books/{bookId}/progress
```

Response `200 AppBookProgressResponse` — same nested progress objects as the detail, plus `readProgress`, `readStatus`, `lastReadTime`. Use when you need progress without the heavy detail payload.

```
PUT /api/v1/app/books/{bookId}/progress
```

Request `UpdateProgressRequest`:
```json
{
  "fileProgress": {
    "bookFileId": 456,
    "positionData": "epubcfi(/6/4...)",
    "positionHref": "chapter1.xhtml",
    "progressPercent": 0.42,
    "ttsPositionCfi": null
  },
  "dateFinished": null
}
```

Legacy per-format fields (`epubProgress`, `pdfProgress`, `cbxProgress`, `audiobookProgress`) are still accepted but marked `@Deprecated`. Exactly one progress source or `dateFinished` must be set. Response `200 OK`.

### Search / Home Feeds

```
GET /api/v1/app/books/search?q=...&page=0&size=20
GET /api/v1/app/books/continue-reading?limit=10
GET /api/v1/app/books/continue-listening?limit=10
GET /api/v1/app/books/recently-added?limit=10
GET /api/v1/app/books/recently-scanned?limit=10
GET /api/v1/app/books/random?page=0&size=20&libraryId=...
```

`search` and `random` return `AppPageResponse<AppBookSummary>`; the home feeds return `List<AppBookSummary>`.

### Update Read Status

```
PUT /api/v1/app/books/{bookId}/status
```

Request: `{ "status": "READING" }`. Values: see [ReadStatus](#readstatus). Response `200`.

### Update Rating

```
PUT /api/v1/app/books/{bookId}/rating
```

Request: `{ "rating": 4 }`. Value: integer 1–5. Response `200`.

### Libraries

```
GET /api/v1/app/libraries
```

Response `200 List<AppLibrarySummary>`:
```json
[
  {
    "id": 1,
    "name": "Library Name",
    "icon": "library_books",
    "bookCount": 150,
    "allowedFormats": ["EPUB", "PDF"],
    "paths": [{ "id": 5, "path": "/books/fiction" }]
  }
]
```

### Authors

```
GET /api/v1/app/authors
```

| Param | Type | Default |
|-------|------|---------|
| `page` | int | 0 |
| `size` | int | 30 |
| `sort` | string | `name` |
| `dir` | string | `asc` |
| `libraryId` | long | — |
| `search` | string | — |
| `hasPhoto` | boolean | — |

Response `200 AppPageResponse<AppAuthorSummary>`: items carry `id`, `name`, `asin`, `bookCount`, `hasPhoto`.

```
GET /api/v1/app/authors/{authorId}
```

Response `200 AppAuthorDetail`: `id`, `name`, `description`, `asin`, `bookCount`, `hasPhoto`.

### Series

```
GET /api/v1/app/series
```

| Param | Type | Default |
|-------|------|---------|
| `page` | int | 0 |
| `size` | int | 20 |
| `sort` | string | `recentlyAdded` |
| `dir` | string | `desc` |
| `libraryId` | long | — |
| `search` | string | — |
| `status` | string | — (`in-progress` for series with active progress) |

Response `200 AppPageResponse<AppSeriesSummary>`:
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
      "coverBooks": [
        { "bookId": 123, "coverUpdatedOn": "...", "seriesNumber": 1.0, "primaryFileType": "EPUB" }
      ]
    }
  ]
}
```

```
GET /api/v1/app/series/{seriesName}/books?page=0&size=20&sort=seriesNumber&dir=asc&libraryId=...
```

Response `200 AppPageResponse<AppBookSummary>`.

### Shelves

```
GET /api/v1/app/shelves
GET /api/v1/app/shelves/magic
GET /api/v1/app/shelves/magic/{magicShelfId}/books?page=0&size=20
```

- `AppShelfSummary`: `id`, `name`, `icon`, `bookCount`, `publicShelf`.
- `AppMagicShelfSummary`: `id`, `name`, `icon`, `iconType`, `publicShelf`.

### Filter Options

```
GET /api/v1/app/filter-options?libraryId=...&shelfId=...&magicShelfId=...
```

Response `200 AppFilterOptions` — a record with counted lists for every facet the list endpoint supports: `authors`, `languages`, `readStatuses`, `fileTypes`, `categories`, `publishers`, `series`, `tags`, `moods`, `narrators`, `ageRatings`, `contentRatings`, `matchScores`, `publishedYears`, `fileSizes`, `personalRatings`, per-provider rating buckets (`amazonRatings`, `goodreadsRatings`, `hardcoverRatings`, `lubimyczytacRatings`, `ranobedbRatings`, `audibleRatings`), `pageCounts`, `shelfStatuses`, comic facets (`comicCharacters`, `comicTeams`, `comicLocations`, `comicCreators`), `shelves`, `libraries`.

Each entry is `{ "name": "...", "count": N }`; `languages` adds a `code` field.

### Notebook — Books with Annotations

```
GET /api/v1/app/notebook/books?page=0&size=20&search=...
```

Response `200 AppPageResponse<AppNotebookBookSummary>` — `bookId`, `bookTitle`, `noteCount`, `authors`, `coverUpdatedOn`.

### Notebook — Entries for a Book

```
GET /api/v1/app/notebook/books/{bookId}/entries?page=0&size=20&types=...&search=...&sort=date_desc
```

Response `200 AppPageResponse<AppNotebookEntry>` — `id`, `type`, `bookId`, `text`, `note`, `color`, `style`, `chapterTitle`, `createdAt`, `updatedAt`.

### Notebook — Update / Delete

```
PUT    /api/v1/app/notebook/entries/{entryId}?type=ANNOTATION
DELETE /api/v1/app/notebook/entries/{entryId}?type=ANNOTATION
```

Update body: `{ "note": "...", "color": "#RRGGBB" }`. Both endpoints require the `type` query parameter.

---

## Books & Downloads

### Get Book (full)

```
GET /api/v1/books/{bookId}?withDescription=false
```

Returns the full `Book` DTO (richer than `AppBookDetail` — includes metadata locks, embedded file details, etc.). Used by Ember's metadata editor.

### Get Books by IDs

```
GET /api/v1/books/batch?ids=1,2,3&withDescription=false
```

### Download Book

```
GET /api/v1/books/{bookId}/download
```

Requires `canDownload` or admin. Returns binary with `Content-Disposition`.

### Download All Formats (ZIP)

```
GET /api/v1/books/{bookId}/download-all
```

Zip of every file for the book, or a single file if only one format exists.

### Stream Book Content

```
GET /api/v1/books/{bookId}/content?bookType=EPUB
```

Supports HTTP `Range` header for partial content (`206`). Used by the in-app reader.

### Replace Book Content

```
PUT /api/v1/books/{bookId}/content?bookType=PDF
```

Raw binary body. Used by the web document viewer to persist annotation layers. Requires `canEditMetadata`. Response `204`.

### Delete Books

```
DELETE /api/v1/books?ids=1,2,3
```

Requires `canDeleteBook` or admin. Response `BookDeletionResponse`.

### Create Physical Book

```
POST /api/v1/books/physical
```

Body: `CreatePhysicalBookRequest`. Requires `canManageLibrary` or admin. Response `201 Book`.

### Duplicate Detection

```
POST /api/v1/books/duplicates
```

Body: `DuplicateDetectionRequest`. Requires `canManageLibrary` or admin. Response `List<DuplicateGroup>`.

### Toggle Physical Flag

```
PATCH /api/v1/books/{bookId}/physical?physical=true
```

### Attach Book Files (merge formats)

```
POST /api/v1/books/{targetBookId}/attach-file
```

Body `AttachBookFileRequest`: `{ "sourceBookIds": [...], "moveFiles": false }`. Consolidates single-file books into alternative formats of the target.

### Viewer Settings

```
GET /api/v1/books/{bookId}/viewer-setting?bookFileId=456
PUT /api/v1/books/{bookId}/viewer-setting
```

### Shelves Assignment

```
POST /api/v1/books/shelves
```

Body:
```json
{
  "bookIds": [1, 2],
  "shelvesToAssign": [5],
  "shelvesToUnassign": [6]
}
```

### Recommendations

```
GET /api/v1/books/{id}/recommendations?limit=25
```

Max `limit` is 25.

### Embedded / ComicInfo Metadata

```
GET /api/v1/books/{bookId}/file-metadata
GET /api/v1/books/{bookId}/cbx/metadata/comicinfo
```

---

## Reading Progress

### Update Progress

```
POST /api/v1/books/progress
```

Request `ReadProgressRequest`:
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
  "dateFinished": null
}
```

At least one of `fileProgress`, the deprecated per-format siblings, or `dateFinished` must be present.

**Deprecated but still accepted** (siblings of `fileProgress`):
- `epubProgress`: `{ cfi, href, percentage, ttsPositionCfi }` — `cfi` and `percentage` required
- `pdfProgress`: `{ page, percentage }` — both required
- `cbxProgress`: `{ page, percentage }` — both required
- `audiobookProgress`: `{ positionMs, trackIndex, trackPositionMs, percentage }` — `positionMs` and `percentage` required

Response: `204 No Content`.

### Batch Read Status

```
POST /api/v1/books/status
```

Request: `{ "bookIds": [1, 2], "status": "READING" }`. Response: `List<BookStatusUpdateResponse>`.

### Reset Progress

```
POST /api/v1/books/reset-progress?type=BOOKLORE
```

Body: `List<Long>` (max 500). `type` is `BOOKLORE`, `KOREADER`, or `KOBO`. Response: `List<BookStatusUpdateResponse>`.

### Personal Rating

```
PUT /api/v1/books/personal-rating
```

Request: `{ "ids": [1, 2], "rating": 4 }` (record — use `ids` not `bookIds`). Response: `List<PersonalRatingUpdateResponse>`.

```
POST /api/v1/books/reset-personal-rating
```

Body: `List<Long>` (max 500).

### Reading Sessions

```
POST /api/v1/reading-sessions
```

Request `ReadingSessionRequest`:
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

Response: `202 Accepted`.

```
GET /api/v1/reading-sessions/book/{bookId}?page=0&size=5
```

Response: `Page<ReadingSessionResponse>` (includes `createdAt`, `durationFormatted`, etc. alongside the input fields).

---

## Annotations

### List

```
GET /api/v1/annotations/book/{bookId}
GET /api/v1/annotations/{annotationId}
```

Response item shape:
```json
{
  "id": 1,
  "userId": 42,
  "bookId": 123,
  "cfi": "epubcfi(/6/4...)",
  "text": "Highlighted text",
  "color": "#FFFF00",
  "style": "highlight",
  "note": "My note",
  "chapterTitle": "Chapter 1",
  "createdAt": "2026-01-15T10:30:00",
  "updatedAt": "2026-01-15T10:30:00"
}
```

### Create

```
POST /api/v1/annotations
```

Request:
```json
{
  "bookId": 123,
  "cfi": "epubcfi(...)",
  "text": "Selected text (max 5000, required)",
  "color": "#FFFF00",
  "style": "highlight",
  "note": "Optional note (max 5000)",
  "chapterTitle": "Chapter 1 (max 500)"
}
```

Styles (validated via `@Pattern`, not an enum): `highlight`, `underline`, `strikethrough`, `squiggly`.

### Update / Delete

```
PUT    /api/v1/annotations/{annotationId}    # body: { color, style, note }
DELETE /api/v1/annotations/{annotationId}    # 204
```

---

## Bookmarks

### List / Get

```
GET /api/v1/bookmarks/book/{bookId}
GET /api/v1/bookmarks/{bookmarkId}
```

Shape:
```json
{
  "id": 1,
  "userId": 42,
  "bookId": 123,
  "cfi": "epubcfi(...)",
  "positionMs": null,
  "trackIndex": null,
  "pageNumber": null,
  "title": "Bookmark name",
  "color": "#FFFF00",
  "notes": "...",
  "priority": 3,
  "createdAt": "...",
  "updatedAt": "..."
}
```

Use `cfi` for EPUB, `pageNumber` for PDF, `positionMs`/`trackIndex` for audiobooks.

### Create / Update / Delete

```
POST   /api/v1/bookmarks
PUT    /api/v1/bookmarks/{bookmarkId}
DELETE /api/v1/bookmarks/{bookmarkId}
```

Create request:
```json
{
  "bookId": 123,
  "cfi": "epubcfi(...)",
  "positionMs": null,
  "trackIndex": null,
  "pageNumber": null,
  "title": "Bookmark name"
}
```

Update request (all optional): `title` (max 255), `cfi` (max 500), `color` (hex), `notes` (max 2000), `priority` (1–5), `pageNumber` (min 1).

---

## Book Notes (V2 - CFI-based)

CFI-anchored notes, separate from annotations.

```
GET    /api/v2/book-notes/book/{bookId}
GET    /api/v2/book-notes/{noteId}
POST   /api/v2/book-notes
PUT    /api/v2/book-notes/{noteId}
DELETE /api/v2/book-notes/{noteId}
```

Create request:
```json
{
  "bookId": 123,
  "cfi": "epubcfi(...)",
  "selectedText": "passage (max 5000)",
  "noteContent": "note body (required)",
  "color": "#FFFF00",
  "chapterTitle": "Chapter 1 (max 500)"
}
```

Update request: `noteContent`, `color`, `chapterTitle`.

---

## Shelves

```
GET    /api/v1/shelves
POST   /api/v1/shelves
GET    /api/v1/shelves/{shelfId}/books
POST   /api/v1/shelves/{shelfId}/books           # { "bookId": 123 }
DELETE /api/v1/shelves/{shelfId}/books/{bookId}
```

Create body: `{ "name": "...", "icon": "star", "publicShelf": false }`.

Magic-shelf CRUD lives under `/api/v1/magic-shelves` (admin-configured; most clients only read via the app endpoints above).

---

## OPDS Catalog

OPDS 1.2 Atom XML. Accepts JWT Bearer OR HTTP Basic Auth.

### Navigation Feeds

```
GET /api/v1/opds/
GET /api/v1/opds/libraries
GET /api/v1/opds/shelves
GET /api/v1/opds/magic-shelves
GET /api/v1/opds/authors
GET /api/v1/opds/series
```

All return `application/atom+xml;profile=opds-catalog;kind=navigation`.

### Acquisition Feeds

```
GET /api/v1/opds/catalog
GET /api/v1/opds/recent
GET /api/v1/opds/surprise
```

### Search

```
GET /api/v1/opds/search.opds       # OpenSearch description (public, no auth)
GET /api/v2/opds/search.opds
```

### Download & Cover

```
GET /api/v1/opds/{bookId}/download
GET /api/v1/opds/{bookId}/download?fileId=789       # specific format
GET /api/v1/opds/{bookId}/cover
```

### OPDS User Admin (v2)

Admin-only CRUD for OPDS/Basic-auth users:

```
GET    /api/v2/opds-users
POST   /api/v2/opds-users
PATCH  /api/v2/opds-users/{id}
DELETE /api/v2/opds-users/{id}
```

### OPDS Entry Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"
      xmlns:opds="http://opds-spec.org/2010/catalog">
  <title>Grimmory OPDS Catalog</title>
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

KOReader-compatible sync endpoints. Custom header auth.

### Auth Headers

```
x-auth-user: <username>
x-auth-key: <MD5 of password>
```

Accept `application/vnd.koreader.v1+json` on requests.

### Authorize

```
GET /api/koreader/users/auth
```

Response `200`:
```json
{ "username": "<username>" }
```

> Grimmory sends `{"username": "..."}`; earlier documentation showing `{"key": "user_key"}` was incorrect. Clients typically only check the status code.

### User Registration

```
POST /api/koreader/users/create
```

Always `403 Forbidden`. Users must be created via the Grimmory web UI.

### Get Progress

```
GET /api/koreader/syncs/progress/{bookHash}
```

`bookHash` is a partial MD5 matching KOReader's algorithm.

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

Request body uses the same shape as the GET response. Response: `{ "status": "progress updated" }`.

### KoReader User Settings (Grimmory-side)

```
GET   /api/v1/koreader-users/me
PUT   /api/v1/koreader-users/me                                   # body: { username, password }
PATCH /api/v1/koreader-users/me/sync?enabled=true
PATCH /api/v1/koreader-users/me/sync-progress-with-booklore?enabled=true
```

`KoreaderUser` response includes `username`, `password`, `passwordMD5`, `syncEnabled`, `syncWithBookloreReader`. Requires `canSyncKoReader` or admin.

---

## User Stats

All endpoints require `canAccessUserStats` or admin. All under `/api/v1/user-stats/`.

### Reading

| Endpoint | Query | Response |
|----------|-------|----------|
| `GET /reading/heatmap` | `year: int` | `List<{date, count}>` |
| `GET /reading/heatmap/monthly` | `year, month: int` | `List<{date, count}>` |
| `GET /reading/timeline` | `year, week: int` | `List<ReadingSessionTimelineResponse>` |
| `GET /reading/speed` | `year: int` | `List<ReadingSpeedResponse>` |
| `GET /reading/peak-hours` | `year?, month?: int` | `List<{hourOfDay, sessionCount, totalDurationSeconds}>` |
| `GET /reading/favorite-days` | `year?, month?: int` | `List<{dayOfWeek, dayName, sessionCount, totalDurationSeconds}>` |
| `GET /reading/genres` | — | `List<GenreStatisticsResponse>` |
| `GET /reading/completion-timeline` | `year: int` | `List<CompletionTimelineResponse>` |
| `GET /reading/book-completion-heatmap` | — | `List<BookCompletionHeatmapResponse>` |
| `GET /reading/page-turner-scores` | — | `List<PageTurnerScoreResponse>` |
| `GET /reading/completion-race` | `year: int` | `List<CompletionRaceResponse>` |
| `GET /reading/book-distributions` | — | `BookDistributionsResponse { ratingDistribution, progressDistribution, statusDistribution }` |
| `GET /reading/dates` | — | `List<{date, count}>` |
| `GET /reading/session-scatter` | `year: int` | `List<{hourOfDay, durationMinutes, dayOfWeek}>` |
| `GET /reading/streak` | — | `ReadingStreakResponse { currentStreak, longestStreak, totalReadingDays, last52Weeks: [{date, active}] }` |
| `GET /reading/book-timeline` | `year: int` | `List<BookTimelineResponse>` |

### Listening (audiobook)

| Endpoint | Query |
|----------|-------|
| `GET /listening/heatmap/monthly` | `year, month` |
| `GET /listening/weekly-trend` | `weeks` (default 26) |
| `GET /listening/completion` | — |
| `GET /listening/monthly-pace` | `months` (default 12) |
| `GET /listening/finish-funnel` | — |
| `GET /listening/peak-hours` | `year?, month?` |
| `GET /listening/genres` | — |
| `GET /listening/authors` | — |
| `GET /listening/session-scatter` | — |
| `GET /listening/longest-books` | — |

---

## EPUB / PDF / CBX Readers

Server-side streaming for the web reader; Ember mostly doesn't use these because it reads local files via Readium.

### EPUB

```
GET /api/v1/epub/{bookId}/info?bookType=EPUB
GET /api/v1/epub/{bookId}/file/{*filePath}
```

Info returns spine, manifest, TOC, metadata. The file endpoint serves individual files inside the EPUB (chapters, CSS, images, fonts) with cache headers.

### PDF

```
GET /api/v1/pdf/{bookId}/info?bookType=PDF
GET /api/v1/pdf/{bookId}/pages?bookType=PDF
```

Info returns `{ id, title, pageCount, outline, ... }`. Pages returns `[1, 2, …, N]`.

### CBX

```
GET /api/v1/cbx/{bookId}/pages?bookType=CBX
GET /api/v1/cbx/{bookId}/page-info?bookType=CBX
GET /api/v1/cbx/{bookId}/page-dimensions?bookType=CBX
```

---

## Audiobook Reader

```
GET /api/v1/audiobooks/{bookId}/info?bookType=AUDIOBOOK
GET /api/v1/audiobooks/{bookId}/stream?bookType=AUDIOBOOK&trackIndex=0
GET /api/v1/audiobooks/{bookId}/track/{trackIndex}/stream
GET /api/v1/audiobooks/{bookId}/cover
```

Streams support HTTP `Range` (`206`). The stream/cover endpoints also accept a `?token=<jwt>` query parameter for media players that can't set headers.

`AudiobookInfo` response includes the list of tracks, total duration, chapter markers, cover URL, etc.

---

## Uploads & Bookdrop

### Upload Book

```
POST /api/v1/files/upload
```

Multipart form: `file`, `libraryId`, `pathId`. Requires `canUpload` or admin. Response `204`.

### Upload to Bookdrop

```
POST /api/v1/files/upload/bookdrop
```

Multipart form: `file`. Same permission. Response `200 Book`.

### Bookdrop Management

```
GET  /api/v1/bookdrop/notification
GET  /api/v1/bookdrop/files?status=...&page=0&size=20&sort=...
POST /api/v1/bookdrop/imports/finalize
POST /api/v1/bookdrop/files/discard
POST /api/v1/bookdrop/rescan
POST /api/v1/bookdrop/files/extract-pattern
POST /api/v1/bookdrop/files/bulk-edit
```

All require `canAccessBookdrop` or admin.

Finalize body:
```json
{
  "selectAll": false,
  "excludedIds": [],
  "files": [
    { "fileId": 1, "libraryId": 2, "pathId": 3, "metadata": {...} }
  ],
  "defaultLibraryId": 2,
  "defaultPathId": 3
}
```

Finalize response:
```json
{
  "totalFiles": 5,
  "successfullyImported": 4,
  "failed": 1,
  "processedAt": "2026-04-22T10:00:00Z",
  "results": [...]
}
```

---

## File Organization

```
POST /api/v1/files/move
```

Body:
```json
{
  "bookIds": [1, 2],
  "moves": [
    { "bookId": 1, "targetLibraryId": 5, "targetLibraryPathId": 10 }
  ]
}
```

Requires `canManageLibrary` or admin.

---

## Metadata Management

### Prospective (SSE)

```
POST /api/v1/books/{bookId}/metadata/prospective
```

Returns a `text/event-stream` of candidate `BookMetadata` records fetched from configured providers. Body: optional `FetchMetadataRequest`.

### Save Metadata

```
PUT /api/v1/books/{bookId}/metadata?mergeCategories=false&replaceMode=REPLACE_ALL
```

Body: `MetadataUpdateWrapper` (book metadata fields plus lock flags). Response: updated `BookMetadata`.

### Bulk & Admin

```
PUT  /api/v1/books/bulk-edit-metadata
PUT  /api/v1/books/metadata/toggle-all-lock
PUT  /api/v1/books/metadata/toggle-field-locks
POST /api/v1/books/metadata/recalculate-match-scores      # admin
POST /api/v1/books/metadata/manage/consolidate
POST /api/v1/books/metadata/manage/delete
POST /api/v1/books/metadata/isbn-lookup
GET  /api/v1/books/metadata/detail/{provider}/{providerItemId}
```

### Metadata Covers

```
POST /api/v1/books/{bookId}/metadata/cover/upload            # multipart file
POST /api/v1/books/{bookId}/metadata/cover/from-url          # { "url": "..." }
POST /api/v1/books/{bookId}/metadata/audiobook-cover/upload
POST /api/v1/books/{bookId}/metadata/audiobook-cover/from-url
POST /api/v1/books/{bookId}/metadata/covers                  # SSE: Flux<CoverImage>
POST /api/v1/books/{bookId}/regenerate-cover
POST /api/v1/books/{bookId}/regenerate-audiobook-cover
POST /api/v1/books/{bookId}/generate-custom-cover
POST /api/v1/books/{bookId}/generate-custom-audiobook-cover
POST /api/v1/books/regenerate-covers?missingOnly=false
POST /api/v1/books/bulk-regenerate-covers                    # body: { bookIds: [...] }
POST /api/v1/books/bulk-generate-custom-covers
POST /api/v1/books/bulk-upload-cover                         # multipart: file + bookIds
```

---

## Covers & Media

```
GET /api/v1/media/book/{bookId}/cover
GET /api/v1/media/book/{bookId}/thumbnail
GET /api/v1/media/book/{bookId}/audiobook-cover
GET /api/v1/media/book/{bookId}/audiobook-thumbnail
GET /api/v1/media/book/{bookId}/cbx/pages/{pageNumber}?bookType=CBX
GET /api/v1/media/author/{authorId}/photo
GET /api/v1/media/author/{authorId}/thumbnail
GET /api/v1/media/bookdrop/{bookdropId}/cover
```

All return JPEGs with `Cache-Control`.

---

## Kobo Sync

Kobo e-reader compatibility under `/api/kobo/{token}/v1/...`. Token-based auth via URL path. Ember does not consume these.

```
POST /api/kobo/{token}/v1/auth/device
GET  /api/kobo/{token}/v1/initialization
GET  /api/kobo/{token}/v1/library/sync
GET  /api/kobo/{token}/v1/library/{bookId}/metadata
GET  /api/kobo/{token}/v1/library/{bookId}/state
PUT  /api/kobo/{token}/v1/library/{bookId}/state
GET  /api/kobo/{token}/v1/books/{bookId}/download
GET  /api/kobo/{token}/v1/books/{imageId}/thumbnail/{width}/{height}/{isGreyscale}/image.jpg
POST /api/kobo/{token}/v1/analytics/gettests
POST /api/kobo/{token}/v1/analytics/event
POST /api/kobo/{token}/v1/products/{bookId}/nextread
DELETE /api/kobo/{token}/v1/library/{bookId}
# plus a catch-all proxy at /api/kobo/{token}/**
```

---

## Komga Compatibility

Komga API v1 compatibility for comic reader apps (OPDS Basic-auth).

```
GET /komga/api/v1/libraries
GET /komga/api/v1/libraries/{libraryId}
GET /komga/api/v1/series?library_id=&page=0&size=20&unpaged=false
GET /komga/api/v1/series/{seriesId}
GET /komga/api/v1/series/{seriesId}/books
GET /komga/api/v1/series/{seriesId}/thumbnail
GET /komga/api/v1/books?library_id=&page=0&size=20
GET /komga/api/v1/books/{bookId}
GET /komga/api/v1/books/{bookId}/pages
GET /komga/api/v1/books/{bookId}/pages/{pageNumber}?convert=png
GET /komga/api/v1/books/{bookId}/file
GET /komga/api/v1/books/{bookId}/thumbnail
GET /komga/api/v1/collections?page=0&size=20
GET /komga/api/v2/users/me
```

Supports `?clean=true` to strip nulls, empty arrays, and `*Lock` fields from responses.

---

## Settings & Admin

### App Settings

```
GET  /api/v1/settings                # authenticated
PUT  /api/v1/settings                # admin; body: List<{ name, value }>
POST /api/v1/settings/oidc/test      # admin
GET  /api/v1/public-settings         # public
```

### Version & Health

```
GET /api/v1/version                  # { "version": "..." }
GET /api/v1/version/changelog        # List<ReleaseNote>
GET /api/v1/healthcheck              # public; { code, message: "Pong", data: { status, message, version, timestamp } }
```

### User Stats (see [User Stats](#user-stats))

---

## WebSocket (STOMP)

Endpoint: `ws://<server>:<port>/ws`

Auth: JWT in STOMP `Authorization` header.

Destinations:
- `/topic/*` — broadcast (library scan progress, metadata updates)
- `/queue/*` — point-to-point
- `/user/*` — per-user notifications

---

## Enums & Types

### ReadStatus

`UNREAD`, `READING`, `RE_READING`, `READ`, `PARTIALLY_READ`, `PAUSED`, `WONT_READ`, `ABANDONED`, `UNSET`

### BookFileType

`PDF`, `EPUB`, `CBX` (cbz/cbr/cb7), `FB2`, `MOBI`, `AZW3` (azw3/azw), `AUDIOBOOK` (m4b/m4a/mp3/opus). Older names `KEPUB` / `KFX` no longer appear in the enum.

### ResetProgressType

`BOOKLORE`, `KOREADER`, `KOBO`

### Annotation Style

Validated via `@Pattern`, not an enum: `highlight`, `underline`, `strikethrough`, `squiggly`.

### Color Format

Hex string `#RRGGBB` (e.g., `#FFFF00`).

### Percentage

Float `0.0` to `1.0`.

### CFI

`epubcfi(/6/4[chap1]!/4/2/16,/1:0,/1:100)`

### Timestamps

ISO 8601 (`2026-03-26T10:30:00Z`). Unix seconds for kosync.

### Pagination (AppPageResponse)

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

The classic Spring `Page<T>` wrapper (used by `/api/v1/books/page`, `/api/v1/reading-sessions/book/*`, `/api/v1/bookdrop/files`) carries the same data under different keys — Ember treats them as interchangeable.

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
| 302 | Found (OIDC redirect) |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict (duplicate) |
| 429 | Rate Limited |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

### Error Types

`BOOK_NOT_FOUND`, `LIBRARY_NOT_FOUND`, `SHELF_NOT_FOUND`, `INVALID_CREDENTIALS`, `GENERIC_UNAUTHORIZED`, `PERMISSION_DENIED`, `FORBIDDEN`, `METADATA_LOCKED`, `RATE_LIMITED`, `FORMAT_NOT_ALLOWED`, `FILE_TOO_LARGE`, `TASK_ALREADY_RUNNING`, `REMOTE_AUTH_DISABLED`.

---

## Security Filter Chain

Processed in order:

1. OPDS Basic Auth Filter
2. Komga Basic Auth Filter
3. KoReader Auth Filter
4. Kobo Auth Filter
5. Cover JWT Filter
6. Custom Font JWT Filter
7. EPUB Streaming JWT Filter
8. Audiobook Streaming JWT Filter
9. Dual JWT Authentication Filter (Bearer header / query param)

---

## Permissions

Granular flags on `UserPermissions` (serialized with the `is` prefix stripped, so `isAdmin` becomes `"admin": true`):

- `admin` — full access
- `canUpload`, `canDownload`, `canAccessBookdrop`
- `canEditMetadata`, `canManageLibrary`, `canDeleteBook`
- `canSyncKoReader`, `canSyncKobo`, `canEmailBook`, `canAccessOpds`
- `canManageMetadataConfig`, `canAccessLibraryStats`, `canAccessUserStats`
- `canAccessTaskManager`, `canManageGlobalPreferences`
- `canManageIcons`, `canManageFonts`, `canMoveOrganizeFiles`
- `canBulkAutoFetchMetadata`, `canBulkCustomFetchMetadata`, `canBulkEditMetadata`
- `canBulkRegenerateCover`, `canBulkLockUnlockMetadata`
- `canBulkResetBookloreReadProgress`, `canBulkResetKoReaderReadProgress`, `canBulkResetBookReadStatus`
- `demoUser` (serialized form of `isDemoUser`)

Enforced on endpoints via `@PreAuthorize("@securityUtil.<flag>() or @securityUtil.isAdmin()")` and custom `@CheckBookAccess` / `@CheckLibraryAccess` aspects.
