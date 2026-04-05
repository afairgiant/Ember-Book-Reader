# Settings Redesign & Navigation Restructure

## Context

The app currently has 4 bottom nav tabs: Home | Library | Profile | Settings. The Profile screen is a grab-bag of connected servers, reading stats, and hidden dev log access. The Settings screen has appearance, sync, and download toggles. This redesign merges them into a single, expandable Settings hub and adds a dedicated Browse tab.

## Navigation Changes

### Bottom Nav (before → after)

```
Home | Library | Profile | Settings
                ↓
Home | Browse | Library | Settings
```

### Route Changes

- **Remove:** `PROFILE` route
- **Add:** `BROWSE` route (server picker → catalog)
- **Keep:** `HOME`, `LOCAL_LIBRARY`, `APP_SETTINGS`, `STATS`, `STORAGE`, `DEV_LOG`
- **Add:** `SETTINGS_APPEARANCE`, `SETTINGS_SYNC`, `SETTINGS_DOWNLOADS` (sub-pages)

## Home Screen (simplified)

Remove Connected Servers section, Add Server button, and Reading Statistics button. Keep:

1. **Continue Reading** — horizontal book row (existing)
2. **Recently Added** — horizontal book row from Grimmory (existing)
3. **Quick Stats** — small summary card: today's reading time + current streak

## Browse Tab (new)

A server list screen showing all connected servers. Tapping a server navigates to its catalog (existing `CatalogScreen`). Reuses the server card design from the current home screen but without edit/delete actions (those live in Settings now).

## Settings Screen (redesigned)

A navigation hub — no inline toggles. Every row has an icon, title, subtitle, and chevron that navigates to a sub-page.

### Layout

```
┌─────────────────────────────────┐
│ [Logo]  Settings                │
│         Customize your reading  │
│         [Grimmory connected]    │
└─────────────────────────────────┘

┌─ Connected Servers ─────────────┐
│ ☁ Grimmory Server        ›     │
│   ● OPDS ● Kosync ● Grimmory   │
│ + Add Server                    │
└─────────────────────────────────┘

┌─ App Settings ──────────────────┐
│ 🎨 Appearance              ›   │
│    Theme, keep screen on        │
│ 🔄 Sync                    ›   │
│    Frequency, highlights, ...   │
│ 📥 Downloads & Storage     ›   │
│    Auto download, cleanup, ...  │
│ 📊 Reading Statistics      ›   │
│    History, streaks, stats      │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ ℹ About Ember                   │
│   v1.0.0 · Long press: dev log │
└─────────────────────────────────┘
```

### Header Card

- Ember app logo (from mipmap foreground)
- "Settings" title
- "Customize your reading experience" subtitle
- "Grimmory connected" badge (green, shown only when logged in to a Grimmory server)

### Connected Servers Section

- Each server shows: icon, name, status dots (OPDS/Kosync/Grimmory), chevron → navigates to edit server form
- "+ Add Server" row at bottom → navigates to server form

### Sub-Pages

**Appearance** (`SETTINGS_APPEARANCE`):
- Theme selector (Light / Dark / System chips)
- Keep Screen On toggle

**Sync** (`SETTINGS_SYNC`):
- Sync Frequency dropdown
- Sync Notifications toggle
- Sync Highlights toggle
- Sync Bookmarks toggle
- Sync Now button

**Downloads & Storage** (`SETTINGS_DOWNLOADS`):
- Auto Download Reading toggle
- Auto Cleanup toggle
- Manage Downloads → navigates to existing Storage screen

**Reading Statistics** — navigates to existing `STATS` route (no new screen needed)

### About Row

- Shows "About Ember" with version string
- Long-press navigates to existing `DEV_LOG` route
- No chevron (not a navigation item, just info + hidden gesture)

## Files to Create/Modify

### Create
- `app/.../ui/settings/SettingsHubScreen.kt` — new navigation hub (replaces SettingsScreen.kt for the tab)
- `app/.../ui/settings/AppearanceSettingsScreen.kt` — theme + keep screen on
- `app/.../ui/settings/SyncSettingsScreen.kt` — sync options (extracted from AppSettingsScreen)
- `app/.../ui/settings/DownloadSettingsScreen.kt` — download/storage options (extracted from AppSettingsScreen)
- `app/.../ui/browse/BrowseScreen.kt` — server list for browsing

### Modify
- `EmberNavHost.kt` — update bottom nav tabs, add new routes, wire sub-pages
- `ServerListScreen.kt` — simplify to Continue Reading + Recently Added + Quick Stats (remove server cards)

### Remove (or deprecate)
- `SettingsScreen.kt` — replaced by SettingsHubScreen
- `AppSettingsScreen.kt` — split into Appearance/Sync/Download sub-pages
- `SettingsViewModel.kt` — functionality moves to new ViewModels or is reused

## Reusable Components

Create a `SettingsRow` composable for the hub rows:
```kotlin
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
)
```

Icon in a tinted circle + title/subtitle + chevron. Used for every row in the hub. Makes adding new settings sections trivial.

## Verification

1. All 4 bottom nav tabs work: Home, Browse, Library, Settings
2. Settings hub shows all sections with correct navigation
3. Each sub-page shows correct controls and persists changes
4. Connected servers show with status dots, edit navigates to server form
5. Add Server navigates to server form
6. Reading Statistics navigates to existing stats screen
7. Dev log accessible via long-press on About row
8. Home screen shows Continue Reading + Recently Added + Quick Stats
9. Browse tab shows server list, tapping opens catalog
