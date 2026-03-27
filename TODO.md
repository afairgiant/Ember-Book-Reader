# Ember — Planned Features & Outstanding Work

## Reader Enhancements

### Reader Customization (Reference: Grimmory's web reader)
- [ ] Configurable tap zones (left/center/right tap behavior)
- [x] Page margins control (0.5x to 2.5x)
- [x] Word spacing and letter spacing controls
- [x] Text alignment options (Left, Justify, Center)
- [x] Publisher styles toggle (respect or override EPUB CSS)
- [ ] Custom font loading (user-provided fonts)
- [ ] Reading progress persistence per-preference (remember font/theme per book vs global)
- [x] Brightness control that adjusts system screen brightness via WindowManager

### Continuous Scroll Mode
- [ ] True continuous scroll across chapters (currently per-chapter with swipe between)
- [ ] Requires custom Readium navigator or workaround — Readium's scroll mode is still per-resource

### PDF Reader
- [ ] Apply reader preferences to PDF navigator (currently only EPUB gets preferences)
- [ ] PDF-specific controls (zoom, page fit mode)

## Sync

### Grimmory Web Reader Sync (Requires Grimmory PR)
- [ ] PR to `afairgiant/booklore-n` — `KoreaderService.java`
- [ ] When `convertXPointerToCfi` fails for Ember's Readium Locator format, fall back to setting `epubProgressPercent` from the percentage field
- [ ] Enables reading on phone (Ember) → continuing on PC (Grimmory web reader) and vice versa
- [ ] Need `syncWithBookloreReader` enabled on the KOReader user in Grimmory settings

### Background Progress Pull
- [x] Add a pull pass to `SyncWorker` that fetches remote kosync progress for all downloaded books with a `fileHash`
- [x] Pre-populates reading progress so books show in "Continue Reading" without needing to open them first
- [x] Useful when switching devices — all in-progress books appear immediately after sync

### Progress Sync for Non-Downloaded Books
- [ ] Currently progress only syncs for books that have been downloaded at least once (we need the `fileHash` to query kosync)
- [ ] **Option A — Grimmory PR**: Add a "list all progress" bulk endpoint to kosync API (e.g., `GET /api/koreader/syncs/progress` returning all entries for the user). Ember could then match by OPDS entry ID or title instead of hash.
- [ ] **Option B — Grimmory PR**: Include the file hash in the OPDS feed entries (e.g., as a `<link>` or `<dc:identifier>` element). Ember could then query kosync for any book in the catalog without downloading it first.
- [ ] Either option would let "Continue Reading" show books read on other devices even before downloading them in Ember.

### Grimmory Native API Integration
Full API reference: `docs/grimmory-api.md`

**Phase 1 — Auth & Progress:**
- [x] JWT auth flow: `POST /api/v1/auth/login` → store access + refresh tokens securely
- [x] Token refresh: `POST /api/v1/auth/refresh` (10hr access, 30day refresh, auto-refresh on 401)
- [x] Push progress: `POST /api/v1/books/progress` via `fileProgress.progressPercent` on reader close + SyncWorker
- [x] Pull progress: `GET /api/v1/app/books/{bookId}` on reader open (supplement kosync pull)
- [x] Continue Reading: `GET /api/v1/app/books/continue-reading` pulled in SyncWorker
- [ ] Read status: `PUT /api/v1/app/books/{bookId}/status` (UNREAD/READING/READ/DNF) — API ready, not wired to UI yet
- [x] Reading sessions: `POST /api/v1/reading-sessions` — records duration, start/end progress on reader close (skips < 30s)

**Phase 2 — Catalog via App API (alternative to OPDS):**
- [ ] Books: `GET /api/v1/app/books` — paginated, filterable, includes progress & covers
- [ ] Libraries: `GET /api/v1/app/libraries`
- [ ] Shelves: `GET /api/v1/app/shelves` + magic shelves
- [ ] Series: `GET /api/v1/app/series` with book counts and read progress
- [ ] Authors: `GET /api/v1/app/authors` with photos
- [ ] Search: `GET /api/v1/app/books/search?q=`
- [ ] Covers: `GET /api/v1/media/book/{bookId}/cover` (JWT auth, cacheable)

**Phase 3 — Annotations & Bookmarks Sync:**
- [ ] Annotations: `GET/POST/PUT/DELETE /api/v1/annotations` (CFI-based highlights)
- [ ] Bookmarks: `GET/POST/PUT/DELETE /api/v1/bookmarks` (CFI-based)
- [ ] Notes: `GET/POST/PUT/DELETE /api/v2/book-notes` (V2 CFI-based notes)
- [ ] Notebook: `GET /api/v1/app/notebook/books` + entries per book

**Infrastructure:**
- [x] Auto-detect Grimmory servers (`GET /api/v1/healthcheck`)
- [x] Keep kosync as fallback for non-Grimmory OPDS servers and KOReader compat
- [x] Server form: Grimmory login section always visible alongside OPDS + kosync credentials

### Bidirectional Position Sync
- [ ] Investigate converting Readium Locator ↔ EPUB CFI for exact position sync (not just percentage)
- [ ] Would give precise page-level sync between Ember and Grimmory's web reader

## UI / Design

### Bottom Navigation
- [ ] Add a 4th "Catalog" tab that aggregates catalogs across all connected servers
- [ ] Currently 3 tabs (Home, Library, Profile) — designs showed 4

### Home Screen
- [ ] Reading streaks / reading goals widget
- [ ] "Featured Publication" card (from OPDS featured/recent feed)

### Library
- [x] Search across all downloaded books
- [x] Sort options (title, author, recently read, date added, progress)
- [x] Long-press to delete downloaded books from library view
- [x] Batch selection / multi-delete + batch sync

### Catalog Browser
- [x] Catalog search (search icon in top bar, navigates to book results)
- [ ] Pagination support (load more books on scroll — `nextPagePath` exists but UI doesn't paginate)
- [ ] "Featured Publication" card at bottom of catalog (shown in designs)

### Server Form
- [ ] Show connection status indicator on saved servers
- [ ] Auto-detect server capabilities (OPDS version, kosync availability)

### Settings / Profile
- [x] Connected accounts with status indicators (OPDS/Kosync/Grimmory)
- [x] Reading stats (downloaded, reading, completed)
- [x] App version info
- [ ] Actual user profile (pull from server if available)
- [x] Appearance settings (theme toggle light/dark/system + keep screen on toggle)
- [ ] Reading goals configuration
- [ ] Export/import reading data

### Storage Management
- [x] Show available device storage alongside used
- [x] Sort options (latest first, largest first, alphabetical)
- [x] Batch delete
- [x] Auto-cleanup for old downloads (toggle in Profile + 90-day threshold on app startup)

## Technical

### Testing
- [ ] Unit tests for OPDS parser
- [ ] Unit tests for PartialMd5 (verify KOReader compatibility)
- [ ] Unit tests for URL builder
- [ ] Integration tests for kosync push/pull
- [ ] UI tests for critical flows (connect server, download book, open reader)

### CI/CD
- [x] GitHub Actions build pipeline (ci.yml)
- [x] Automated APK builds on release tags + manual dispatch (release.yml)
- [x] Lint and test checks on PRs

### Performance
- [x] Coil disk cache (50MB) + memory cache (25%) configured in EmberApplication
- [ ] Lazy loading for large book catalogs (currently loads all at once)
- [ ] Database indices optimization for large libraries

### Offline
- [ ] Queue downloads for when network is available
- [ ] Offline indicator in UI
- [ ] Graceful degradation when server is unreachable

## Reader Features (Standard in E-Readers)

### Text-to-Speech (TTS)
- [ ] Android TTS engine integration for read-aloud
- [ ] Play/pause/skip controls overlay
- [ ] Highlight current sentence/paragraph being read
- [ ] Speed control and voice selection
- [ ] Background playback (continue reading with screen off)
- [ ] Readium has TTS support via `readium-navigator-media`

### Search in Book
- [ ] Full-text search within the current book
- [ ] Search results list with context snippets
- [ ] Navigate to search result
- [ ] Readium provides `SearchService` for EPUB search

### Dictionary / Lookup
- [ ] Long-press word to select
- [ ] Built-in dictionary lookup (or Android system dictionary intent)
- [ ] Wikipedia / web search for selected text
- [ ] Translate selected text (via Android translation intent)

### Highlights & Annotations
- [ ] Select text → highlight with color picker
- [ ] Add notes to highlights
- [ ] Highlights list/export
- [ ] Underline, strikethrough styles
- [ ] Readium `DecorableNavigator` supports decorations for highlights

### Reading Statistics
- [ ] Time spent reading today/week/month
- [ ] Pages/percentage per session
- [ ] Estimated time to finish book
- [ ] Daily reading goal tracker
- [ ] Reading streak calendar (similar to GitHub contribution graph)

### Book Details Screen
- [ ] Dedicated book detail page (cover, description, metadata, series info)
- [ ] Rating (personal + Goodreads if available from Grimmory)
- [ ] Read status toggle (Unread/Reading/Read/DNF)
- [ ] Shelves/collections management
- [ ] Download progress indicator
- [ ] Similar books / recommendations (Grimmory has `/api/v1/books/{id}/recommendations`)

### Collections / Shelves
- [ ] Create/manage custom collections locally
- [ ] Sync shelves with Grimmory (`GET/POST /api/v1/shelves`)
- [ ] Assign books to multiple shelves
- [ ] Smart collections (auto-populated by rules: genre, author, read status)

### Night Mode / Blue Light Filter
- [ ] True system brightness control (not just theme)
- [ ] Blue light filter (warm color temperature overlay)
- [ ] Auto-switch based on time of day
- [x] Screen keep-awake while reading (configurable in Profile → Appearance)

### Page Turn Animations
- [ ] Curl/flip animation option
- [ ] Slide animation
- [ ] Fade transition
- [ ] None (instant, current default)

### Orientation Lock
- [x] Lock to portrait/landscape while reading (Auto/Portrait/Landscape in reader settings)
- [ ] Per-book orientation preference
- [ ] Auto-rotate toggle in reader chrome

### File Management
- [ ] Import from file manager / Downloads folder
- [ ] Share book file (export)
- [ ] Bulk import from folder
- [ ] Support for more formats (FB2, MOBI, CBZ/CBR via Readium)

### Multi-Language Support
- [ ] App UI localization (strings.xml for multiple languages)
- [ ] Per-book language setting (for TTS and dictionary)
- [ ] RTL layout support for Arabic/Hebrew

### Accessibility
- [ ] Screen reader (TalkBack) compatibility
- [ ] Large touch targets
- [ ] High contrast mode
- [ ] Reduce motion option

### Notifications
- [ ] Download complete notification
- [ ] Sync complete notification
- [ ] Daily reading reminder (configurable time)
- [ ] New books available on server notification

### Widgets
- [ ] Home screen widget showing current book + progress
- [ ] "Continue Reading" widget that opens directly to last book

## Bugs / Known Issues
- [ ] Horizontal swipe in scroll mode still changes chapters (Readium limitation — `disablePageTurnsWhileScrolling` blocks all chapter transitions)
