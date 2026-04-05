# Hardcover API Reference — Schemas & Queries

## Core Schemas Overview

The most relevant schemas for Ember integration:

| Schema | Purpose |
|--------|---------|
| `me` | Authenticated user (shortcut to current user) |
| `users` | User profiles |
| `user_books` | User's library entries (status, rating, review, dates) |
| `user_book_reads` | Individual read-through records with progress |
| `books` | Book records (title, pages, description, cover) |
| `editions` | Specific published versions (ISBN, format, publisher) |
| `authors` | Author records |
| `contributions` | Author-to-book/edition relationships with roles |
| `series` | Book series |
| `book_series` | Book's position within a series |
| `reading_journals` | Reading activity log (notes, quotes, status changes) |
| `goals` | Reading goals |
| `lists` | User-created book collections |
| `tags` | Genre/mood/content-warning tags |
| `images` | Cover images and profile photos |

---

## `me` — Current User

Returns the authenticated user's data.

```graphql
query {
    me {
        id
        username
        name
        books_count
        followers_count
        followed_users_count
        pro
    }
}
```

---

## `users` — User Profiles

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| username | citext | Username |
| name | String | Display name |
| bio | String | Biography |
| books_count | Int! | Books on shelves |
| followers_count | Int! | Follower count |
| followed_users_count | Int! | Following count |
| pro | Boolean! | Has pro subscription |
| flair | String | Profile badge (e.g. 'Supporter') |
| cached_image | jsonb! | Cached profile image |
| location | String | Location |
| image | images | Profile image |

### Query by ID
```graphql
query {
    users(where: {id: {_eq: 1}}, limit: 1) {
        id
        username
    }
}
```

### Query by Username
```graphql
query {
    users(where: {username: {_eq: "adam"}}, limit: 1) {
        id
        username
    }
}
```

---

## `user_books` — Library Entries

The central schema for tracking a user's relationship with books. Each entry tracks reading status, rating, review, ownership, and reading history.

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| user_id | Int! | Owner |
| book_id | Int! | Associated book |
| book | books! | The associated book object |
| edition_id | Int | Specific edition being read |
| edition | editions | The specific edition object |
| status_id | Int! | Reading status (1-6, see Overview doc) |
| rating | numeric | User's rating (0-5, supports halves) |
| review | String | Review text |
| review_html | String | HTML formatted review |
| has_review | Boolean! | Has a written review |
| review_has_spoilers | Boolean! | Review contains spoilers |
| date_added | date! | When added to library |
| first_started_reading_date | date | When first started reading |
| first_read_date | date | First completion date |
| last_read_date | date | Most recent completion date |
| read_count | Int! | Number of times read |
| owned | Boolean! | User owns a copy |
| owned_copies | Int | Number of copies owned |
| starred | Boolean! | User starred this book |
| private_notes | String | Private notes (only visible to owner) |
| privacy_setting_id | Int! | Privacy (1=Public, 2=Followers, 3=Private) |
| recommended_by | String | Who recommended this book |
| user_book_reads | [user_book_reads!]! | Individual read-through records |
| reading_journals | [reading_journals!]! | Reading journal entries |
| created_at | timestamptz! | Record creation time |
| updated_at | timestamptz | Last update time |

### Available Queries
| Query | Returns | Description |
|-------|---------|-------------|
| user_books | user_books[] | Array with filtering and pagination |
| user_books_aggregate | aggregate | Aggregated data (count, averages) |
| user_books_by_pk | user_books | Single entry by primary key (id) |

### Get User's Library
```graphql
query MyLibrary {
    user_books(
        where: {user_id: {_eq: USER_ID}}
        order_by: {date_added: desc}
        limit: 20
    ) {
        id
        status_id
        rating
        date_added
        book {
            title
            contributions {
                author {
                    name
                }
            }
            image {
                url
            }
        }
    }
}
```

### Get Currently Reading
```graphql
query CurrentlyReading {
    user_books(
        where: {
            user_id: {_eq: USER_ID}
            status_id: {_eq: 2}
        }
        order_by: {updated_at: desc}
    ) {
        id
        book {
            title
            pages
            image { url }
        }
        user_book_reads {
            progress_pages
        }
    }
}
```

### Get Books by Status
```graphql
query BooksByStatus {
    user_books(
        where: {user_id: {_eq: USER_ID}, status_id: {_eq: 2}}
    ) {
        book {
            title
            image { url }
            contributions {
                author { name }
            }
        }
    }
}
```

### Get Reading Statistics
```graphql
query ReadingStats {
    user_books_aggregate(
        where: {user_id: {_eq: USER_ID}}
    ) {
        aggregate {
            count
            avg { rating }
        }
    }
}
```

### Get User's Reviews
```graphql
query UserReviews {
    user_books(
        where: {
            user_id: {_eq: USER_ID}
            has_review: {_eq: true}
        }
        order_by: {reviewed_at: desc}
        limit: 10
    ) {
        id
        rating
        review_html
        review_has_spoilers
        reviewed_at
        book {
            title
            image { url }
        }
    }
}
```

### Paginated Library
```graphql
query PaginatedLibrary {
    user_books(
        where: {user_id: {_eq: USER_ID}}
        distinct_on: book_id
        limit: 20
        offset: 0
    ) {
        book {
            title
            pages
            release_date
        }
    }
}
```

---

## `user_book_reads` — Read-Through Records

Tracks individual reading sessions/attempts for a user_book entry.

### Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| user_book_id | Int! | Parent user_book |
| edition_id | Int | Edition being read |
| started_at | date | When reading started |
| finished_at | date | When reading finished |
| paused_at | date | When reading was paused |
| progress | float8 | Reading progress (0-1 fraction) |
| progress_pages | Int | Pages read |
| progress_seconds | Int | Seconds listened (audiobooks) |

---

## `books` — Book Records

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| title | String | Primary title |
| subtitle | String | Subtitle |
| description | String | Book summary |
| slug | String! | URL-friendly identifier |
| pages | Int | Page count |
| release_date | date | Original publication date |
| release_year | Int | Publication year |
| rating | numeric | Average rating |
| ratings_count | Int! | Number of ratings |
| ratings_distribution | jsonb! | Rating breakdown |
| users_count | Int! | Users with this book |
| reviews_count | Int! | Number of reviews |
| book_category_id | Int | Category (novel, poetry, etc.) |
| literary_type_id | Int | Fiction/nonfiction |
| canonical_id | Int | Canonical book if duplicate |
| image | images | Cover image |
| image_id | Int | Cover image ID |
| contributions | [contributions!]! | Authors/contributors |
| editions | [editions!]! | Published editions |
| book_series | [book_series!]! | Series relationships |
| user_books | [user_books!]! | User library entries |
| cached_image | jsonb | Cached cover image data |
| cached_contributors | json | Cached contributor data |
| cached_tags | json | Cached tags |

### Book Category IDs
Books have categories including novels, poetry, fan fiction, etc. (10 types).

### Get Books by Author
```graphql
query GetBooksByAuthor {
    authors(where: {name: {_eq: "Dan Wells"}}) {
        books_count
        name
        contributions(where: {contributable_type: {_eq: "Book"}}) {
            book {
                title
            }
        }
    }
}
```

---

## `editions` — Published Versions

A specific published version of a book (different ISBN, publisher, format, cover art).

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| book_id | Int! | Parent book |
| title | String | Title (may differ from book) |
| subtitle | String | Subtitle |
| isbn_13 | String | ISBN-13 |
| isbn_10 | String | ISBN-10 |
| asin | String | Amazon ID (ASIN) |
| pages | Int | Page count |
| audio_seconds | Int | Audiobook duration in seconds |
| edition_format | String | Format (hardcover, paperback, ebook, audiobook) |
| physical_format | String | Physical format details |
| reading_format_id | Int! | Reading format (1=Physical, 2=Audio, 3=Both, 4=Ebook) |
| release_date | date | Publication date |
| release_year | Int | Publication year |
| publisher | publishers | Publisher |
| publisher_id | Int | Publisher ID |
| language | languages | Language |
| language_id | Int | Language ID |
| country | countries | Country of publication |
| image | images | Cover image |
| image_id | Int | Cover image ID |
| rating | numeric | Average rating |
| users_count | Int! | Users with this edition |
| state | String! | Record state (pending, linking, linked, normalized, error, duplicate) |
| contributions | [contributions!]! | Edition-specific contributors |

### Get Edition by ISBN
```graphql
query GetEditionByISBN {
    editions(where: {isbn_13: {_eq: "9780547928227"}}) {
        id
        title
        isbn_13
        isbn_10
        asin
        pages
        release_date
        physical_format
        publisher { name }
        book {
            id
            title
            rating
            contributions {
                author { name }
            }
        }
        language { language }
        reading_format { format }
    }
}
```

### Get All Editions of a Book
```graphql
query GetBookEditions {
    editions(
        where: {book_id: {_eq: 328491}}
        order_by: {release_date: desc}
    ) {
        id
        title
        isbn_13
        pages
        release_date
        physical_format
        publisher { name }
        users_count
        rating
    }
}
```

### Get Editions by Title
```graphql
query GetEditionsFromTitle {
    editions(where: {title: {_eq: "Oathbringer"}}) {
        id
        title
        edition_format
        pages
        release_date
        isbn_10
        isbn_13
        publisher { name }
    }
}
```

---

## `authors` — Author Records

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| name | String! | Display name |
| name_personal | String | Personal/first name |
| bio | String | Biography |
| slug | String | URL-friendly identifier |
| books_count | Int! | Number of books |
| image | images | Author photo |
| born_date | date | Birth date |
| death_date | date | Death date |
| alternate_names | jsonb! | Alternate names or pen names |
| contributions | [contributions!]! | Book and edition contributions |
| identifiers | jsonb! | External IDs (VIAF, ISNI, etc.) |
| location | String | Location or country of origin |
| canonical_id | Int | Canonical author if duplicate |

---

## `contributions` — Author-Book Relationships

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| author | authors | The contributing author |
| author_id | Int! | Author ID |
| contributable_id | Int | Book or Edition ID |
| contributable_type | String | "Book" or "Edition" |
| contribution | String | Role: author, illustrator, translator, editor, narrator, foreword, afterword, cover_artist |

---

## `series` — Book Series

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| name | String! | Series name |
| slug | String! | URL-friendly identifier |
| author | authors | Primary author |
| books_count | Int! | Number of books |
| primary_books_count | Int | Main books (excluding companions) |
| description | String | Description |
| is_completed | Boolean | Series is complete |
| book_series | [book_series!]! | Books in this series |

### Get Books in Series (deduplicated)
```graphql
query GetBooksBySeries {
    series(
        where: {
            name: {_eq: "Harry Potter"},
            books_count: {_gt: 0},
            canonical_id: {_is_null: true}
        }
    ) {
        id
        name
        author { name }
        books_count
        book_series(
            distinct_on: position
            order_by: [{position: asc}, {book: {users_count: desc}}]
            where: {
                book: {canonical_id: {_is_null: true}},
                compilation: {_eq: false}
            }
        ) {
            position
            book {
                id
                slug
                title
            }
        }
    }
}
```

**Deduplication tips:**
- Filter `canonical_id: {_is_null: true}` to hide merged books
- Use `distinct_on: position` with `order_by: [{position: asc}, {book: {users_count: desc}}]` for one book per position
- Filter `compilation: {_eq: false}` to exclude compilations

---

## `book_series` — Book-Series Relationships

### Fields
| Field | Type | Description |
|-------|------|-------------|
| id | bigint! | Unique identifier |
| book_id | Int! | Book ID |
| series_id | Int! | Series ID |
| position | float8 | Numeric position in series |
| details | String | Text form of position (e.g. "1-3" for compilations) |
| compilation | Boolean! | Is a compilation |
| featured | Boolean! | Series featured on the book |

---

## `reading_journals` — Reading Activity Log

Tracks reading events including notes, quotes, status changes, and progress updates.

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | bigint! | Unique identifier |
| user_id | Int | Entry owner |
| book_id | Int | Associated book |
| edition_id | Int | Specific edition |
| event | String | Event type (see below) |
| entry | String | Entry text |
| action_at | timestamptz! | When the reading event occurred |
| metadata | jsonb! | Additional data |
| privacy_setting_id | Int! | Privacy (1=Public, 2=Followers, 3=Private) |
| likes_count | Int | Engagement metric |

### Event Types
- `note` — User note
- `quote` — Book quote
- `status_currently_reading` — Started reading
- `status_read` — Finished reading
- `rated` — Rated the book
- `reviewed` — Wrote a review
- `progress_updated` — Updated reading progress

---

## `goals` — Reading Goals

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| user_id | Int | Owner |
| metric | String | Measurement type (books, pages) |
| goal | Int | Target number |
| progress | numeric | Current progress |
| start_date | date | Start date |
| end_date | date | End date |
| completed_at | timestamptz | When completed |
| state | String | State (active, completed, failed) |
| archived | Boolean | Is archived |
| conditions | jsonb | Filters (author gender, format, etc.) |
| description | String | Description |
| privacy_setting_id | Int! | Privacy (1=Public, 2=Followers, 3=Private) |

---

## `lists` — Book Collections

### Key Fields
| Field | Type | Description |
|-------|------|-------------|
| id | Int! | Unique identifier |
| user_id | Int! | Owner |
| name | String! | List name |
| description | String | Description |
| books_count | Int! | Number of books |
| public | Boolean! | Publicly visible |
| ranked | Boolean! | Books are ranked/ordered |
| slug | String | URL-friendly identifier |
| privacy_setting_id | Int! | Privacy (1=Public, 2=Followers, 3=Private) |
| likes_count | Int! | Likes |
| followers_count | Int | Followers |
| list_books | [list_books!]! | Books in this list |

### list_books Fields
| Field | Type | Description |
|-------|------|-------------|
| id | int | Unique identifier |
| list_id | int | List ID |
| book_id | int | Book ID |
| edition_id | int | Optional edition ID |
| position | int | Order position |
| date_added | timestamptz | When added |

---

## `images` — Images

### Fields
| Field | Type | Description |
|-------|------|-------------|
| id | bigint! | Unique identifier |
| url | String | Image URL |
| width | Int | Width in pixels |
| height | Int | Height in pixels |
| ratio | float8 | Aspect ratio |
| color | String | Dominant color |
| colors | jsonb | Extracted colors |
| imageable_id | Int | Associated record ID |
| imageable_type | String | Record type (Book, Edition, Author, User) |

---

## `tags` — Tags

### Fields
| Field | Type | Description |
|-------|------|-------------|
| id | bigint! | Unique identifier |
| tag | String! | Tag name |
| slug | String! | URL-friendly identifier |
| tag_category_id | Int! | Category (genre, mood, etc.) |
| count | Int! | Items with this tag |
