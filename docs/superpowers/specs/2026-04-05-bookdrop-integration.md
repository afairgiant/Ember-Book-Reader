# Book Drop Integration — Upload & Organize Books from Mobile

## Context

Grimmory has a "Book Drop" feature where users upload book files, review/edit metadata, assign to a library, and finalize the import. Currently only accessible via the web UI. This feature brings it to the Ember mobile app.

## Grimmory API Endpoints

### Upload
- `POST /api/v1/files/upload/bookdrop` — multipart file upload to book drop folder
- `POST /api/v1/files/upload` — direct upload to a specific library + path
- Auth: JWT Bearer token, requires upload permission or admin

### Book Drop Management
- `GET /api/v1/bookdrop/notification` — pending file count summary
- `GET /api/v1/bookdrop/files?status=...` — paginated list of pending files
- `POST /api/v1/bookdrop/files/bulk-edit` — edit metadata on selected files
- `POST /api/v1/bookdrop/files/extract-pattern` — extract metadata from filenames
- `POST /api/v1/bookdrop/files/discard` — discard selected files
- `POST /api/v1/bookdrop/imports/finalize` — finalize import (assign library, move files)
- `POST /api/v1/bookdrop/rescan` — rescan book drop folder

### Key DTOs to investigate
- `BookdropFile` — file info, metadata fields, status
- `BookdropFinalizeRequest` — library assignment, selection
- `BookdropBulkEditRequest` — metadata changes
- `BookdropFileNotification` — summary counts

## Proposed Mobile Flow

### 1. Upload
- Android share intent: share an EPUB/PDF from file manager → Ember → uploads to book drop
- In-app upload button: pick file from device → upload
- Show upload progress

### 2. Review Pending Files
- List of files in book drop with status
- Tap a file to review/edit metadata
- Simplified mobile layout (no two-column comparison — single column with editable fields)
- Cover preview
- Library + subpath selector dropdowns

### 3. Actions
- Edit metadata (title, author, series, etc.)
- Assign library + subpath
- Finalize (import to library)
- Discard (remove from book drop)
- Rescan folder

## Mobile UI Concept

### Book Drop Screen (single screen, all inline)

All books in the book drop are shown in one scrollable list. Each book is an expandable card — collapsed by default showing just the essentials, expandable to show the full metadata comparison.

**Top bar:** "Book Drop" + upload button + rescan button

**Global controls (sticky top or bottom bar):**
- Library dropdown + Subpath dropdown (applies to selected books on finalize)
- Select All / Deselect All
- Finalize Selected (disabled unless library is selected AND at least one book is checked)
- Discard Selected

**Each book card (collapsed):**
```
☐  [cover]  Due South; or, Cuba Past and Present
             Maturin M. Ballou · EPUB
                                            ▾ expand
```
- Checkbox for selection (for bulk finalize/discard)
- Cover thumbnail, title, author, format
- Expand chevron to show metadata

**Each book card (expanded):**
```
☑  [cover]  Due South; or, Cuba Past and Present
             Maturin M. Ballou · EPUB
                                            ▴ collapse
─────────────────────────────────────────
Title
Due South; or, Cuba Past and Present
    ‹  Due south; or, Cuba past and present...

Subtitle
(empty)

Publisher
(empty)
    ‹  Facsimile Publisher

Published
2009-09-29
    ‹  2024-01-01

Authors
Maturin M. Ballou
    ‹  Maturin Murray Ballou

Language
en
    ‹  English

ISBN
(empty)
    ‹  1465548734

Description
(empty)
    ‹  Leather Binding on Spine and Corners...

[Library ▼]  [Subpath ▼]
[Save]  [Discard]
─────────────────────────────────────────
```

**Stacked metadata rows:**
- Top row: current/file metadata (left-aligned) — tappable to edit inline
- Bottom row: fetched metadata (right-aligned, accent color)
- Chevron `‹` on left of bottom row — tap to replace current with fetched value
- If values match, only show one row
- If no fetched value, only show current value

**Finalize rules:**
- Library must be selected (either per-book or global)
- At least one book must be checked
- Finalize button shows count: "Finalize (3)"

### Settings Integration
- Badge on Book Drop row in Settings showing pending count
- Or accessible from Browse tab

## Navigation
- Accessible from Settings hub (new nav row) or Browse tab
- Route: `Routes.BOOKDROP`

## Files to Create
- `core/.../grimmory/BookdropClient.kt` — API client
- `core/.../grimmory/BookdropModels.kt` — DTOs
- `app/.../ui/bookdrop/BookdropScreen.kt` — pending files list
- `app/.../ui/bookdrop/BookdropDetailScreen.kt` — edit metadata
- `app/.../ui/bookdrop/BookdropViewModel.kt` — state management

## Files to Modify
- `EmberNavHost.kt` — add routes
- `SettingsHubScreen.kt` or `BrowseScreen.kt` — add entry point
- `AndroidManifest.xml` — share intent filter for EPUB/PDF files

## Notes
- This is a large feature — implement in phases:
  1. Phase 1: Upload + view pending list
  2. Phase 2: Edit metadata + finalize
  3. Phase 3: Share intent integration
- Requires checking user permissions (canUpload or isAdmin) before showing the feature
