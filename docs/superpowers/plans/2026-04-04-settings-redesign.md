# Settings Redesign & Navigation Restructure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge Profile and Settings into a single Settings navigation hub, add a Browse tab, and simplify the Home screen.

**Architecture:** Replace the 4-tab bottom nav (Home/Library/Profile/Settings) with Home/Browse/Library/Settings. The new Settings tab is a navigation hub where every row opens a sub-page. The existing `SettingsViewModel` is reused by all sub-pages. The Browse tab shows a server list that navigates to the existing catalog screens.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, existing SettingsViewModel

**Spec:** `docs/superpowers/specs/2026-04-04-settings-redesign.md`

---

### Task 1: Create shared SettingsRow composables

Extract and enhance the settings row components from `AppSettingsScreen.kt` into a shared file so the hub and sub-pages can both use them.

**Files:**
- Create: `app/src/main/java/com/ember/reader/ui/settings/components/SettingsComponents.kt`

- [ ] **Step 1: Create SettingsComponents.kt with reusable composables**

```kotlin
package com.ember.reader.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingsGroup(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsDetailRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/settings/components/SettingsComponents.kt
git commit -m "refactor: extract shared settings row composables"
```

---

### Task 2: Create Settings Hub screen

The main settings page — a navigation hub with no inline controls.

**Files:**
- Create: `app/src/main/java/com/ember/reader/ui/settings/SettingsHubScreen.kt`

- [ ] **Step 1: Create SettingsHubScreen.kt**

This screen shows:
- Header card with logo, title, Grimmory badge
- Connected Servers section with status dots per server, chevron to edit, and add button
- App Settings group with nav rows: Appearance, Sync, Downloads & Storage, Reading Statistics
- About row with dev log long-press

Use `SettingsViewModel` (existing) for server list and Grimmory login state. Use the shared `SettingsGroup` and `SettingsNavRow` from Task 1. Use Material icons:
- Appearance: `Icons.Default.Palette`
- Sync: `Icons.Default.Sync`
- Downloads: `Icons.Default.Download`
- Statistics: `Icons.Default.BarChart`
- Server: `Icons.Default.CloudQueue`
- About: `Icons.Default.Info`

Navigation callbacks: `onEditServer(Long)`, `onAddServer()`, `onOpenAppearance()`, `onOpenSync()`, `onOpenDownloads()`, `onOpenStats()`, `onOpenDevLog()`

For server status dots, reuse the pattern from the existing `SettingsScreen.kt` ServerStatusCard but simplified into the row format.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/settings/SettingsHubScreen.kt
git commit -m "feat: add SettingsHubScreen navigation hub"
```

---

### Task 3: Create Appearance, Sync, and Downloads sub-pages

Extract the controls from `AppSettingsScreen.kt` into three focused sub-pages.

**Files:**
- Create: `app/src/main/java/com/ember/reader/ui/settings/AppearanceSettingsScreen.kt`
- Create: `app/src/main/java/com/ember/reader/ui/settings/SyncSettingsScreen.kt`
- Create: `app/src/main/java/com/ember/reader/ui/settings/DownloadSettingsScreen.kt`

- [ ] **Step 1: Create AppearanceSettingsScreen.kt**

Scaffold with TopAppBar ("Appearance" + back arrow). Content:
- Theme selector (FilterChips for ThemeMode.entries) — copy from AppSettingsScreen lines 97-123
- Keep Screen On toggle

Uses `SettingsViewModel` via `hiltViewModel()`.

- [ ] **Step 2: Create SyncSettingsScreen.kt**

Scaffold with TopAppBar ("Sync" + back arrow). Content:
- Sync Frequency with dropdown — copy from AppSettingsScreen lines 162-184
- Sync Notifications toggle
- Sync Highlights toggle
- Sync Bookmarks toggle
- Sync Now button with spinning icon — copy from AppSettingsScreen lines 217-239

Uses `SettingsViewModel` via `hiltViewModel()`.

- [ ] **Step 3: Create DownloadSettingsScreen.kt**

Scaffold with TopAppBar ("Downloads & Storage" + back arrow). Content:
- Auto Download Reading toggle
- Auto Cleanup toggle
- Manage Downloads button → `onOpenStorage()` callback

Uses `SettingsViewModel` via `hiltViewModel()`.

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/settings/AppearanceSettingsScreen.kt \
      app/src/main/java/com/ember/reader/ui/settings/SyncSettingsScreen.kt \
      app/src/main/java/com/ember/reader/ui/settings/DownloadSettingsScreen.kt
git commit -m "feat: add Appearance, Sync, Downloads sub-pages"
```

---

### Task 4: Create Browse screen

Server list for browsing catalogs. Tapping a server navigates to its catalog.

**Files:**
- Create: `app/src/main/java/com/ember/reader/ui/browse/BrowseScreen.kt`
- Create: `app/src/main/java/com/ember/reader/ui/browse/BrowseViewModel.kt`

- [ ] **Step 1: Create BrowseViewModel.kt**

```kotlin
@HiltViewModel
class BrowseViewModel @Inject constructor(
    serverRepository: ServerRepository,
) : ViewModel() {
    val servers: StateFlow<List<Server>> = serverRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 2: Create BrowseScreen.kt**

Scaffold with TopAppBar ("Browse"). LazyColumn of server cards. Each card shows server name and a subtitle (e.g., "Grimmory" or "OPDS"). Tapping navigates via `onOpenLibrary(serverId)`. Show an empty state message when no servers are connected with a hint to add one in Settings.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/browse/BrowseScreen.kt \
      app/src/main/java/com/ember/reader/ui/browse/BrowseViewModel.kt
git commit -m "feat: add Browse screen with server list"
```

---

### Task 5: Simplify Home screen

Remove server cards, add server button, and reading statistics button from the home screen. Add quick stats card.

**Files:**
- Modify: `app/src/main/java/com/ember/reader/ui/server/ServerListScreen.kt`
- Modify: `app/src/main/java/com/ember/reader/ui/server/ServerListViewModel.kt`

- [ ] **Step 1: Add quick stats to ViewModel**

Add `readingStats` StateFlow to `ServerListViewModel` with today's reading time and current streak from `ReadingSessionRepository`:

```kotlin
private val _quickStats = MutableStateFlow<QuickStats?>(null)
val quickStats: StateFlow<QuickStats?> = _quickStats.asStateFlow()

// In init block:
viewModelScope.launch {
    val todaySeconds = readingSessionRepository.getTotalDurationToday()
    val streak = readingSessionRepository.getCurrentStreak()
    _quickStats.value = QuickStats(todaySeconds = todaySeconds, currentStreak = streak)
}

data class QuickStats(val todaySeconds: Long, val currentStreak: Int)
```

Inject `ReadingSessionRepository` into the constructor.

- [ ] **Step 2: Simplify ServerListScreen**

Remove from the LazyColumn:
- Connected Servers header + server cards section
- Add Server button
- Reading Statistics button

Remove unused callbacks: `onAddServer`, `onEditServer`, `onOpenStats`

Add after Recently Added:
- Quick stats card showing today's reading time and streak (small card with two values)

Remove the `ServerCard` composable (no longer needed here).

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ember/reader/ui/server/ServerListScreen.kt \
      app/src/main/java/com/ember/reader/ui/server/ServerListViewModel.kt
git commit -m "feat: simplify Home screen to reading focus with quick stats"
```

---

### Task 6: Wire up navigation

Update EmberNavHost to use the new screens and bottom nav tabs.

**Files:**
- Modify: `app/src/main/java/com/ember/reader/navigation/EmberNavHost.kt`

- [ ] **Step 1: Add new routes**

Add to the `Routes` object:
```kotlin
const val BROWSE = "browse"
const val SETTINGS_APPEARANCE = "settings/appearance"
const val SETTINGS_SYNC = "settings/sync"
const val SETTINGS_DOWNLOADS = "settings/downloads"
```

- [ ] **Step 2: Update BottomNavTab**

```kotlin
private enum class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Default.Home),
    BROWSE(Routes.BROWSE, "Browse", Icons.Default.Explore),
    LIBRARY(Routes.LOCAL_LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks),
    SETTINGS(Routes.APP_SETTINGS, "Settings", Icons.Default.Settings)
}
```

Update `bottomNavRoutes`:
```kotlin
private val bottomNavRoutes = setOf(Routes.HOME, Routes.BROWSE, Routes.LOCAL_LIBRARY, Routes.APP_SETTINGS)
```

- [ ] **Step 3: Replace PROFILE and APP_SETTINGS composable routes**

Remove the `PROFILE` composable route. Replace the `APP_SETTINGS` composable route with `SettingsHubScreen`:

```kotlin
composable(Routes.APP_SETTINGS) {
    SettingsHubScreen(
        onEditServer = { serverId -> navController.navigate(Routes.serverForm(serverId)) },
        onAddServer = { navController.navigate(Routes.serverForm()) },
        onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
        onOpenSync = { navController.navigate(Routes.SETTINGS_SYNC) },
        onOpenDownloads = { navController.navigate(Routes.SETTINGS_DOWNLOADS) },
        onOpenStats = { navController.navigate(Routes.STATS) },
        onOpenDevLog = { navController.navigate(Routes.DEV_LOG) },
    )
}
```

- [ ] **Step 4: Add new route composables**

Add Browse, Appearance, Sync, Downloads composable routes:

```kotlin
composable(Routes.BROWSE) {
    BrowseScreen(
        onOpenLibrary = { serverId -> navController.navigate(Routes.catalog(serverId)) },
    )
}

composable(Routes.SETTINGS_APPEARANCE) {
    AppearanceSettingsScreen(
        onNavigateBack = { navController.popBackStack() },
    )
}

composable(Routes.SETTINGS_SYNC) {
    SyncSettingsScreen(
        onNavigateBack = { navController.popBackStack() },
    )
}

composable(Routes.SETTINGS_DOWNLOADS) {
    DownloadSettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onOpenStorage = { navController.navigate(Routes.STORAGE) },
    )
}
```

- [ ] **Step 5: Simplify Home (ServerListScreen) callbacks**

Remove `onAddServer`, `onEditServer`, `onOpenStats` from the ServerListScreen call. Keep `onOpenLibrary`, `onOpenSettings`, `onOpenReader`, `onOpenBookDetail`.

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ember/reader/navigation/EmberNavHost.kt
git commit -m "feat: wire new navigation — Browse tab, Settings hub, sub-pages"
```

---

### Task 7: Clean up old files

Remove the now-unused Profile screen and old Settings screen.

**Files:**
- Delete: `app/src/main/java/com/ember/reader/ui/settings/SettingsScreen.kt` (replaced by SettingsHubScreen)
- Delete: `app/src/main/java/com/ember/reader/ui/settings/AppSettingsScreen.kt` (split into sub-pages)

- [ ] **Step 1: Delete old files**

Only delete after confirming no imports reference them. Search for `SettingsScreen` and `AppSettingsScreen` imports across the codebase — they should only be referenced from `EmberNavHost.kt` which was already updated.

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git rm app/src/main/java/com/ember/reader/ui/settings/SettingsScreen.kt \
      app/src/main/java/com/ember/reader/ui/settings/AppSettingsScreen.kt
git commit -m "chore: remove old Profile and Settings screens"
```

---

### Task 8: End-to-end verification

- [ ] **Step 1: Build and install**

Run: `./gradlew assembleDebug`
Install on device.

- [ ] **Step 2: Verify bottom nav**

Confirm 4 tabs: Home | Browse | Library | Settings. All tabs navigate correctly.

- [ ] **Step 3: Verify Home screen**

- Continue Reading row shows
- Recently Added row shows (if Grimmory connected)
- Quick stats card shows today's time and streak
- No server cards or add server button

- [ ] **Step 4: Verify Browse tab**

- Shows list of connected servers
- Tapping a server opens its catalog
- Empty state shows when no servers configured

- [ ] **Step 5: Verify Settings hub**

- Header card with logo and Grimmory badge
- Connected servers with status dots and chevrons
- Add Server navigates to server form
- Each App Settings row navigates to its sub-page
- About row shows version, long-press opens dev log

- [ ] **Step 6: Verify sub-pages**

- Appearance: theme chips work, keep screen on toggle works
- Sync: frequency dropdown, all toggles, sync now button
- Downloads: auto download, auto cleanup toggles, manage downloads button
- Reading Statistics: opens existing stats screen
- All sub-pages have back arrow that returns to hub
