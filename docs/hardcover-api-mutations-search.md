# Hardcover API Reference — Mutations & Search

## Mutations Overview

All mutations require authentication. Actions are performed as the authenticated user.

---

## User Book Mutations

### `insert_user_book` — Add Book to Library
```graphql
mutation {
    insert_user_book(object: {
        book_id: 123
        status_id: 2              # 1=Want to Read, 2=Reading, 3=Read, 4=Paused, 5=DNF, 6=Ignored
        rating: 4.5               # 0-5, supports halves
        edition_id: 456           # optional: specific edition
        date_added: "2024-09-07"  # optional
        privacy_setting_id: 1     # optional: 1=Public, 2=Followers, 3=Private
        private_notes: "Great so far" # optional
    }) {
        id
        user_book {
            id
            status_id
            rating
            book {
                title
            }
        }
        error
    }
}
```

**UserBookCreateInput fields:**
| Field | Type | Description |
|-------|------|-------------|
| book_id | Int! | **Required.** Book to add |
| status_id | Int | Reading status (1-6) |
| rating | numeric | Rating (0-5, supports halves) |
| edition_id | Int | Specific edition |
| date_added | date | When added |
| first_started_reading_date | date | When first started |
| last_read_date | date | Most recent completion |
| read_count | Int | Times read |
| privacy_setting_id | Int | Privacy level |
| private_notes | String | Private notes |
| review_slate | jsonb | Review in Slate editor format |
| review_has_spoilers | Boolean | Review has spoilers |
| reviewed_at | date | Review date |
| sponsored_review | Boolean | Sponsored review |
| recommended_by | String | Who recommended |
| recommended_for | String | Recommended for whom |
| referrer_user_id | Int | Referring user |
| media_url | String | Media URL |
| url | String | External URL |
| user_date | date | User-specified date |

### `update_user_book` — Update Library Entry
```graphql
mutation {
    update_user_book(
        id: 789
        object: {
            status_id: 3
            rating: 5.0
            last_read_date: "2024-09-15"
        }
    ) {
        id
        user_book {
            id
            status_id
            rating
        }
        error
    }
}
```

**UserBookUpdateInput fields:** Same as UserBookCreateInput but without `book_id` (all fields optional).

### `delete_user_book` — Remove from Library
```graphql
mutation {
    delete_user_book(id: 789) {
        id
        book_id
        user_id
        user_book {
            id
        }
    }
}
```

**Returns UserBookDeleteType:**
| Field | Type |
|-------|------|
| id | Int |
| book_id | Int |
| user_id | Int |
| user_book | user_books |

---

## User Book Reads Mutations

Track individual read-throughs (start/finish dates, progress).

### `insert_user_book_read` — Start a Read-Through
```graphql
mutation {
    insert_user_book_read(
        user_book_id: 789
        user_book_read: {
            started_at: "2024-09-01"
            edition_id: 456
            progress_pages: 50
        }
    ) {
        id
        user_book_read {
            id
            started_at
            progress_pages
        }
        error
    }
}
```

### `update_user_book_read` — Update Read Progress
```graphql
mutation {
    update_user_book_read(
        id: 101
        object: {
            progress_pages: 150
            finished_at: "2024-09-15"
        }
    ) {
        id
        user_book_read {
            id
            progress_pages
            finished_at
        }
        error
    }
}
```

### `upsert_user_book_reads` — Bulk Upsert Reads
```graphql
mutation {
    upsert_user_book_reads(
        user_book_id: 789
        datesRead: [
            {
                started_at: "2024-09-01"
                finished_at: "2024-09-15"
                edition_id: 456
            }
        ]
    ) {
        user_book_id
        user_book {
            id
            read_count
        }
        error
    }
}
```

### `delete_user_book_read` — Delete Read-Through
```graphql
mutation {
    delete_user_book_read(id: 101) {
        id
        error
    }
}
```

**DatesReadInput fields:**
| Field | Type | Description |
|-------|------|-------------|
| id | Int | Read ID (for updates) |
| started_at | date | Start date |
| finished_at | date | Finish date |
| edition_id | Int | Edition read |
| progress_pages | Int | Pages read |
| progress_seconds | Int | Seconds listened (audiobooks) |
| action | String | Action type |

---

## Reading Journal Mutations

### `insert_reading_journal` — Create Journal Entry
```graphql
mutation {
    insert_reading_journal(object: {
        book_id: 123
        event: "note"
        entry: "Really enjoying chapter 5"
        action_at: "2024-09-10"
        privacy_setting_id: 1
        edition_id: 456
        tags: []
    }) {
        id
        reading_journal {
            id
            event
            entry
        }
        errors
    }
}
```

**ReadingJournalCreateType fields:**
| Field | Type | Description |
|-------|------|-------------|
| book_id | Int! | **Required.** Associated book |
| event | String! | **Required.** Event type (note, quote, status_currently_reading, status_read, rated, reviewed, progress_updated) |
| privacy_setting_id | Int! | **Required.** Privacy level |
| tags | [BasicTag]! | **Required.** Tags (can be empty array) |
| entry | String | Entry text |
| action_at | date | When event occurred |
| edition_id | Int | Specific edition |
| metadata | jsonb | Additional data |

### `update_reading_journal` — Update Journal Entry
```graphql
mutation {
    update_reading_journal(
        id: 555
        object: {
            entry: "Updated note text"
            event: "note"
        }
    ) {
        id
        reading_journal {
            id
            entry
        }
        errors
    }
}
```

**ReadingJournalUpdateType fields:** Same as create but all fields optional (no book_id).

### `delete_reading_journal` — Delete Journal Entry
```graphql
mutation {
    delete_reading_journal(id: 555) {
        id
    }
}
```

### `delete_reading_journals_for_book` — Delete All Journal Entries for a Book
```graphql
mutation {
    delete_reading_journals_for_book(book_id: 123) {
        ids
    }
}
```

---

## Goal Mutations

### `insert_goal` — Create Reading Goal
```graphql
mutation {
    insert_goal(object: {
        description: "Read 50 books in 2024"
        metric: "books"
        goal: 50
        start_date: "2024-01-01"
        end_date: "2024-12-31"
        conditions: {}
        privacy_setting_id: 1
    }) {
        id
        goal {
            id
            metric
            goal
            progress
        }
        errors
    }
}
```

**GoalInput fields:**
| Field | Type | Description |
|-------|------|-------------|
| description | String! | Goal description |
| metric | String! | Measurement (books, pages) |
| goal | Int! | Target number |
| start_date | date! | Start date |
| end_date | date! | End date |
| conditions | GoalConditionInput! | Filters (can be empty `{}`) |
| archived | Boolean | Is archived |
| privacy_setting_id | Int | Privacy level |

**GoalConditionInput fields:**
| Field | Type | Description |
|-------|------|-------------|
| authorBipoc | Int | Filter by BIPOC authors |
| authorGenderIds | [Int] | Filter by author gender |
| authorLgbtqia | Int | Filter by LGBTQIA+ authors |
| bookCategoryIds | [Int] | Filter by book category |
| readingFormatId | Int | Filter by reading format |

### `update_goal` — Update Goal
```graphql
mutation {
    update_goal(id: 10, object: { goal: 60 }) {
        id
        goal { id, goal, progress }
        errors
    }
}
```

### `update_goal_progress` — Recalculate Goal Progress
```graphql
mutation {
    update_goal_progress(id: 10) {
        id
        goal { id, progress }
    }
}
```

### `delete_goal` — Delete Goal
```graphql
mutation {
    delete_goal(id: 10) {
        id
        errors
    }
}
```

---

## List Mutations

### `insert_list` — Create List
```graphql
mutation {
    insert_list(object: {
        name: "My Favorite Sci-Fi Books"
        description: "Best science fiction novels"
        privacy_setting_id: 1
    }) {
        id
        list {
            id
            name
        }
        errors
    }
}
```

### `update_list` — Update List
```graphql
mutation {
    update_list(id: 123, object: {
        name: "Updated Name"
        description: "Updated description"
    }) {
        id
        list { id, name }
        errors
    }
}
```

### `delete_list` — Delete List
```graphql
mutation {
    delete_list(id: 123) {
        success
    }
}
```

### `insert_list_book` — Add Book to List
```graphql
mutation {
    insert_list_book(object: {
        list_id: 123
        book_id: 456
        position: 1
    }) {
        id
        list_book {
            id
            book { title }
            list { name }
        }
    }
}
```

### `delete_list_book` — Remove Book from List
```graphql
mutation {
    delete_list_book(id: 789) {
        id
        list_id
        list { name, books_count }
    }
}
```

### `upsert_followed_list` — Follow a List
```graphql
mutation {
    upsert_followed_list(list_id: 3) {
        id
        followed_list { id, list_id }
    }
}
```

### `delete_followed_list` — Unfollow a List
```graphql
mutation {
    delete_followed_list(list_id: 3) {
        success
    }
}
```

---

## Other Useful Mutations

### `upsert_like` — Like/Unlike Content
```graphql
mutation {
    upsert_like(likeable_id: 123, likeable_type: "UserBook") {
        # LikeType response
    }
}
```

### `delete_like` — Remove Like
```graphql
mutation {
    delete_like(likeable_id: 123, likeable_type: "UserBook") {
        # LikeDeleteType response
    }
}
```

### `insert_followed_user` — Follow a User
```graphql
mutation {
    insert_followed_user(user_id: 42) {
        id
        followed_user_id
        user_id
    }
}
```

---

## Search API

The search uses Typesense (same as the website). It's a query, not a mutation.

### Search Query Signature
```graphql
search(
    query: String!        # Search term (required)
    query_type: String    # Content type (default: "book")
    per_page: Int         # Results per page (default: 25)
    page: Int             # Page number (default: 1)
    sort: String          # Sort attribute
    fields: String        # Searchable attributes (comma-separated)
    weights: String       # Field weights (comma-separated)
): SearchOutput
```

### SearchOutput Type
| Field | Type | Description |
|-------|------|-------------|
| ids | [Int] | Array of result IDs in order |
| results | jsonb | Full Typesense result objects |
| query | String | Echo of search query |
| query_type | String | Echo of query type |
| page | Int | Current page |
| per_page | Int | Results per page |
| error | String | Error message if any |

### Searchable Types
- `book` (default)
- `author`
- `series`
- `character`
- `list`
- `prompt`
- `publisher`
- `user`

### Search for Books
```graphql
query SearchBooks {
    search(
        query: "lord of the rings"
        query_type: "Book"
        per_page: 5
        page: 1
    ) {
        ids
        results
    }
}
```

**Book search defaults:**
- fields: `title,isbns,series_names,author_names,alternative_titles`
- sort: `_text_match:desc,users_count:desc`
- weights: `5,5,3,1,1`
- typos: `5,0,5,5,5`

**Book result fields include:** title, subtitle, description, author_names, series_names, isbns, slug, pages, rating, ratings_count, users_count, release_year, cover_color, genres, moods, content_warnings, has_audiobook, has_ebook, audio_seconds, contributions, featured_series, featured_series_position, alternative_titles

### Search for Authors
```graphql
query SearchAuthors {
    search(
        query: "rowling"
        query_type: "Author"
        per_page: 5
    ) {
        ids
        results
    }
}
```

**Author search defaults:**
- fields: `name,name_personal,alternate_names,series_names,books`
- sort: `_text_match:desc,books_count:desc`
- weights: `3,3,3,2,1`
- typos: `5`

### Search for Series
```graphql
query SearchSeries {
    search(
        query: "harry potter"
        query_type: "Series"
        per_page: 7
    ) {
        ids
        results
    }
}
```

**Series search defaults:**
- fields: `name,books,author_name`
- sort: `_text_match:desc,readers_count:desc`
- weights: `2,1,1`
- typos: `5`

### Important Notes
- `fields`, `weights`, and `typos` work together — if you specify 2 fields, provide 2 weights and 2 typos
- Single modifier values (weights, typos) apply equally to all fields
- Search returns `ids` array which can be used to query full objects via their respective schema queries
- The `results` field contains full Typesense response objects with all searchable fields

### Typical Search + Detail Flow
1. Search for a book: `search(query: "...", query_type: "Book") { ids }`
2. Get full details: `books(where: {id: {_in: [id1, id2, ...]}}) { title, ... }`
