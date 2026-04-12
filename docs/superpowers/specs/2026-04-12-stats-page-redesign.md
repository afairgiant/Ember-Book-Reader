# Stats Page Redesign

## Context

The stats page currently feels flat and unengaging. Two main issues:
1. **Presentation**: Every card uses the same `surfaceVariant` background with identical visual weight — nothing stands out
2. **Structure**: Local stats appear first, Grimmory stats are bolted on below a divider — feels like two separate pages, not one dashboard

The goal is to transform it into a clean, data-dense, dashboard-like experience (Apple Health / Grafana vibe) where Grimmory data is prioritized when connected, local data fills a supporting role, and two new visualizations (session scatter, reading timeline) are added.

## Design

### Information Hierarchy (top to bottom)

**1. Hero Summary Strip**
- When Grimmory connected: large Grimmory streak number + longest streak + total reading days (3 cards, `primaryContainer`)
- When local-only: local streak + all-time duration (2 cards, `primaryContainer`)
- This replaces the current separate local streak row AND Grimmory streak row

**2. Activity Section — "Reading Activity"**
- When Grimmory connected: 52-week GitHub-style heatmap (`surfaceVariant` card)
- When local-only: 12-week calendar (existing, same card style)
- NEW: Session scatter plot (Grimmory only, below heatmap)
  - Canvas-drawn dot plot inside a Card
  - X-axis: hour of day (0–24), labeled at 0, 6, 12, 18, 24
  - Y-axis: duration in minutes, auto-scaled
  - Each dot = one session, color-coded by day of week using primary/secondary/tertiary palette
  - Fixed height ~200dp
  - Title: "Session Patterns" with a small legend row

**3. Time Period Cards**
- Today / This Week / This Month (always local, device-specific)
- Use `surface` background (lighter than `surfaceVariant`) — these are supporting metrics
- Estimated completion card below if applicable

**4. Reading Patterns (Grimmory only)**
- Peak hours horizontal bar chart (bars in `tertiary` color)
- Favorite days horizontal bar chart (bars in `tertiary` color)
- Both in a single section under "Reading Patterns" header

**5. Reading Timeline (NEW, Grimmory only)**
- Section header: "Reading Timeline" with left/right week-navigation arrows and "Week N, YYYY" label
- Horizontal `LazyRow` of book cards showing:
  - Book title (bold, single line, ellipsis)
  - Date range (e.g., "Mar 2 – Mar 8")
  - Session count + total time
- Cards use `secondaryContainer` background
- Week state managed locally in the composable; week changes trigger `viewModel.loadTimeline(year, week)`
- Default: current week

**6. Library & Genres (Grimmory only)**
- Library overview: Read / Reading / Unread as a segmented horizontal bar (one bar, three colored segments) instead of 3 separate cards
- Top genres as horizontal bar chart (existing, `secondary` color bars)

**7. Recent Sessions**
- Moved to bottom as supporting detail
- Default shows 5, "Show all" expands to 15
- Uses `surface` background (lightest weight)

**8. Footer (Grimmory only)**
- "View full stats on Grimmory" button
- Opens `{serverUrl}/reading-stats` in system browser via Intent

### Visual Hierarchy via Color

| Section | Container Color | Visual Weight |
|---------|----------------|---------------|
| Hero summary | `primaryContainer` | Highest |
| Activity (heatmap/scatter) | `surfaceVariant` | High |
| Time periods | `surface` | Low (supporting) |
| Patterns (peak/days) | `surfaceVariant` cards, `tertiary` bars | Medium |
| Timeline | `secondaryContainer` | Medium |
| Library/genres | `surfaceVariant` | Medium-low |
| Recent sessions | `surface` | Lowest (detail) |

### Local-Only Degradation

When Grimmory is not connected, sections 4–6 and 8 simply don't render. The hero falls back to local streak data. The result is: hero → 12-week calendar → time periods → estimated completion → recent sessions. Clean and complete on its own.

## File Structure

Move stats screen from `ui/settings/StatsScreen.kt` to a new `ui/settings/stats/` package:

```
ui/settings/stats/
├── StatsScreen.kt              — Thin orchestrator (~100 lines): Scaffold + LazyColumn calling sections
├── StatsViewModel.kt           — Moved from parent, gains timeline + scatter + serverUrl fields
├── HeroSummarySection.kt       — Top streak/highlights strip with Grimmory/local branching
├── ActivitySection.kt          — Heatmap calendars (both variants) + session scatter Canvas
├── TimePeriodSection.kt        — Today/week/month cards + estimated completion
├── PatternsSection.kt          — Peak hours + favorite days bar charts
├── TimelineSection.kt          — NEW: week-picker + horizontal book cards
├── LibrarySection.kt           — Status segmented bar + genre bars
├── RecentSessionsSection.kt    — Session list with expand/collapse
└── StatComponents.kt           — Shared: StatMiniCard, TimeCard, SectionHeader, HorizontalBarChart, formatDuration
```

### ViewModel Changes

Add to `StatsData`:
```kotlin
val timeline: List<GrimmoryTimelineEntry>? = null,
val sessionScatter: List<GrimmorySessionScatter>? = null,
val grimmoryServerUrl: String? = null,
```

Add to `loadGrimmoryStats` (parallel with existing async calls):
```kotlin
val timelineDeferred = async {
    val week = java.time.LocalDate.now().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
    grimmoryClient.getReadingTimeline(baseUrl, serverId, currentYear, week).getOrNull()
}
val scatterDeferred = async {
    grimmoryClient.getSessionScatter(baseUrl, serverId, currentYear).getOrNull()
}
```

Add `loadTimeline(year: Int, week: Int)` method for week navigation:
```kotlin
fun loadTimeline(year: Int, week: Int) {
    viewModelScope.launch {
        val server = findGrimmoryServer() ?: return@launch
        val result = grimmoryClient.getReadingTimeline(server.url, server.id, year, week).getOrNull()
        _stats.value = _stats.value.copy(timeline = result)
    }
}
```

Store `grimmoryServerUrl = server.url` when loading Grimmory stats.

### Navigation Update

Update `EmberNavHost.kt` to import from new package path. The route `Routes.STATS` stays the same.

## Critical Files

- `app/src/main/java/com/ember/reader/ui/settings/StatsScreen.kt` — rewrite and move to `stats/` package
- `app/src/main/java/com/ember/reader/ui/settings/StatsViewModel.kt` — move to `stats/`, add new fields + methods
- `app/src/main/java/com/ember/reader/ui/navigation/EmberNavHost.kt` — update import path
- `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryClient.kt` — already has timeline + scatter APIs
- `core/src/main/java/com/ember/reader/core/grimmory/GrimmoryModels.kt` — already has models
- `app/src/main/res/values/strings.xml` — new string resources for section headers, timeline labels

## Verification

1. Build the app and navigate to Settings → Reading Statistics
2. **With Grimmory connected**: Verify hero shows Grimmory streak data, 52-week heatmap renders, scatter plot draws correctly, timeline loads current week with navigation working, patterns and library sections display, footer link opens browser to `/reading-stats`
3. **Without Grimmory**: Verify hero shows local streak, 12-week calendar renders, time periods show, Grimmory-only sections are absent, no errors
4. **Empty state**: Verify "no sessions yet" message when no reading data exists
5. **Visual check**: Confirm different sections have distinct color treatments and the page has clear visual hierarchy
