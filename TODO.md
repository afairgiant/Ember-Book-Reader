# Ember — Planned Features & Outstanding Work

## Reader Enhancements

### Reader Customization (Reference: Grimmory's web reader)
- [ ] Configurable tap zones (left/center/right tap behavior)
- [ ] Margins / padding control (horizontal and vertical)
- [ ] Word spacing, letter spacing, paragraph spacing
- [ ] Text alignment options (left, justify, etc.)
- [ ] Publisher styles toggle (respect or override EPUB CSS)
- [ ] Custom font loading (user-provided fonts)
- [ ] Reading progress persistence per-preference (remember font/theme per book vs global)
- [ ] Brightness control that actually adjusts screen brightness (currently UI-only)

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
- [ ] Add a pull pass to `SyncWorker` that fetches remote kosync progress for all downloaded books with a `fileHash`
- [ ] Pre-populates reading progress so books show in "Continue Reading" without needing to open them first
- [ ] Useful when switching devices — all in-progress books appear immediately after sync

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
- [ ] Search across all downloaded books (currently filter chips only)
- [ ] Sort options (title, author, recently read, date added)
- [ ] Long-press to delete downloaded books from library view
- [ ] Batch selection / multi-delete

### Catalog Browser
- [ ] OPDS search integration (Grimmory has `/api/v1/opds/search.opds`)
- [ ] Pagination support (load more books on scroll — `nextPagePath` exists but UI doesn't paginate)
- [ ] "Featured Publication" card at bottom of catalog (shown in designs)

### Server Form
- [ ] Show connection status indicator on saved servers
- [ ] Auto-detect server capabilities (OPDS version, kosync availability)

### Settings / Profile
- [ ] Actual user profile (pull from server if available)
- [ ] Appearance settings (theme toggle light/dark/system, dynamic color toggle)
- [ ] Reading goals configuration
- [ ] Export/import reading data
- [ ] App version info

### Storage Management
- [ ] Show available device storage alongside used
- [ ] Sort options (latest first, largest first, alphabetical)
- [ ] Batch delete
- [ ] Auto-cleanup for old downloads

## Technical

### Testing
- [ ] Unit tests for OPDS parser
- [ ] Unit tests for PartialMd5 (verify KOReader compatibility)
- [ ] Unit tests for URL builder
- [ ] Integration tests for kosync push/pull
- [ ] UI tests for critical flows (connect server, download book, open reader)

### CI/CD
- [ ] GitHub Actions build pipeline
- [ ] Automated APK/AAB builds on release tags
- [ ] Lint and test checks on PRs

### Performance
- [ ] Coil disk cache configuration for cover images
- [ ] Lazy loading for large book catalogs (currently loads all at once)
- [ ] Database indices optimization for large libraries

### Offline
- [ ] Queue downloads for when network is available
- [ ] Offline indicator in UI
- [ ] Graceful degradation when server is unreachable

## Bugs / Known Issues
- [ ] Horizontal swipe in scroll mode still changes chapters (Readium limitation — `disablePageTurnsWhileScrolling` blocks all chapter transitions)
