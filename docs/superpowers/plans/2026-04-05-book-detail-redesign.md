# Book Detail Redesign + Hardcover Enrichment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the book detail screen with a hero cover layout, add Hardcover community/user data enrichment, and improve the Want to Read → Grimmory flow.

**Architecture:** The existing `BookDetailViewModel` gets a new `HardcoverClient` dependency to fetch book matches and user status. The screen layout is restructured with a centered hero cover, then info cards. Matching uses a two-tier strategy: direct Grimmory `hardcoverBookId` (null until Grimmory PR lands, falls through gracefully) then title search fallback.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, existing HardcoverClient + BookDetailViewModel

**Spec:** `docs/superpowers/specs/2026-04-05-book-detail-redesign.md`

---

### Task 1: Add Hardcover search and user_books queries to HardcoverClient

**Files:**
- Modify: `core/src/main/java/com/ember/reader/core/hardcover/HardcoverClient.kt`
- Modify: `core/src/main/java/com/ember/reader/core/hardcover/HardcoverModels.kt`
- Modify: `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryModels.kt`

- [ ] **Step 1: Add `HardcoverSearchResult` and `HardcoverUserBookEntry` models**

In `HardcoverModels.kt`, add:

```kotlin
data class HardcoverSearchResult(
    val bookId: Int,
    val title: String,
    val slug: String,
    val averageRating: Float?,
    val ratingsCount: Int,
    val coverUrl: String?,
    val authors: List<String>,
)

data class HardcoverUserBookEntry(
    val statusId: Int,
    val rating: Float?,
    val dateAdded: String?,
)
```

- [ ] **Step 2: Add `hardcoverBookId` to `GrimmoryBookDetail`**

In `GrimmoryModels.kt`, add to the `GrimmoryBookDetail` data class:

```kotlin
val hardcoverBookId: Long? = null,
```

This field will deserialize as null until the Grimmory PR lands. No other changes needed.

- [ ] **Step 3: Add `searchBooks` method to `HardcoverClient`**

```kotlin
suspend fun searchBooks(query: String, limit: Int = 3): Result<List<HardcoverSearchResult>> = runCatching {
    val json = query(
        """
        query {
            search(query: "${query.replace("\"", "\\\"")}", query_type: "Book", per_page: $limit) {
                results
            }
        }
        """.trimIndent()
    )
    val results = json.obj("data").obj("search")["results"]
    if (results == null || results is kotlinx.serialization.json.JsonNull) return@runCatching emptyList()

    val hits = results.jsonArray
        .filter { it.jsonObject.containsKey("document") }
        .map { it.jsonObject.obj("document") }

    hits.map { doc ->
        HardcoverSearchResult(
            bookId = doc.int("id"),
            title = doc.str("title"),
            slug = doc.str("slug"),
            averageRating = doc.floatOrNull("rating"),
            ratingsCount = doc.intOrDefault("ratings_count", 0),
            coverUrl = null, // Cover fetched separately via books query if needed
            authors = doc["author_names"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        )
    }
}
```

- [ ] **Step 4: Add `fetchUserBookEntry` method to `HardcoverClient`**

```kotlin
suspend fun fetchUserBookEntry(userId: Int, bookId: Int): Result<HardcoverUserBookEntry?> = runCatching {
    val json = query(
        """
        query {
            user_books(
                where: {user_id: {_eq: $userId}, book_id: {_eq: $bookId}}
                limit: 1
            ) {
                status_id
                rating
                date_added
            }
        }
        """.trimIndent()
    )
    val entries = json.obj("data").arr("user_books")
    if (entries.isEmpty()) return@runCatching null
    val entry = entries.first().jsonObject
    HardcoverUserBookEntry(
        statusId = entry.int("status_id"),
        rating = entry.floatOrNull("rating"),
        dateAdded = entry.strOrNull("date_added"),
    )
}
```

- [ ] **Step 5: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/ember/reader/core/hardcover/HardcoverClient.kt \
      core/src/main/java/com/ember/reader/core/hardcover/HardcoverModels.kt \
      core/src/main/java/com/ember/reader/core/grimmory/GrimmoryModels.kt
git commit -m "feat: add Hardcover search and user book entry queries"
```

---

### Task 2: Add Hardcover enrichment to BookDetailViewModel

**Files:**
- Modify: `app/src/main/java/com/ember/reader/ui/book/BookDetailViewModel.kt`

- [ ] **Step 1: Add Hardcover dependencies and state**

Inject `HardcoverClient` and `HardcoverTokenManager` into the constructor. Add state flows:

```kotlin
private val _hardcoverMatch = MutableStateFlow<HardcoverBookDetail?>(null)
val hardcoverMatch: StateFlow<HardcoverBookDetail?> = _hardcoverMatch.asStateFlow()

private val _hardcoverUserEntry = MutableStateFlow<HardcoverUserBookEntry?>(null)
val hardcoverUserEntry: StateFlow<HardcoverUserBookEntry?> = _hardcoverUserEntry.asStateFlow()
```

- [ ] **Step 2: Add `loadHardcoverData` method**

Called after book loads. Implements two-tier matching:

```kotlin
private fun loadHardcoverData(book: Book) {
    if (!hardcoverTokenManager.isConnected()) return

    viewModelScope.launch {
        // Tier 1: Direct ID from Grimmory metadata
        val directId = _grimmoryDetail.value?.hardcoverBookId?.toInt()
        val bookId = if (directId != null) {
            directId
        } else {
            // Tier 2: Search by title
            val query = buildString {
                append(book.title)
                book.author?.let { append(" $it") }
            }
            val results = hardcoverClient.searchBooks(query, limit = 1).getOrNull()
            results?.firstOrNull()?.bookId
        }

        if (bookId != null) {
            // Fetch full book detail
            hardcoverClient.fetchBookDetail(bookId).onSuccess { detail ->
                _hardcoverMatch.value = detail
            }
            // Fetch user's entry for this book
            hardcoverClient.fetchMe().onSuccess { user ->
                hardcoverClient.fetchUserBookEntry(user.id, bookId).onSuccess { entry ->
                    _hardcoverUserEntry.value = entry
                }
            }
        }
    }
}
```

- [ ] **Step 3: Call `loadHardcoverData` after book loads**

In the `init` block's book observer, after `book?.serverId?.let { loadServer(it) }`, add:

```kotlin
book?.let { loadHardcoverData(it) }
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/book/BookDetailViewModel.kt
git commit -m "feat: add Hardcover enrichment to BookDetailViewModel"
```

---

### Task 3: Redesign BookDetailScreen layout — hero cover + info structure

**Files:**
- Modify: `app/src/main/java/com/ember/reader/ui/book/BookDetailScreen.kt`

- [ ] **Step 1: Restructure the layout**

Replace the current side-by-side Row layout with:

1. **Hero cover** — centered, large (200dp wide), with the book's cover image
2. **Title block** — centered text: title, author, series
3. **Hardcover rating** — community stars + count (from `hardcoverMatch`)
4. **User Hardcover status** — badge showing their status (from `hardcoverUserEntry`)
5. **Action buttons** — Read/Download (existing logic, unchanged)
6. **About card** — description + key metadata (pages, year, language) in one card
7. **Details card** — all the detailed metadata rows (existing, reformatted)
8. **Grimmory card** — read status chips, View on Grimmory, Share (existing, grouped)

The screen composable collects the new states:
```kotlin
val hardcoverMatch by viewModel.hardcoverMatch.collectAsStateWithLifecycle()
val hardcoverUserEntry by viewModel.hardcoverUserEntry.collectAsStateWithLifecycle()
```

Key layout changes:
- Cover moves from side-by-side to centered hero at top (200dp width, 2:3 aspect ratio)
- Title/author/series become centered text below the cover
- Hardcover community rating (stars + count) below author
- User's Hardcover status as a small badge/chip
- Action buttons stay as horizontal row
- Description and key stats merged into "About" card
- Grimmory-specific actions grouped in their own card at bottom

- [ ] **Step 2: Add Hardcover rating display composable**

```kotlin
@Composable
private fun HardcoverRating(detail: HardcoverBookDetail) {
    val avg = detail.averageRating ?: return
    if (avg <= 0f) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RatingStars(rating = avg)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "%.1f".format(avg),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = " (${detail.ratingsCount})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RatingStars(rating: Float) {
    Row {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Default.Star
                rating >= i - 0.5f -> Icons.Default.StarHalf
                else -> Icons.Default.StarBorder
            }
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFFB300))
        }
    }
}
```

- [ ] **Step 3: Add Hardcover user status badge**

```kotlin
@Composable
private fun HardcoverStatusBadge(entry: HardcoverUserBookEntry) {
    val label = HardcoverStatus.label(entry.statusId)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Hardcover: $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/book/BookDetailScreen.kt
git commit -m "feat: redesign book detail with hero cover and Hardcover enrichment"
```

---

### Task 4: End-to-end verification

- [ ] **Step 1: Build and install**

Run: `./gradlew assembleDebug`
Install on device.

- [ ] **Step 2: Verify book detail layout**

- Open a book from the library
- Cover is large and centered at top
- Title, author, series displayed below cover
- Action buttons (Read/Download) work correctly
- Description and details cards render properly
- Grimmory section shows read status chips, View on Grimmory button

- [ ] **Step 3: Verify Hardcover enrichment**

- Open a book that exists on Hardcover (a popular title)
- Community rating (stars + count) appears below the author
- If you've tracked the book on Hardcover, your status badge appears
- Hardcover data loads async (doesn't block initial render)

- [ ] **Step 4: Verify books without Hardcover match**

- Open a local-only book or obscure title
- Page renders normally without Hardcover data (no errors, no blank spaces)
- All existing functionality works

- [ ] **Step 5: Verify Hardcover not connected**

- Disconnect Hardcover in Settings
- Open a book detail — no Hardcover data shown, no API errors
