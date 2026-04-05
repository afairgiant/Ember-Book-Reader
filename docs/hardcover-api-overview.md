# Hardcover API Reference — Overview & Authentication

## What Is Hardcover?
Hardcover (hardcover.app) is a book tracking platform. Users can track reading progress, rate books, write reviews, create lists, set reading goals, and follow other readers. It exposes a GraphQL API powered by Hasura.

## API Endpoint
```
POST https://api.hardcover.app/v1/graphql
```

## Authentication
- Get an API token from account settings on hardcover.app (Settings > Hardcover API link)
- Pass it as an HTTP header:
  ```
  authorization: <token>
  ```
- **No "Bearer" prefix** in the actual API request — just the raw token value
- **Important:** The Hardcover website displays the token prefixed with "Bearer" when users copy it. Ember must strip the "Bearer " prefix if present when the user pastes their token.
- Tokens expire annually, reset on January 1st
- Tokens are user-scoped — all actions happen as that user
- **Never expose tokens client-side** — backend/server use only (localhost or approved APIs)

## Rate Limits & Constraints
| Constraint | Value |
|---|---|
| Rate limit | 60 requests/minute |
| Query timeout | 30 seconds |
| Max query depth | 3 levels |
| Token expiry | 1 year (resets Jan 1) |

## Data Access Rules
- Can access: own data, public data, followed users' data
- Cannot access: private data of non-followed users

## Disabled Query Operators
The following comparison operators are **disabled**:
- `_like`, `_nlike`
- `_ilike`
- `_regex`, `_nregex`, `_iregex`, `_niregex`
- `_similar`, `_nsimilar`

Use the `search()` query for text searching instead.

## Response Codes
| Code | Meaning |
|---|---|
| 200 | Success |
| 401 | Expired or invalid token (`{ error: "Unable to verify token" }`) |
| 403 | Insufficient access (`{ error: "Message describing the error" }`) |
| 404 | Not Found |
| 429 | Rate limited (`{ error: "Throttled" }`) |
| 500 | Server error (`{ error: "An unknown error occurred" }`) |

## Basic Query Example
```graphql
query {
    me {
        id
        username
    }
}
```

## Reading Status IDs
Used throughout the API for `status_id` fields:

| ID | Status |
|----|--------|
| 1 | Want to Read |
| 2 | Currently Reading |
| 3 | Read |
| 4 | Paused |
| 5 | Did Not Finish |
| 6 | Ignored |

## Privacy Setting IDs
Used throughout the API for `privacy_setting_id` fields:

| ID | Setting |
|----|---------|
| 1 | Public |
| 2 | Followers Only |
| 3 | Private |

## Reading Format IDs
Used in editions for `reading_format_id`:

| ID | Format |
|----|--------|
| 1 | Physical |
| 2 | Audio |
| 3 | Both |
| 4 | Ebook |
