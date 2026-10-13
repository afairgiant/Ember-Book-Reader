# Ember Book Reader

## Project Overview
Ember is a modern Android EPUB/PDF reader app. Primary companion to self-hosted Grimmory (formerly Booklore) book servers with native progress sync via kosync. Also works as a standalone reader for local files.

## Architecture

### Module Structure
- `:app` — UI layer (Compose screens, ViewModels, navigation, theme, DI modules)
- `:core` — Data layer (models, database, network, OPDS, kosync, repositories)
- `:feature` — Feature logic (future extraction point, currently thin)

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 (Material You)
- **Reader**: Readium Kotlin Toolkit 3.1.2 (Fragment-based navigator wrapped in Compose)
- **Database**: Room (SQLite)
- **Networking**: Ktor Client + Kotlin Serialization
- **DI**: Hilt
- **Async**: Kotlin Coroutines + Flow
- **Image Loading**: Coil
- **Min SDK**: 28 (Android 9)
- **Target SDK**: 35 (Android 15)
- **Package (source namespace)**: com.ember.reader
- **Application ID (Play Store)**: app.ember.reader

### Architecture Pattern
- **UI**: Compose screen + ViewModel (one per screen), stateless composables with state hoisting
- **Data**: Repository (single source of truth) → DAO + Remote source
- **No use case/interactor classes** unless logic is shared across multiple ViewModels
- **State**: Sealed classes for UI state (Loading, Success, Error)
- **Reactive**: Flow for streams, suspend for one-shot operations. No LiveData.

## Code Conventions

### Kotlin
- Standard Kotlin naming conventions, no abbreviations (e.g., `serverRepository` not `serverRepo`)
- Trailing commas everywhere for cleaner diffs
- `Result<T>` wrapper pattern for error handling consistently
- One Hilt module per core subpackage (DatabaseModule, NetworkModule, SyncModule)
- Constructor injection everywhere
- Preview functions for every Compose screen

### File Organization
- One public class/interface per file (exceptions: small related sealed classes)
- File name matches the primary class name
- Composables: `ScreenNameScreen.kt` for full screens, `ComponentName.kt` for reusable components
- ViewModels: `ScreenNameViewModel.kt`

### Dependencies
- Version catalog (`libs.versions.toml`) for all dependencies
- No hardcoded version strings in build files

## Key Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Simple module structure (:app, :core) | Faster development, split later when needed |
| 2 | Ktor Client over OkHttp | Better coroutines/serialization fit |
| 3 | Fragment-based Readium navigator | Compose navigator is experimental/unreleased |
| 4 | OPDS 1.2 (Atom XML) | Matches Grimmory's implementation |
| 5 | UUID-based book IDs | Supports both OPDS and local books |
| 6 | Percentage for cross-client kosync sync | Readium Locator stored locally for precision |
| 7 | MD5-hashed password in x-auth-key | Matches KOReader and Grimmory conventions |
| 8 | Random UUID device ID on first launch | Matches KOReader convention |
| 9 | Separate OPDS and kosync credentials | Grimmory manages them independently |
| 10 | Partial MD5 for document hashing | Matches KOReader's algorithm for compatibility |

## Server Integration

### OPDS (Catalog Browsing)
- Grimmory endpoint: `/api/v1/opds`
- Auth: HTTP Basic Auth
- Format: OPDS 1.2 Atom XML

### Kosync (Progress Sync)
- Grimmory endpoint: `/api/koreader`
- Auth: `x-auth-user` + `x-auth-key` (MD5 of password) headers
- Accept: `application/vnd.koreader.v1+json`
- User registration blocked on Grimmory (users created via web UI)
- Device ID: random UUID, persisted on first launch
- Device name: "Ember" (user-configurable later)
- Document hash: partial MD5 matching KOReader's algorithm

### Kosync Push (PUT /syncs/progress)
```json
{
  "document": "<partial_md5_hash>",
  "progress": "<readium_locator_json>",
  "percentage": 0.42,
  "device": "Ember",
  "device_id": "<uuid>"
}
```

### Kosync Pull (GET /syncs/progress/:document)
Response contains: document, progress, percentage, device, device_id, timestamp

## Known Limitations

### EPUB CFIs (highlights, bookmarks, progress)
Ember computes real EPUB CFIs by injecting foliate-js's `epubcfi.js` (the library Grimmory's web reader uses) into Readium's WebView (`app/src/main/assets/cfi/`, `CfiBridge`). Highlights, bookmarks and progress pushed to Grimmory carry them, and items pulled from Grimmory are placed in the book from their CFI. What remains:

- **No CFI, no push.** A highlight or bookmark without a real CFI stays on the device: fixed-layout EPUBs, PDFs (PDF bookmarks are not synced at all), and any page where the script fails. Progress falls back to the placeholder `epubcfi(/6/2)` and the percentage.
- **Older highlights and bookmarks.** Items pushed before Ember had real CFIs sit on the server with a fake one and do not show in the web reader. The "repair" action in the highlights sheet replaces such highlights (create new, delete old; the server cannot change an annotation's CFI). The one in the bookmarks sheet updates such bookmarks in place. A bookmark has no text to find it by, so its repaired CFI is worked out from how far into the chapter it was and can be off by some lines. Only the device that made a bookmark can repair it; other devices pick the real CFI up on their next sync.
- **Placement is lazy.** A synced highlight or bookmark is placed the first time its chapter is opened in the reader, not at sync time. Tapping one in the bookmarks or highlights sheet before then opens it by its CFI, so it still lands on the right text.
- **Fixed-layout books carry nothing to the server.** Checked on a device: text cannot be selected in one, so it has no highlights; a bookmark is kept but has no CFI and stays on the device; progress goes by percentage. Reflowable books are checked in both paginated and scroll mode.
- **The CFI depends on the book file.** If the server's copy of the EPUB changes, existing CFIs may no longer resolve; Ember then leaves the item where it was, or opens progress by percentage.

### EPUB Scroll Mode Infinite Scroll
Scroll mode shows one chapter at a time. Swiping on past the end of a chapter opens the next one, and swiping back past its start opens the one before at its end (`ChapterPullController`); a horizontal swipe changes chapter too. (Auto-scroll, the play button in the reader's bottom bar, also moves on to the next chapter by itself.) The chapters are still separate pages: true continuous/infinite vertical scrolling, with one chapter running into the next on screen, is not implemented. It requires deeper integration with Readium's CSS `overflow: scrolled-continuous` or loading multiple chapters into a single scrollable WebView.

### Grimmory Audiobook Covers in OPDS (fixed upstream in v3.5)
Grimmory before v3.5 doesn't include cover links for audiobooks in its OPDS feed (it checked `coverUpdatedOn` but audiobooks use `audiobookCoverUpdatedOn`). Grimmory v3.5 emits them. Ember still uses `/api/v1/audiobooks/{id}/cover` directly so covers work on older servers.

## Current Status
- **Phase**: 5 — Release Prep
- **Completed**: Foundation, Reader Core, Sync, Polish
- **In Progress**: Unit tests, CI/CD, documentation
