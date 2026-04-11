# Apply Cover from Metadata Provider

## Context

Ember's recent metadata editor (commit `a35b831`) added the ability to pull book metadata from external providers (Google Books, Amazon, Hardcover, Goodreads, etc.) via Grimmory's `POST /api/v1/books/{id}/metadata/prospective` SSE endpoint. Each provider result includes a `thumbnailUrl`, which Ember already displays in the candidate row and in a tap-to-expand full-screen overlay.

What's missing: there's no way to **apply** one of those provider thumbnails as the book's actual cover. Grimmory's backend supports this — `POST /api/v1/books/{id}/metadata/cover/from-url` downloads the image, rewrites the EPUB on disk, updates Grimmory's thumbnail cache, and bumps `coverUpdatedOn`. `MetadataClient.uploadCoverFromUrl` (`core/.../grimmory/MetadataClient.kt:80-92`) already wraps that endpoint. It's just never wired to the UI.

A second, related problem surfaces while fixing this: **Ember has no cache-busting on cover URLs anywhere.** `GrimmoryAppClient.coverUrl()` builds a bare `"${origin}/api/v1/media/book/$id/cover"`, which means once Coil caches it, subsequent cover changes (from this feature, from Grimmory's web UI, or from another Ember session) go stale until the cache is manually evicted. `GrimmoryBookMetadata` and `GrimmoryAppBook` both already expose `coverUpdatedOn: String?` — the fix is small and broadly useful.

This spec covers both: the user-visible "Use this cover" feature, and the app-wide cache-buster pass that makes it actually work.

## Scope

- **Grimmory books only.** Local-book cover apply is explicitly out of scope (would require Ember to parse/rewrite EPUB zips, which it doesn't do anywhere today).
- **Reuses existing provider thumbnails.** We do *not* add Grimmory's separate DuckDuckGo-based cover-search dialog (`POST /api/v1/books/{id}/metadata/covers`). That's a larger, separate feature.

## Design

### Components touched

**Data / networking layer**

- `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryAppClient.kt:196` — `fun coverUrl(baseUrl, grimmoryBookId, coverUpdatedOn: String? = null)` appends `?v=<urlencoded>` when `coverUpdatedOn` is non-null; returns the bare URL when it's null (backwards-compatible).
- `core/src/main/java/com/ember/reader/core/sync/worker/SyncWorker.kt:215` — pass `summary.coverUpdatedOn` (already on `GrimmoryAppBook`, see `GrimmoryAppModels.kt:26`) when constructing the persisted cover URL.
- `app/src/main/java/com/ember/reader/ui/book/BookDetailViewModel.kt:160` — pass `appBook.coverUpdatedOn` when constructing the runtime cover URL.
- `core/src/main/java/com/ember/reader/core/repository/BookRepository.kt` — add `suspend fun updateBookCoverUrl(bookId: String, coverUrl: String)` following the same `get → copy → dao.update` pattern as the existing `updateLocalBookMetadata` (`BookRepository.kt:858`).

**UI layer (editmetadata feature)**

- `app/src/main/java/com/ember/reader/ui/editmetadata/EditMetadataViewModel.kt`
  - New state field on `EditMetadataSuccess`: `applyingCoverUrl: String? = null` — which candidate's cover is currently being uploaded, used to drive per-button spinner state.
  - New action: `fun applyCover(url: String)` — see Data Flow below.
- `app/src/main/java/com/ember/reader/ui/editmetadata/EditMetadataScreen.kt`
  - `CandidateRow` (line 547): add a small "Use this cover" icon button next to the thumbnail. Disabled while `applyingCoverUrl == coverUrl`. `onClick` stops propagation so it doesn't trigger candidate selection.
  - `CoverOverlay` (line 615): add (a) a filled "Use this cover" button and (b) a small bottom-right resolution caption. The overlay closes itself on successful upload.

### Data flow

User taps "Use this cover" (from row or overlay):

1. `EditMetadataScreen` calls `viewModel.applyCover(candidate.thumbnailUrl!!)`. The button is hidden when `thumbnailUrl` is null.
2. `applyCover` sets `applyingCoverUrl = url` in state and dispatches a coroutine.
3. Coroutine calls `metadataClient.uploadCoverFromUrl(server.url, server.id, grimmoryBookId, url)`. Grimmory downloads the image, rewrites the EPUB (server-side `.bak` is created), updates its thumbnail store, and bumps `coverUpdatedOn`.
4. On success:
   - Build a cache-busted cover URL: `"${serverOrigin(server.url)}/api/v1/media/book/${grimmoryBookId}/cover?v=${Instant.now().epochSecond}"`. The value only needs to differ from the previous URL to force Coil to refetch; precise parity with Grimmory's `coverUpdatedOn` isn't required — the next library sync naturally converges the stored value to Grimmory's authoritative timestamp.
   - Call `bookRepository.updateBookCoverUrl(bookId, newUrl)` so the persisted URL is cache-busted everywhere in the app (library grid, book detail).
   - Clear `applyingCoverUrl`. Emit `"Cover applied"` via the existing `_message` flow → existing snackbar.
   - If the `CoverOverlay` was open, close it (clear `selectedCandidate`'s overlay state — see UI notes below).
5. On failure: clear `applyingCoverUrl`, surface `friendlyErrorMessage(e)` via the same snackbar.

**Independence from metadata save:**
- `applyCover` is fully independent of the existing `saveGrimmory()` flow. It works even with no dirty fields.
- It does **not** modify `selectedCandidate`, `edited`, `originalEditable`, or `clearFlags`. The user can apply a cover from a candidate without merging any of the candidate's text fields.
- `coverUpdatedOn` is not part of `EditableMetadata`, so there's nothing to sync on the edit form side.

### Resolution display

Coil's `AsyncImage` exposes `onSuccess: (AsyncImagePainter.State.Success) -> Unit`. The `Success` state gives us `result.drawable.intrinsicWidth / intrinsicHeight`. We capture those into a `remember { mutableStateOf<IntSize?>(null) }` local to `CoverOverlay`, keyed by `coverUrl` so it resets when the overlay is reused for a different cover.

Rendering:
- A `Box` wraps the `AsyncImage`. The caption is a small `Text` inside the bottom-right of that Box.
- Typography: `MaterialTheme.typography.labelSmall` (or smaller if it still feels too loud).
- Readability: `color = Color.White`, sitting on a semi-transparent pill — `background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))` with small internal padding.
- Format: `"${width} × ${height}"` (e.g. `"1200 × 1800"`). Use the Unicode multiplication sign `×`, not an ASCII `x`.
- Hidden while loading, hidden on load error.

No resolution display on the row-level thumbnails (explicit UX call: the user asked for "when the image is opened").

### UX details

- **No confirmation dialog.** Tap → upload → toast. Grimmory keeps a `.bak` server-side; re-applying a different cover is trivial.
- **Button copy:** "Use this cover" on both placements, to match the mental model of picking from a list.
- **Icon for the row button:** `Icons.Filled.AddPhotoAlternate` or `Icons.Outlined.Image`, TBD during implementation — whatever reads best at 24dp. Pick one, don't iterate.
- **Row-button tap propagation:** the row currently has `clickable { onClick }` for selecting a candidate; the cover-apply button must consume its own click via `Modifier.clickable` on the button, not the row.
- **Overlay auto-close on success:** the overlay closes so the user can immediately see their library's new cover. On failure, the overlay stays open.
- **Per-button spinner:** while `applyingCoverUrl == coverUrl`, replace the button content with a small `CircularProgressIndicator` (~16.dp). Only one apply operation is allowed at a time; if another upload is in flight, disable all other "Use this cover" buttons.

## Verification

1. **Build:** `./gradlew :app:assembleDebug`
2. **Unit tests:** `./gradlew :app:testDebugUnitTest` (no new tests required for this change; existing `EditMetadataViewModel` behavior is preserved).
3. **Manual smoke test (happy path):**
   - Open a Grimmory book, tap "Edit metadata" from the book detail overflow.
   - Run a provider search, wait for results.
   - Tap a candidate thumbnail → overlay opens → caption shows e.g. `"1200 × 1800"`.
   - Tap "Use this cover" → snackbar shows "Cover applied" → overlay closes.
   - Navigate back to book detail → new cover is displayed (not the old cached one).
   - Navigate to library grid → new cover is displayed.
4. **Manual smoke test (from row button):**
   - Same flow, but tap "Use this cover" directly on the candidate row without opening the overlay. Snackbar shows "Cover applied".
   - Confirm that tapping the button does **not** select the candidate for metadata merge (no "Apply all fetched" form state changes).
5. **Manual smoke test (cache-buster pass):**
   - In Grimmory's web UI, upload a new cover for a different book.
   - Pull-to-refresh (or trigger) a library sync in Ember.
   - The new cover appears in Ember within one Coil load cycle. (Previously: stale until cache eviction.)
6. **Error path:** point Ember at a Grimmory server that returns 5xx for the cover-upload endpoint (or disconnect network right after tapping). Confirm the snackbar surfaces a friendly error and the button resets out of the spinner state.

## Out of scope

- Local-book cover apply. (Separate EPUB-rewrite effort.)
- DuckDuckGo-based separate cover search (Grimmory's `POST /metadata/covers` + `CoverSearchComponent` equivalent).
- "Compare current cover vs. candidate" side-by-side UI.
- Resolution display on the candidate row thumbnails.
- Audiobook cover apply (`/metadata/audiobook-cover/from-url`) — same plumbing would work but no UX call for it yet.
