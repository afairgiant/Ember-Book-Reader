# Code Quality Audit Report

**Project:** Ember Book Reader
**Date:** 2026-04-12
**Languages/Frameworks:** Kotlin, Jetpack Compose, Android (SDK 28-35)
**Build:** Gradle 8.13 + AGP 8.13.2 + Kotlin 2.1.0
**Codebase Size:** ~30,122 lines of Kotlin across 188 source files (82 app, 106 core) + 15 test files

## Executive Summary

Ember is a well-architected Android app that follows modern conventions (Compose, Hilt, Ktor, Coroutines/Flow, Room). The module structure is clean (:app for UI, :core for data), dependency management uses a version catalog, and CI/CD is in place. The project is in good shape for a solo-developer app in Phase 5 (Release Prep).

The biggest risks center on three areas: (1) **duplicated business logic** -- particularly the quadruplicated `withAuth` token refresh pattern across Grimmory clients and the 926-line `BookRepository` god class with internally duplicated upsert/format-detection logic; (2) **very low test coverage** at ~10% file-level, with zero tests for all sync managers, most API clients, and 13 of 17 ViewModels; (3) **silent error swallowing** via `getOrNull()` patterns that drop failures without user notification, particularly in stats and sync code.

Top priorities: extract shared auth logic, break up `BookRepository`, add tests to sync managers and API clients, and establish consistent error-handling patterns.

---

## Linting & Formatting Status

| Tool | Status | Notes |
|------|--------|-------|
| **ktlint** | Configured (v12.1.2, android_studio style) | Applied to all subprojects via root `build.gradle.kts`. 10 rules disabled in `.editorconfig` (wildcard imports, max-line-length, function naming, etc.) |
| **Android Lint** | Configured | Runs in CI (`lintDebug`). One rule disabled: `NullSafeMutableLiveData` |
| **Formatter** | ktlint (format mode) | Available via `./gradlew ktlintFormat` |
| **detekt** | Not configured | No static analysis beyond ktlint and Android Lint |
| **.editorconfig** | Present and comprehensive | UTF-8, LF, 4-space indent, trailing whitespace trim, language-specific overrides |
| **CI quality gates** | Weak | Both lint and unit tests run with `continue-on-error: true` -- failures don't block merges |
| **ktlint in CI** | Not run | CI runs `lintDebug` (Android Lint) but never `ktlintCheck` |

**Recommendations:**
- Remove `continue-on-error: true` from lint and test CI steps so failures block PRs
- Add `ktlintCheck` step to CI pipeline
- Consider adding detekt for deeper static analysis (complexity metrics, code smell detection)

---

## Top Issues by Priority

### Critical (address soon)

1. **Quadruplicated `withAuth` token refresh logic** -- `GrimmoryClient.kt:475-497`, `GrimmoryAppClient.kt:297-323`, `BookdropClient.kt:100-126`, `MetadataClient.kt:173-198`. Three of four inline the refresh HTTP call. All detect 401 via fragile `e.message?.contains("401")` string matching.

2. **`BookRepository.kt` is a 926-line god class** with 20+ methods handling 7+ distinct responsibilities (downloading, metadata extraction, cover extraction, catalog refresh, reconciliation, relinking, orphan recovery). Contains internally duplicated format-detection (3x) and upsert logic (2x).

3. **Test coverage at ~10%** -- 15 test files covering ~20 of 188 source files. Zero tests for all sync managers (`BookmarkSyncManager`, `HighlightSyncManager`, `ProgressSyncManager`), most API clients, and 13/17 ViewModels.

4. **`runBlocking` on main thread** in `ReaderViewModel.onCleared():421` -- blocks the main thread for Room I/O during ViewModel destruction.

5. **`HighlightSyncManager` and `BookmarkSyncManager` are structural clones** sharing ~80% identical sync logic. Only the entity type differs. Both duplicate a `parseTimestamp` helper.

### Important (next iteration)

6. **Silent error swallowing via `getOrNull()`** -- 48 uses in main source. `StatsViewModel.kt:113-132` silently drops 7 parallel API failures. Sync code silently drops progress/bookmark/highlight pull failures.

7. **Monolithic composables** -- `EditMetadataScreen.kt` (1004 lines), `BookDetailScreen.kt` (880 lines), `EpubReaderScreen.kt` (732 lines) are too large. Core logic and UI sections should be extracted into sub-composables.

8. **`ReaderViewModel` is a god class with 16 injected dependencies** (`ReaderViewModel.kt:49-66`). Manages preferences, sync, bookmarks, highlights, sessions, progress, and dictionary lookup.

9. **Inconsistent error handling within individual classes** -- `CatalogViewModel.kt` uses both try/catch (Grimmory paths) and `Result.fold` (OPDS paths) for the same category of operation.

10. **`GrimmoryAppClient.getBooks` has 15 parameters** (`GrimmoryAppClient.kt:22-38`). `BookRepository.refreshFromGrimmory` has 14 parameters (`BookRepository.kt:160-176`). Both need filter/request data classes.

11. **Download-to-file streaming logic duplicated 3x** across `GrimmoryClient.kt:130-157`, `GrimmoryClient.kt:435-466`, and `OpdsClient.kt:124-139`.

12. **Inline `LoadingScreen`/`ErrorScreen`** reimplementations in `LibraryScreen.kt:201-203`, `HardcoverScreen.kt:128,296` instead of using shared components from `SharedComponents.kt`.

13. **CI quality gates are non-blocking** -- `continue-on-error: true` on lint and test steps means failures are silently ignored.

### Nice to Have

14. **Hardcoded UI strings not using string resources** across 10+ files (navigation labels, dialog titles, button text). Would need to be addressed for i18n.

15. **Magic numbers for slider ranges** in `ReaderPreferencesSheet.kt` (font sizes `12f..32f`, line heights `1.0f..2.5f`, margins, zone sizes).

16. **128 uses of `RoundedCornerShape(N.dp)`** with inconsistent values. Could benefit from theme-level shape tokens.

17. **22 repeated `runCatching { Enum.valueOf() }.getOrNull()` patterns** across preference repositories. A `toEnumOrNull()` extension would clean this up.

18. **Hardcoded delay values** without named constants: `delay(2000)` in `ServerListViewModel.kt:89`, `delay(500)` in `NavigatorContainer.kt:81` and `LibraryViewModel.kt:405`.

19. **Repeated `navController.popBackStack()` lambdas** (~12 times in `EmberNavHost.kt`). Could be a `val`.

20. **70+ inline API path strings** scattered across Grimmory clients. Only `GRIMMORY_OPDS_PATH` is defined as a constant.

---

## Detailed Findings

### DRY Violations

#### `withAuth` Token Refresh (4x duplication) -- HIGH
- `core/.../grimmory/GrimmoryClient.kt:475-497` -- uses `refreshToken()` helper
- `core/.../grimmory/GrimmoryAppClient.kt:297-323` -- inlines refresh HTTP call
- `core/.../grimmory/BookdropClient.kt:100-126` -- inlines refresh HTTP call
- `core/.../grimmory/MetadataClient.kt:173-198` -- inlines refresh HTTP call

All four detect 401 by string-matching `e.message?.contains("401")` which is fragile and could match false positives. Should be extracted to a shared `AuthenticatedGrimmoryClient` or extension function that checks `response.status == HttpStatusCode.Unauthorized`.

#### `BookRepository` Internal Duplication -- HIGH
- **Format detection** (`when (appBook.primaryFileType?.uppercase()) { "PDF"/"AUDIOBOOK"/else -> EPUB }`) at lines 212-215, 353-357, 723-725
- **Cover URL selection** (audiobook vs ebook branching) at lines 218-222, 358-362, 721
- **Grimmory book upsert** logic in `refreshFromGrimmory` (208-252) vs `upsertGrimmoryBook` (346-389)
- **Download URL** `"/api/v1/opds/${id}/download"` at lines 230, 244, 369, 382, 722

#### `HighlightSyncManager` / `BookmarkSyncManager` Structural Clones -- HIGH
- `core/.../sync/HighlightSyncManager.kt:21-169` and `core/.../sync/BookmarkSyncManager.kt:20-130` follow identical patterns: fetch remote, build maps, process server items, process local items, handle 409, cleanup tombstones.
- `parseTimestamp` is duplicated identically in both files (`HighlightSyncManager.kt:172-175`, `BookmarkSyncManager.kt:132-135`).
- Both use fragile `it.message?.contains("409")` for conflict detection.

#### Bookmark Matching Logic (2x duplication) -- HIGH
- `ReaderViewModel.kt:243-252` and `EpubReaderScreen.kt:204-215` contain the same "same chapter + within 0.02 progression tolerance" logic with magic number `0.02`.

#### AudiobookViewModel Progress Calculation (2x duplication) -- HIGH
- `AudiobookViewModel.kt:388-406` (`saveProgress`) and `AudiobookViewModel.kt:457-472` (`saveProgressSnapshot`) contain near-identical 20-line percentage calculation blocks and JSON construction.

#### Auth Header Injection -- MEDIUM
- `OpdsClient.kt` repeats Basic Auth header setup 5 times (lines 38-39, 58-59, 79-80, 99-100, 118)
- `KosyncClient.kt` repeats kosync auth headers 3 times (lines 29-31, 45-47, 64-66)

#### Other Duplication -- MEDIUM
- Cover extraction bitmap logic duplicated in `BookRepository.kt:852-866` and `ServerRepository.kt:136-150`
- Search submit handler duplicated in `CatalogScreen.kt:170-175` and `CatalogScreen.kt:195-200`
- Catalog entry navigation routing duplicated in `CatalogScreen.kt:202-210` and `CatalogScreen.kt:286-304`
- `TrackListSheet`/`ChapterListSheet` structurally identical in `AudiobookPlayerScreen.kt:392-494`
- `BookGrid`/`BookList` share identical signatures and load-more logic in `LibraryScreen.kt:311-366,469-522`

### Complexity

#### God Classes

| File | Lines | Issue |
|------|-------|-------|
| `BookRepository.kt` | 926 | 7+ responsibilities, 14-param methods |
| `EpubReaderScreen.kt` | 732 | Single composable function ~520 lines |
| `EditMetadataScreen.kt` | 1004 | Largest file in project |
| `BookDetailScreen.kt` | 880 | Main composable ~610 lines |
| `LibraryControls.kt` | 942 | Second largest file |
| `ServerFormScreen.kt` | 726 | Large form composable |
| `ReaderViewModel.kt` | 526 | 16 injected dependencies |

#### Excessive Parameters

| File | Lines | Count |
|------|-------|-------|
| `ReaderViewModel.kt` | 49-66 | 16 constructor params |
| `GrimmoryAppClient.getBooks` | 22-38 | 15 params |
| `BookRepository.refreshFromGrimmory` | 160-176 | 14 params |
| `BookDetailViewModel.kt` | 39-51 | 11 constructor params |
| `ServerRepository.kt` | 32-43 | 10 constructor params |
| `BookDetailScreen` composable | 90-99 | 8 params |

#### Deep Nesting
- `HighlightSyncManager.syncHighlightsForBook` (~148 lines, 5 nesting levels) -- `HighlightSyncManager.kt:21-169`
- `EpubReaderScreen` selection action handler (5+ nesting levels) -- `EpubReaderScreen.kt:268-317`
- `BookRepository.findRelinkMatches` (~90 lines, 3-4 nesting levels) -- `BookRepository.kt:656-744`
- `LibraryViewModel.uiState` combine (5 flows with nested destructuring) -- `LibraryViewModel.kt:126-169`

### Error Handling

#### Silent Error Swallowing via `getOrNull()` -- MEDIUM-HIGH
48 uses of `.getOrNull()` in main source. Worst offenders:

| File | Lines | Impact |
|------|-------|--------|
| `StatsViewModel.kt` | 113-132 | 7 parallel API calls silently drop failures; stats page shows partial data with no error indication |
| `ServerListViewModel.kt` | 110, 114 | Reading streak and continue-reading data silently null |
| `ProgressSyncManager.kt` | 74, 112, 136 | Kosync/Grimmory progress pulls silently null |
| `BookRepository.kt` | 700, 837 | Book search and publication opening failures silently null |

#### Logged-But-Not-Propagated Errors -- MEDIUM

| File | Lines | Pattern |
|------|-------|---------|
| `StatsViewModel.kt` | 155-157 | All Grimmory stats failures caught, logged, no user notification |
| `SyncWorker.kt` | 113 | Per-server sync failure logged, continues silently |
| `LibraryViewModel.kt` | 222, 289 | Grimmory filter/refresh failures logged, not surfaced to UI |
| `BookRepository.kt` | 863-865 | Cover extraction failure returns null silently |

#### Fragile HTTP Status Detection -- MEDIUM
- `e.message?.contains("401")` in all 4 `withAuth` implementations
- `it.message?.contains("409")` in `HighlightSyncManager.kt:146` and `BookmarkSyncManager.kt:106`

#### Coroutine Safety Issues
- `runBlocking(Dispatchers.IO)` in `ReaderViewModel.onCleared():421` -- blocks main thread
- `GlobalScope.launch` in `ReaderViewModel.kt:436-442` and `AudiobookViewModel.kt:436-438` -- bypasses structured concurrency

### Dead Code / Unused References
- `ServerRepository.testGrimmoryConnection:165-174` -- calls `grimmoryClient.login` and captures tokens but never stores them
- `OpdsParser.kt:17-19` -- namespace constants `NS_ATOM`, `NS_OPDS`, `NS_DC` declared but never used (parser ignores namespaces)
- Duplicate imports in `EpubReaderScreen.kt:9/14` (`padding`), `EpubReaderScreen.kt:11/22` (`dp`)

### Naming & Consistency

#### Inconsistent Sealed State Naming
- "Success" states: `Success` (Library, Bookdrop, EditMetadata), `Ready` (Reader, Audiobook, OrganizeFiles), `Connected` (Hardcover), `OpdsSuccess`/`GrimmorySuccess` (Catalog)

#### Mixed Error Handling Approaches
Within `CatalogViewModel.kt` alone: try/catch for Grimmory errors, `Result.fold` for OPDS errors.

#### Fully-Qualified Names Used Inline
`ReaderViewModel.kt`, `EpubReaderScreen.kt`, `BookRepository.kt` use fully-qualified class names (`java.time.Instant`, `kotlinx.coroutines.runBlocking`, `kotlinx.coroutines.GlobalScope`, `kotlinx.coroutines.Dispatchers.IO`) instead of imports.

---

## Testing Assessment

### Overview

| Metric | Value |
|--------|-------|
| Test files | 15 |
| Source files | 188 |
| File-level coverage | ~10.6% |
| Framework | JUnit 5 + MockK + Coroutines-test |
| Test runner | JUnit Platform (configured in both modules) |
| CI integration | `testDebugUnitTest` (with `continue-on-error: true`) |

### What's Tested Well
- **OPDS parsing** (`OpdsParserTest`) -- realistic XML fixtures, multiple entry types
- **Kosync protocol** (`KosyncClientTest`) -- Ktor MockEngine tests against real serialization
- **File naming patterns** (`FileNamingPatternResolverTest`) -- 25+ cases including edge cases
- **Crypto utilities** (`Md5Test`, `PartialMd5Test`, `ConvertersTest`) -- unit-level correctness
- **Server/Book/Progress repositories** -- basic CRUD and state transitions

### Critical Coverage Gaps

**Sync Managers (zero tests, high-risk):**
- `BookmarkSyncManager.kt` -- bidirectional bookmark sync with conflict resolution
- `HighlightSyncManager.kt` -- bidirectional highlight sync
- `ProgressSyncManager.kt` -- progress conflict resolution

**API Clients (mostly untested):**
- `GrimmoryClient.kt` (498 lines) -- auth, highlights, bookmarks, stats
- `OpdsClient.kt` -- HTTP-level OPDS fetching
- `BookdropClient.kt` -- file upload
- `MetadataClient.kt` -- metadata search/update
- `HardcoverClient.kt` -- external service integration
- `GrimmoryAppClient.kt` -- only organize-files tests exist

**ViewModels (13/17 untested):**
All untested: `BookDetailViewModel`, `CatalogViewModel`, `AudiobookViewModel`, `BrowseViewModel`, `BookdropViewModel`, `EditMetadataViewModel`, `HardcoverViewModel`, `LocalLibraryViewModel`, `ServerListViewModel`, `SettingsViewModel`, `StorageViewModel`, `ReaderDefaultsViewModel`, `StatsViewModel`

**Other high-risk untested areas:**
- `BookOpener.kt` -- Readium publication opening
- `LocatorSerializer.kt` -- locator JSON serialization
- `CfiLocatorConverter.kt` -- CFI conversion
- `CredentialEncryption.kt` -- KeyStore-based credential storage
- Dictionary providers

### Test Quality Issues
- `ReaderViewModelTest` has a `@Disabled` test (line 172) due to `org.json.JSONObject` Android dependency -- real coverage hole
- `LibraryViewModelTest` only tests trivial toggles, not the complex library loading/reconciliation
- `BookRepositoryTest.addLocalBook` doesn't verify entity content or error handling
- No tests for network timeout/exception scenarios in `KosyncClientTest`
- No tests for malformed input (e.g., HTML response instead of OPDS XML)
- Shared test fixtures (`testServer`) duplicated across 4 test files with slightly different values

---

## Architecture & Structure

### Module Boundaries -- Good
Clean two-module split: `:app` (UI) depends on `:core` (data). No circular dependencies. `:core` has no dependency on `:app`.

### Separation of Concerns -- Mostly Good
- ViewModels handle UI logic, repositories handle data
- DAOs are clean Room interfaces
- Models are plain data classes with serialization annotations

**Concerns:**
- `BookRepository.kt` mixes too many concerns (downloading, metadata extraction, catalog refresh, file management)
- `ReaderViewModel.kt` handles too many features via 16 dependencies
- Some Compose screens embed business logic inline (e.g., bookmark matching in `EpubReaderScreen.kt`)

### File Organization -- Good
- Files follow the convention: `ScreenNameScreen.kt`, `ScreenNameViewModel.kt`
- One public class per file with few exceptions
- Package structure mirrors feature boundaries

**Large files (500+ lines) that should be decomposed:**

| File | Lines | Suggested Split |
|------|-------|-----------------|
| `EditMetadataScreen.kt` | 1004 | Extract form sections into sub-composables |
| `LibraryControls.kt` | 942 | Extract filter/sort sheets |
| `BookRepository.kt` | 926 | Extract `BookDownloader`, `CoverExtractor`, `CatalogReconciler` |
| `BookDetailScreen.kt` | 880 | Extract card sections |
| `EpubReaderScreen.kt` | 732 | Extract lifecycle effects, selection handling, overlay |
| `ServerFormScreen.kt` | 726 | Extract form field components |
| `EditMetadataViewModel.kt` | 669 | Extract metadata fetch/save logic |

### Configuration Management -- Good
- Secrets handled via environment variables in CI
- Signing config reads from env vars
- `local.properties` is gitignored
- No hardcoded secrets in source

### Dependency Management -- Good
- All versions in `libs.versions.toml`
- No hardcoded version strings
- One exception: `kxml2:2.3.0` is hardcoded in `core/build.gradle.kts:96` (test dependency)

**Potential staleness:** Some dependencies could be newer (Compose BOM 2024.12.01, Coil 2.7.0 vs 3.x, Room 2.6.1 vs 2.7.x), but nothing is deprecated or EOL.

### Security Concern
- `GrimmoryClient.kt:429` -- `audiobookStreamUrl` embeds JWT token as a URL query parameter. Tokens in URLs can be logged by proxies and intermediaries.

---

## Recommended Action Plan

### Quick Wins (< 30 minutes each)

1. **Remove `continue-on-error: true`** from CI lint and test steps (`ci.yml:29,33`) -- makes quality gates actually block
2. **Add `ktlintCheck` to CI** -- add `./gradlew ktlintCheck` step in `ci.yml`
3. **Extract `parseTimestamp` to a shared utility** -- currently duplicated in `HighlightSyncManager.kt:172-175` and `BookmarkSyncManager.kt:132-135`
4. **Add `toEnumOrNull()` extension** -- eliminates 22 repeated `runCatching { Enum.valueOf() }.getOrNull()` calls across preference repositories
5. **Extract `navController.popBackStack()` to a val** in `EmberNavHost.kt`
6. **Remove duplicate imports** in `EpubReaderScreen.kt:9/14,11/22`
7. **Extract bookmark matching logic** from `EpubReaderScreen.kt:204-215` to use `ReaderViewModel` method, eliminating the `0.02` magic number duplication
8. **Move `ServerListViewModel.kt` hardcoded `delay(2000)`** to a named constant
9. **Hardcode kxml2 version** into version catalog (`core/build.gradle.kts:96`)

### Medium Effort (1-4 hours each)

1. **Extract shared `withAuth` token refresh** into a single `AuthenticatedGrimmoryClient` base class or extension function -- eliminates 4x duplication and fixes fragile 401 string matching
2. **Extract download-to-file streaming** into a shared utility -- used in `GrimmoryClient` (2x) and `OpdsClient` (1x)
3. **Break up `BookRepository`** into `BookDownloader`, `CoverExtractor`, and keep `BookRepository` as the orchestrator/CRUD layer
4. **Add Ktor MockEngine tests for `GrimmoryClient`** -- the most critical untested client at 498 lines
5. **Add tests for `BookmarkSyncManager` and `HighlightSyncManager`** -- untested conflict resolution logic
6. **Unify `HighlightSyncManager`/`BookmarkSyncManager`** into a generic `AnnotationSyncManager<T>` with entity-specific adapters
7. **Create a `GrimmoryFilterRequest` data class** to replace the 14-15 parameter lists in `BookRepository.refreshFromGrimmory` and `GrimmoryAppClient.getBooks`
8. **Add Grimmory API path constants** -- extract 70+ inline path strings into a `GrimmoryApi` object
9. **Replace `runBlocking` in `ReaderViewModel.onCleared()`** with `NonCancellable` context or application-scoped coroutine
10. **Standardize on `LoadingScreen()`/`ErrorScreen()`** from `SharedComponents.kt` -- replace inline reimplementations

### Strategic Refactors (multi-session)

1. **Decompose monolithic Compose screens** -- `EditMetadataScreen` (1004 lines), `BookDetailScreen` (880 lines), `EpubReaderScreen` (732 lines), `LibraryControls` (942 lines). Extract section composables, reduce per-file complexity.

2. **Split `ReaderViewModel`** (16 dependencies) into focused collaborators: `ReadingSessionTracker`, `ProgressSyncHelper`, `BookmarkManager`, `HighlightManager`. ViewModel becomes an orchestrator.

3. **Build comprehensive test suite for sync layer** -- `ProgressSyncManager`, `HighlightSyncManager`, `BookmarkSyncManager`, `SyncWorker`. These handle cross-device data integrity and have zero tests.

4. **Establish consistent error-handling strategy** -- decide on `Result<T>` vs sealed state vs exceptions as the standard pattern, document in CLAUDE.md, and gradually align existing code.

5. **Add detekt** for automated complexity and code smell detection with CI integration.

6. **Extract string resources** for all hardcoded UI text -- prerequisite for internationalization.

---

## Tooling Recommendations

| Tool | Purpose | Priority |
|------|---------|----------|
| **detekt** | Static analysis (complexity, code smells, performance) | Medium |
| **ktlintCheck in CI** | Enforce formatting in PRs | High |
| **CI gate enforcement** | Remove `continue-on-error: true` | High |
| **Shared test fixtures** | Reduce test setup duplication | Low |
| **Kover or JaCoCo** | Code coverage reporting | Medium |
| **Danger or similar** | PR review automation (file size, test coverage delta) | Low |

---

*Generated by Claude Code audit on 2026-04-12*
