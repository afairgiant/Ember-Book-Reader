# Hardcover Integration — Read-Only Book Tracking View

## Context

Hardcover is a book tracking platform (like Goodreads). Users track reading status, rate books, and set goals. The user wants to view their Hardcover library from within Ember. This is read-only — no status updates or modifications from the app. API is GraphQL with token-based auth.

## Scope

- Store API key in encrypted preferences
- Fetch user profile and reading lists
- Display in a tabbed screen accessible from Settings
- Read-only: no mutations

## API Details

- Endpoint: `POST https://api.hardcover.app/v1/graphql`
- Auth: `authorization: <token>` header (no "Bearer" prefix — strip if user pastes with it)
- Rate limit: 60 requests/minute
- Status IDs: 1=Want to Read, 2=Currently Reading, 3=Read, 4=Paused, 5=DNF

## Files to Create

### Core module
- `core/src/main/java/com/ember/reader/core/hardcover/HardcoverClient.kt` — GraphQL HTTP client
- `core/src/main/java/com/ember/reader/core/hardcover/HardcoverModels.kt` — data models
- `core/src/main/java/com/ember/reader/core/hardcover/HardcoverTokenManager.kt` — token storage

### App module
- `app/src/main/java/com/ember/reader/ui/hardcover/HardcoverScreen.kt` — tabbed list UI
- `app/src/main/java/com/ember/reader/ui/hardcover/HardcoverViewModel.kt` — state management

### Modify
- `SettingsHubScreen.kt` — add Hardcover nav row
- `EmberNavHost.kt` — add route and composable

## Data Models

```kotlin
data class HardcoverUser(
    val id: Int,
    val username: String,
    val name: String?,
    val booksCount: Int,
)

data class HardcoverBook(
    val id: Int,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val statusId: Int,
    val rating: Float?,
    val dateAdded: String?,
    val pages: Int?,
)
```

## Screen Layout

**Not connected:** API key input field + connect button + link to Hardcover settings page

**Connected:**
- Header: "Hardcover" title + username + book count + disconnect button
- Tabs: Currently Reading | Want to Read | Read | DNF
- Each tab: LazyColumn of book rows (cover, title, author, rating stars if rated)

## Settings Hub Row

Add under App Settings group:
```
📚 Hardcover
   View your reading lists
```

## Verification

1. Enter API key → validates with `me` query → shows username
2. Tabs show correct books for each status
3. Covers load from Hardcover CDN (no auth needed for public images)
4. Disconnect clears token
5. Settings row navigates to Hardcover screen
