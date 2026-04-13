# Ember — Planned Features & Outstanding Work

## Reader Enhancements

### Reader Customization (Reference: Grimmory's web reader)
- [x] Configurable tap zones (left/center/right — Previous Page, Next Page, Toggle Menu, Nothing)
- [x] Page margins control (0.5x to 2.5x)
- [x] Word spacing and letter spacing controls
- [x] Text alignment options (Left, Justify, Center)
- [x] Publisher styles toggle (respect or override EPUB CSS)
- [x] 13 reader themes with Aa preview (AMOLED, Ember, Aurora, Ocean, Mist, Dawnlight, Rosewood, Meadow, Crimson + built-in Light/Dark/Sepia/System)
- [x] Custom theme colors via Readium backgroundColor/textColor (not limited to built-in Theme enum)
- [x] Hyphenation toggle (on by default, wired to Readium hyphens preference)
- [ ] Custom font loading (user-provided fonts)
- [ ] Reading progress persistence per-preference (remember font/theme per book vs global)
- [x] Brightness control that adjusts system screen brightness via WindowManager

### Continuous Scroll Mode
- [ ] True continuous scroll across chapters (currently per-chapter with swipe between)
- [ ] Requires custom Readium navigator or workaround — Readium's scroll mode is still per-resource

### PDF Reader
- [x] Apply reader preferences to PDF navigator (brightness, orientation, keep screen on, preferences sheet)
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
- [x] Read status: `PUT /api/v1/app/books/{bookId}/status` (UNREAD/READING/READ/DNF) — wired to Book Details screen
- [x] Reading sessions: `POST /api/v1/reading-sessions` — records duration, start/end progress on reader close (skips < 30s)

**Phase 2 — Catalog via App API (alternative to OPDS):**
- [ ] Books: `GET /api/v1/app/books` — paginated, filterable, includes progress & covers
- [ ] Libraries: `GET /api/v1/app/libraries`
- [ ] Shelves: `GET /api/v1/app/shelves` + magic shelves
- [ ] Series: `GET /api/v1/app/series` with book counts and read progress
- [ ] Authors: `GET /api/v1/app/authors` with photos
- [ ] Search: `GET /api/v1/app/books/search?q=`
- [ ] Covers: `GET /api/v1/media/book/{bookId}/cover` (JWT auth, cacheable)

**Phase 3 — Book Management:**
- [ ] Ability to edit basic book metadata
- [ ] Ability to change "read" status
- [ ] Ability to shelve books
- [ ] Ability to upload books
- [x] Access to book drop

**Phase 4 — Annotations & Bookmarks Sync:**
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
- [x] Add a 4th "Catalog" tab that aggregates catalogs across all connected servers
- [x] Currently 3 tabs (Home, Library, Profile) — designs showed 4

### Home Screen
- [ ] Reading streaks / reading goals widget
- [x] "Featured Publication" card (from OPDS featured/recent feed)

### Library
- [x] Search across all downloaded books
- [x] Sort options (title, author, recently read, date added, progress)
- [x] Long-press to delete downloaded books from library view
- [x] Batch selection / multi-delete + batch sync

### Catalog Browser
- [x] Catalog search (search icon in top bar, navigates to book results)
- [x] Pagination support (infinite scroll — loads more books when near end of list via nextPagePath / Grimmory page param)
- [ ] "Featured Publication" card at bottom of catalog (shown in designs)

### Server Form
- [x] Show connection status indicator on saved servers (OPDS · Kosync · Grimmory)
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
- [x] Unit tests for OPDS parser (OpdsParserTest — 24 tests)
- [x] Unit tests for PartialMd5 (PartialMd5Test — 8 tests)
- [x] Unit tests for URL builder (UrlBuilderTest — 7 tests)
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
- [x] Offline indicator in UI (banner on home screen when no network)
- [x] Graceful degradation when server is unreachable (cached data shown, friendly error messages, retry buttons)

## Reader Features (Standard in E-Readers)

### Text-to-Speech (TTS)
- [ ] Android TTS engine integration for read-aloud
- [ ] Play/pause/skip controls overlay
- [ ] Highlight current sentence/paragraph being read
- [ ] Speed control and voice selection
- [x] Background playback (continue reading with screen off)
- [ ] Readium has TTS support via `readium-navigator-media`

### Search in Book
- [x] Full-text search within the current book (via Readium SearchService)
- [x] Search results list with context snippets (highlighted match text)
- [x] Navigate to search result (taps locator)
- [x] Search icon in reader top bar → SearchSheet bottom sheet

### Dictionary / Lookup
- [ ] Long-press word to select
- [ ] Built-in dictionary lookup (or Android system dictionary intent)
- [ ] Wikipedia / web search for selected text
- [ ] Translate selected text (via Android translation intent)
- [ ] **Crowdsourced dictionary cache on Grimmory**: add a `/api/v1/dictionary/{word}` endpoint to Grimmory that Ember queries before falling through to Free Dictionary / Wiktionary. Every successful upstream lookup is POSTed back to Grimmory so the server gradually builds a SQLite library of words its users actually read. Over time most lookups would be served from the user's own server — fast, offline-from-the-internet, and shareable across all clients on that Grimmory instance.

### Highlights & Annotations
- [x] Select text → highlight with color picker (custom ActionMode.Callback with Highlight/Note/Copy)
- [x] Add notes to highlights (AnnotationDialog with text input + color picker)
- [x] Highlights list with text previews (HighlightsSheet shows selectedText in quotes)
- [x] Visual decorations rendered in book via Readium DecorableNavigator
- [x] Tap existing highlight to edit note, change color, or delete
- [x] Highlights button in reader top bar
- [ ] Underline, strikethrough styles
- [ ] Highlights export
- [x] Sync highlights to Grimmory API (Phase 3)

### Reading Statistics
- [x] Time spent reading today/week/month (StatsScreen with time cards)
- [x] Pages/percentage per session (recent sessions list with +N% delta)
- [x] Estimated time to finish book (calculated from avg reading speed)
- [x] Reading streak calendar (GitHub-style 12-week activity grid)
- [x] Local reading session storage (ReadingSessionEntity table)
- [x] Current reading streak counter (consecutive days)

### Book Details Screen
- [x] Dedicated book detail page (cover, description, metadata, series info)
- [ ] Rating (personal + Goodreads + Hardcover if available from Grimmory)
- [x] Read status toggle (Unread/Reading/Read/DNF) — Grimmory API
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
- [x] Share book file (export via Android share sheet from Book Details)
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

### Notifications & In-App Feedback
- [x] Download complete notification (system notification)
- [x] Sync complete notification (shown after SyncWorker completes)
- [x] In-app snackbar system for transient feedback (replaces AlertDialogs in Library + Book Details)
- [x] Manual sync success/failure feedback (push/pull result shown as snackbar)
- [x] Download success/failure snackbar on Book Details screen
- [ ] Download progress indicator with "Open" action
- [ ] Sync conflict notification (remote progress differs from local)
- [x] Server connection error feedback (friendly messages for timeout, DNS, SSL, auth, 500 errors)
- [x] Book import success/failure notification
- [ ] Daily reading reminder (configurable time)
- [ ] New books available on server notification
- [ ] Reading milestone notifications (finished book, streak achievements)

### Widgets
- [ ] Home screen widget showing current book + progress
- [ ] "Continue Reading" widget that opens directly to last book

## Bugs / Known Issues
- [ ] Horizontal swipe in scroll mode still changes chapters (Readium limitation — `disablePageTurnsWhileScrolling` blocks all chapter transitions)
