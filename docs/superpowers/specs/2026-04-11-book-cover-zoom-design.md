# Tap-to-Zoom Book Cover on Detail Screen

## Context

Ember's book detail screen renders a 180dp hero cover at the top (`BookDetailScreen.kt:208`). Tapping it currently does nothing. Users want to see the cover at larger size — mostly to appreciate the artwork, or (now that provider-applied covers exist) to verify the cover looks right at higher resolution.

This spec adds tap-to-zoom: tap the hero cover → fullscreen overlay of the cover → tap anywhere or press back to dismiss. The feature is scoped to the book detail screen only. Zoom access from library grid / carousels / cards is explicitly out of scope because tap is already claimed by navigation there; those could get a separate long-press gesture later if wanted.

Pinch-to-zoom was considered and declined — a static fullscreen overlay at ~85% screen width already delivers ~4-5× visual enlargement over the 180dp thumbnail, which is the point of the feature. Interactive pinch/pan can be added later if it turns out to feel needed.

## Scope

- Book detail screen hero cover only.
- Every book that has a `coverUrl` (Grimmory remote, Grimmory downloaded, local EPUB, OPDS). Books with a placeholder (no `coverUrl`) are not zoomable — nothing meaningful to see at a larger size.
- No pinch, no pan, no animated zoom transition. Just tap → show → tap → hide.

## Design

### Components touched

- `app/src/main/java/com/ember/reader/ui/book/BookDetailScreen.kt`
  - Add local state `var coverZoomed by remember { mutableStateOf(false) }` near the top of the detail content.
  - Add a `Modifier.clickable { coverZoomed = true }` to the hero `BookCover` call at line 208, only when `currentBook.coverUrl != null`.
  - Add `BackHandler(enabled = coverZoomed) { coverZoomed = false }` so the Android back button dismisses the overlay before popping the screen.
  - Render `FullscreenCoverOverlay` on top when `coverZoomed == true`.
  - Add a new private composable `FullscreenCoverOverlay(book, coverAuthHeader, onDismiss)` at the bottom of the file.

### `FullscreenCoverOverlay` composable

```kotlin
@Composable
private fun FullscreenCoverOverlay(
    book: Book,
    coverAuthHeader: String?,
    onDismiss: () -> Unit,
) {
    val bookCoverUrl = book.coverUrl ?: return
    val context = LocalContext.current
    val imageModel = remember(bookCoverUrl, coverAuthHeader) {
        val url = if (coverAuthHeader?.startsWith("jwt:") == true) {
            bookCoverUrl.appendQueryParam("token", coverAuthHeader.removePrefix("jwt:"))
        } else {
            bookCoverUrl
        }
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (coverAuthHeader != null && !coverAuthHeader.startsWith("jwt:")) {
                    addHeader("Authorization", coverAuthHeader)
                }
            }
            .crossfade(true)
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = book.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}
```

The image auth plumbing (JWT token vs Basic header) mirrors the existing `BookCover` composable in the same file. No refactor to share — duplicating ~15 lines of `ImageRequest.Builder` setup is cheaper than introducing a helper that only two call sites would use, and the duplication stays local to one file.

### Dismissal behavior

- **Tap anywhere outside the image:** the outer `Box` has `clickable(onClick = onDismiss)`. Because the inner `AsyncImage` has no click handler, taps on the image area pass through to the outer Box and also dismiss. This is intentional — any tap dismisses, which is the simplest model.
- **Android back button:** `BackHandler` intercepts only when `coverZoomed == true`, so the first back tap closes the overlay and the second back tap (or a single back tap with no overlay) pops the detail screen as usual.
- **Swipe / drag:** not handled. Not in scope.

### Placement in the content hierarchy

The `FullscreenCoverOverlay` renders at the top level of the `Scaffold`'s content area — same nesting as any existing full-screen overlay pattern. It needs to sit above the book detail content so it visually covers everything, but below the `Scaffold`'s `snackbarHost` so any snackbars that fire while the overlay is open still appear on top (consistent with how `EditMetadataScreen` handles its `CoverOverlay`).

## Verification

1. **Build:** `./gradlew :app:assembleDebug`
2. **Manual smoke tests:**
   - **Happy path (Grimmory):** Open a Grimmory book → tap hero cover → overlay appears with the cover at ~85% screen width → tap anywhere → overlay dismisses.
   - **Happy path (local EPUB):** Repeat with a book added from local storage. Cover should load (from its `file://` URI) and zoom correctly.
   - **Happy path (downloaded Grimmory):** Repeat with a previously-downloaded Grimmory book. Auth header plumbing must still work.
   - **Back button:** While the overlay is open, press Android back. Overlay closes, book detail screen stays visible.
   - **Placeholder book:** Open a book with no `coverUrl` (first-two-letters placeholder). Tapping the placeholder does nothing (no overlay, no crash).
   - **Overlay + snackbar:** If possible, trigger a background snackbar (e.g. sync completion) while the overlay is open. Snackbar should appear on top and not be obscured.

## Out of scope

- Pinch-to-zoom / pan / fling.
- Animated shared-element transition from thumbnail to full-screen.
- Zoom from library grid / carousels / series rows (tap is claimed by navigation there).
- Double-tap to toggle zoom levels.
- Saving the zoomed cover to the device.
