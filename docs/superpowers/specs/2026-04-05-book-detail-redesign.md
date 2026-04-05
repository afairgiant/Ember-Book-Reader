# Book Detail Screen Redesign + Hardcover Enrichment

## Context

The current book detail screen is functional but basic — a side-by-side cover+info layout with stacked cards for description, read status, and metadata. It needs a visual upgrade and Hardcover data enrichment.

## Goals

1. **Visual facelift** — modern, polished book detail layout
2. **Hardcover enrichment** — show community rating, user rating/status from Hardcover when available
3. **Want to Read integration** — easy path from Hardcover's "Want to Read" list to downloading from Grimmory

## Current Layout (before)

```
[Back]
[Cover] Title
        Author
        Series #N
        [EPUB]
        ━━━━ 42%

[Read/Download buttons]
[Share]
[View on Grimmory]

┌ Read Status ────────────┐
│ [Unread] [Reading] [Read]│
└─────────────────────────┘

┌ Description ────────────┐
│ Book description text...│
└─────────────────────────┘

┌ Details ────────────────┐
│ Format: EPUB            │
│ Author: ...             │
│ Publisher: ...           │
│ Pages: 320              │
│ Published: 2024         │
│ ...15+ info rows        │
└─────────────────────────┘
```

## Proposed Layout (after)

```
[Cover — large, centered, with subtle backdrop blur]

Title
Author
Series · #N

★★★★☆ 4.2 (1,204 ratings)  ← Hardcover community rating
Your rating: ★★★★★           ← Hardcover user rating (if rated)

[📖 Read] [⬇ Download]       ← Primary actions
[Hardcover: Currently Reading] ← Hardcover status badge

┌ About ──────────────────┐
│ Description text...     │
│ Pages · Published · Lang│
└─────────────────────────┘

┌ Details ────────────────┐
│ Publisher, ISBN, Format  │
│ Series, Subjects         │
│ Server, Library, Shelves │
└─────────────────────────┘

┌ Grimmory ───────────────┐  ← Only if Grimmory server
│ Read Status chips        │
│ [View on Grimmory]       │
│ [Share Book File]        │
└─────────────────────────┘
```

### Key Design Changes

1. **Hero cover** — large centered cover at top (not side-by-side), similar to Hardcover/Audible book pages
2. **Community rating** — Hardcover average rating with star display + ratings count
3. **User's Hardcover status** — badge showing their Hardcover status for this book (if matched)
4. **Cleaner info hierarchy** — description + key metadata (pages/year/language) in one card, detailed metadata in a collapsible section
5. **Grouped actions** — read/download actions prominent, Grimmory-specific actions in their own section

## Hardcover Enrichment

### Matching Books

Three-tier matching strategy (best match wins):

1. **Direct ID match (best)** — Grimmory stores `hardcoverBookId` in its book metadata. If present, use it directly. Requires adding `hardcoverBookId` to Grimmory's `AppBookDetail` DTO (Grimmory PR needed — field exists in `BookMetadata` but isn't exposed via the app API yet). **Note:** The Grimmory PR may not land soon. Ember should add `hardcoverBookId: Long?` to `GrimmoryBookDetail` now — it will deserialize as null until Grimmory starts sending it. No feature flags or commented-out code needed; the null check naturally falls through to title search.

2. **Title+Author search (fallback)** — Search Hardcover API by title + author. Use `search()` query. Pick the best match from top 3 results.

3. **No match** — Show the enhanced layout without Hardcover data. No degradation.

Cache the resolved Hardcover book ID once matched (in the local Book entity or in-memory) to avoid re-searching on every detail view.

### Data to Display

From Hardcover's book detail:
- `rating` — community average rating (0-5)
- `ratings_count` — number of ratings
- `users_count` — number of users tracking this book
- `description` — can supplement/replace Grimmory's if richer

From user's Hardcover `user_books` entry (if they've tracked it):
- `status_id` — their reading status on Hardcover
- `rating` — their personal rating
- `date_added`, `first_read_date`, `last_read_date`

### API Queries Needed

**Search for book match:**
```graphql
query {
    search(query: "Book Title Author Name", limit: 3) {
        ... on Book {
            id
            title
            slug
            rating
            ratings_count
        }
    }
}
```

**Get user's entry for a book (if matched):**
```graphql
query {
    user_books(
        where: {user_id: {_eq: USER_ID}, book_id: {_eq: BOOK_ID}}
        limit: 1
    ) {
        status_id
        rating
        date_added
    }
}
```

## Want to Read → Grimmory Flow

Already partially built via the Hardcover screen's "Search in Grimmory" button. This redesign ensures:
- The Hardcover "Want to Read" list clearly shows which books exist on Grimmory (if any)
- Tapping a Want to Read book → detail sheet → "Search in Grimmory" → catalog search

Future enhancement: auto-match Want to Read books against Grimmory catalog in background.

## Files to Modify

- `app/.../ui/book/BookDetailScreen.kt` — full layout redesign
- `app/.../ui/book/BookDetailViewModel.kt` — add Hardcover data fetching
- `core/.../hardcover/HardcoverClient.kt` — add search + user_books queries

## Files to Create

None — enhancements to existing files.

## Verification

1. Book detail shows large centered cover with backdrop
2. Hardcover rating displays when book is matched (may not match all books)
3. User's Hardcover status/rating shown when they've tracked the book
4. Existing Grimmory features still work (read status, view on Grimmory, download)
5. Books without Hardcover matches show the enhanced layout without Hardcover data
6. Performance: Hardcover API calls don't block initial page load (show local data first, enrich async)
