# Tap-to-Zoom Book Cover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping the hero cover on the book detail screen opens a fullscreen overlay showing the cover at ~85% screen width; tapping anywhere or pressing Android back dismisses it.

**Architecture:** Pure Compose UI change in a single file. Local `coverZoomed` state in `BookDetailScreen`, a new private `FullscreenCoverOverlay` composable at the bottom of the same file, and a `BackHandler` to intercept the back button while the overlay is open. Auth plumbing mirrors the existing `BookCover` composable in the same file.

**Tech Stack:** Jetpack Compose, Material 3, Coil (`AsyncImage` + `ImageRequest.Builder`), `androidx.activity.compose.BackHandler`.

**Spec:** `docs/superpowers/specs/2026-04-11-book-cover-zoom-design.md`

**Testing note:** This is a UI-only change verified by manual smoke test on device. The project's Compose test infrastructure currently has pre-existing compile errors (`LibraryViewModelTest.kt`, `ReaderViewModelTest.kt`) unrelated to this feature, so adding automated tests here would require fixing unrelated test setup first — out of scope. Verification is `:app:assembleDebug` plus the manual smoke tests listed at the end of this plan.

---

### Task 1: Add state, `BackHandler`, clickable modifier, and `FullscreenCoverOverlay` composable

**Files:**
- Modify: `app/src/main/java/com/ember/reader/ui/book/BookDetailScreen.kt`

All changes are in this one file. Commit as a single unit since the state, clickable, `BackHandler`, and overlay composable are meaningless without each other.

- [ ] **Step 1: Verify required imports exist**

Open `app/src/main/java/com/ember/reader/ui/book/BookDetailScreen.kt`. Confirm these imports are present (all should already exist except possibly `BackHandler`):

```kotlin
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ember.reader.core.model.Book
import com.ember.reader.core.network.appendQueryParam
```

Add any that are missing (almost certainly only `androidx.activity.compose.BackHandler`).

- [ ] **Step 2: Add `coverZoomed` state in the detail content scope**

Find the block that contains the hero `BookCover` call (around line 200, inside the scrollable content where `currentBook` is in scope). Immediately before the `Box` that wraps the hero cover (currently at roughly line 202), add:

```kotlin
var coverZoomed by remember { mutableStateOf(false) }

BackHandler(enabled = coverZoomed) { coverZoomed = false }
```

The `BackHandler` only intercepts when the overlay is open, so normal back navigation still pops the detail screen when nothing is zoomed.

- [ ] **Step 3: Make the hero cover clickable when it has a real cover URL**

The hero cover is rendered at roughly line 208:

```kotlin
BookCover(
    book = currentBook,
    coverAuthHeader = coverAuthHeader,
    modifier = Modifier
        .width(180.dp)
        .aspectRatio(0.67f),
)
```

Change the `modifier` so it's clickable only when `currentBook.coverUrl != null` (no point opening an overlay on a placeholder):

```kotlin
BookCover(
    book = currentBook,
    coverAuthHeader = coverAuthHeader,
    modifier = Modifier
        .width(180.dp)
        .aspectRatio(0.67f)
        .then(
            if (currentBook.coverUrl != null) {
                Modifier.clickable { coverZoomed = true }
            } else {
                Modifier
            }
        ),
)
```

- [ ] **Step 4: Render the overlay when `coverZoomed == true`**

Immediately after the hero `Box` closing brace (around line 215, after the `Spacer(modifier = Modifier.height(16.dp))` is fine too — anywhere inside the same outer content column is OK, but it renders at the same level as the rest of the detail content):

Find the outermost Scaffold content block and add this just before the Scaffold's content closing brace — so the overlay sits above everything else in the detail content and below the Scaffold's snackbar host:

```kotlin
if (coverZoomed) {
    FullscreenCoverOverlay(
        book = currentBook,
        coverAuthHeader = coverAuthHeader,
        onDismiss = { coverZoomed = false },
    )
}
```

**Important:** place this inside the same Composable scope where `coverZoomed`, `currentBook`, and `coverAuthHeader` are all in scope. The existing hero cover block is that scope. Put the `if (coverZoomed)` block as the last child of the parent Column/Box that wraps the detail content, so it paints on top of everything in that container.

- [ ] **Step 5: Add the `FullscreenCoverOverlay` composable at the bottom of the file**

Scroll to the end of `BookDetailScreen.kt`, after the existing `BookCover` composable (around line 671). Add:

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
            val token = coverAuthHeader.removePrefix("jwt:")
            bookCoverUrl.appendQueryParam("token", token)
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

Notes on this composable:
- Mirrors the auth plumbing in `BookCover` (JWT → `?token=` via `appendQueryParam`, OPDS → `Authorization` header). Duplicating ~15 lines is cheaper than extracting a helper for just two call sites.
- The inner `AsyncImage` has no click handler, so taps on the image pass through to the outer `Box`'s `clickable` and dismiss. Intentional — any tap dismisses.
- `book.coverUrl ?: return` short-circuits if the book somehow has no URL. In practice step 3 prevents the overlay from opening without a cover URL, but this keeps the composable self-defensive.

- [ ] **Step 6: Build to verify it compiles**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Fix any compile errors before proceeding.

- [ ] **Step 7: Manual smoke test on device**

Install the debug APK and run through each scenario from the spec's Verification section:

1. **Grimmory remote cover:** Open a Grimmory book → tap hero cover → overlay appears with the cover at ~85% screen width → tap the image area → overlay dismisses. Repeat, but tap on the scrim (outside the image) → dismisses too.
2. **Local EPUB cover:** Open a book added from local storage (`file://` cover URL) → same flow. Confirm image loads.
3. **Downloaded Grimmory book:** Open a previously-downloaded Grimmory book → same flow. Confirm auth still works.
4. **Back button:** While the overlay is open, press Android back. Overlay closes, book detail screen stays visible. Press back again — detail screen pops as normal.
5. **Placeholder book:** Open a book with no `coverUrl` (first-two-letters placeholder). Tapping the placeholder does nothing, no crash.
6. **Snackbar interaction (bonus, if easy to trigger):** If a snackbar fires while the overlay is open (e.g. a sync completion), the snackbar should still appear on top and not be obscured by the scrim.

If any scenario fails, diagnose and fix before committing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/book/BookDetailScreen.kt docs/superpowers/specs/2026-04-11-book-cover-zoom-design.md docs/superpowers/plans/2026-04-11-book-cover-zoom.md
git commit -m "$(cat <<'EOF'
feat: tap-to-zoom book cover on detail screen

Tapping the hero cover on the book detail screen now opens a fullscreen
overlay showing the cover at ~85% screen width. Tap anywhere or press
back to dismiss. Placeholder covers (no coverUrl) are not zoomable.

No pinch or pan — keeping it simple. Can revisit if it turns out to be
needed.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-review

**Spec coverage:**
- Scope (book detail hero only) — Task 1 step 3 restricts clickable to the hero.
- Dismissal by tap anywhere — Task 1 step 5, outer `Box.clickable(onDismiss)` + no inner click handler.
- Dismissal by back button — Task 1 step 2, `BackHandler(enabled = coverZoomed)`.
- No zoom on placeholder — Task 1 step 3, conditional `Modifier.clickable`.
- Auth plumbing (JWT + Basic) for Grimmory/OPDS/downloaded/local — Task 1 step 5, mirrors `BookCover`.
- Overlay sits under snackbars — Task 1 step 4, placed inside Scaffold content, not outside it.
- No pinch/pan — not in plan. ✓

**Placeholder scan:** No TBD, TODO, "add error handling", "similar to". Each step has the actual code the engineer writes.

**Type consistency:** `FullscreenCoverOverlay(book, coverAuthHeader, onDismiss)` signature matches in step 4 call and step 5 definition. `coverZoomed` name is consistent across steps 2, 3, 4.

**Known risk — overlay placement:** Step 4 says "place this inside the same Composable scope where `coverZoomed`, `currentBook`, and `coverAuthHeader` are all in scope." The existing `BookDetailScreen` structure wraps the hero in a scrollable column; placing the overlay inside that scrollable column would scroll with the content, which is wrong for a fullscreen overlay. The implementer should place the `if (coverZoomed)` block at the top level of the Scaffold's content lambda — same level as the scrollable column, not inside it — so it renders over the entire screen without being affected by scroll state. If the current scope doesn't permit that, hoist `coverZoomed` up to that level and keep `currentBook`/`coverAuthHeader` accessible from there. This is the one implementation judgment call that the plan can't fully resolve without re-reading the exact Scaffold structure; do it carefully.
