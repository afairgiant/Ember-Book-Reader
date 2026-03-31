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
- **Package**: com.ember.reader

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

### Highlight/Bookmark Push to Grimmory
Highlights created in Ember push to Grimmory but **don't render in Grimmory's web reader**. Readium Kotlin uses `href + progression + text` locators without EPUB CFI. Grimmory's web reader requires CFI (`epubcfi(/6/112!/4/2...)`) to render highlights. The data IS stored on Grimmory and syncs correctly between Ember devices — it just can't be visualized in the web reader. Pull (Grimmory → Ember) works correctly because Grimmory's web reader generates real CFIs.

**Root cause**: Readium Kotlin Toolkit doesn't expose EPUB CFI for text selections. Readium.js (used by Grimmory's web reader) does.

**Potential fix**: Inject JavaScript into Readium's WebView at selection time to extract CFI from the DOM. Complex and fragile.

### EPUB Scroll Mode Infinite Scroll
Horizontal swipe is the only way to change chapters in scroll mode. True continuous/infinite vertical scrolling across chapters is not yet implemented. Requires deeper integration with Readium's CSS `overflow: scrolled-continuous` or loading multiple chapters into a single scrollable WebView.

### Grimmory Audiobook Covers in OPDS
Grimmory's OPDS feed doesn't include cover links for audiobooks (checks `coverUpdatedOn` but audiobooks use `audiobookCoverUpdatedOn`). Ember works around this by using `/api/v1/audiobooks/{id}/cover` directly. A Grimmory-side fix would resolve this for all OPDS clients.

## Current Status
- **Phase**: 5 — Release Prep
- **Completed**: Foundation, Reader Core, Sync, Polish
- **In Progress**: Unit tests, CI/CD, documentation
