# Architecture Tour

A human-friendly map of how Ember is put together, so you can find the right
file fast. For the canonical architecture rules and design decisions, see
[CLAUDE.md](../../CLAUDE.md).

## Modules

Ember is a multi-module Gradle project:

| Module | Responsibility |
|--------|----------------|
| `:app` | UI layer — Jetpack Compose screens, ViewModels, navigation, theme, Hilt DI modules. |
| `:core` | Data layer — models, Room database, Ktor networking, OPDS, kosync, Grimmory and Hardcover APIs, Readium integration, repositories. |
| `:feature` | Reserved extraction point for shared feature logic. Currently thin. |

Source lives under `com.ember.reader` (the `:app` module) and
`com.ember.reader.core` (the `:core` module).

## The pattern: Compose + ViewModel + Repository

Ember follows MVVM with a single-source-of-truth repository layer:

```
Compose screen  →  ViewModel  →  Repository  →  DAO (Room) / Remote (Ktor)
   (stateless,      (one per      (single        (local + network
    hoisted state)   screen)       source of      sources)
                                   truth)
```

- **Screens** are stateless composables; state is hoisted from the ViewModel.
- **ViewModels** expose UI state as sealed classes (`Loading` / `Success` /
  `Error`) over Kotlin `Flow`.
- **Repositories** are the single source of truth, combining a Room DAO with a
  remote source.
- Streams use `Flow`; one-shot operations use `suspend` functions. No LiveData.

## Where the UI lives (`:app`)

Under `com.ember.reader`:

| Area | Package |
|------|---------|
| Navigation graph | `navigation/` |
| Reader screen | `ui/reader/` |
| Library | `ui/library/` |
| Catalog browsing | `ui/catalog/`, `ui/browse/` |
| Book detail | `ui/book/` |
| Book Drop | `ui/bookdrop/` |
| Downloads / uploads | `ui/download/`, `ui/upload/` |
| Metadata editing | `ui/editmetadata/`, `ui/organize/` |
| Hardcover screens | `ui/hardcover/` |
| Server setup | `ui/server/` |
| Settings | `ui/settings/` |
| Theme | `ui/theme/` |
| Shared components | `ui/common/` |

## Where the data lives (`:core`)

Under `com.ember.reader.core`:

| Area | Package |
|------|---------|
| Domain models | `model/` |
| Room database + DAOs | `database/` |
| Ktor networking | `network/` |
| OPDS catalog parsing | `opds/` |
| Grimmory App API | `grimmory/` |
| Hardcover API | `hardcover/` |
| Progress sync (kosync) | `sync/` |
| Readium integration | `readium/` |
| Repositories | `repository/` |
| Paging | `paging/` |
| Dictionary lookups | `dictionary/` |
| Coroutine scopes/utils | `coroutine/`, `util/` |

## Three key flows

**Reader.** Ember wraps Readium's Fragment-based navigator inside Compose (the
Compose navigator is still experimental). Reader UI is in `ui/reader/`; the
Readium glue is in `core/readium/`.

**Progress sync.** Reading progress syncs two ways: kosync (KOReader-compatible,
percentage-based for cross-client compatibility) and the Grimmory native API.
The sync logic lives in `core/sync/` and `core/grimmory/`; the Readium
`Locator` is stored locally for precise resume.

**Catalog browsing.** Book catalogs come from a Grimmory server over OPDS 1.2
(Atom XML) and the Grimmory App API. Parsing is in `core/opds/` and
`core/grimmory/`; the browsing UI is in `ui/catalog/` and `ui/browse/`.

## Where do I find X?

- **A screen's logic?** Its ViewModel sits next to the screen in the same
  `ui/<area>/` package.
- **A network call?** Start in `core/network/` for the client, then the
  per-service package (`core/grimmory/`, `core/hardcover/`, `core/opds/`).
- **A database table?** `core/database/` (entities + DAOs).
- **Dependency injection wiring?** Hilt modules — one per `:core` subpackage
  (e.g. `DatabaseModule`, `NetworkModule`, `SyncModule`).

## Going deeper

[CLAUDE.md](../../CLAUDE.md) documents the full architecture, server-integration
details (OPDS/kosync endpoints and payloads), key design decisions, and known
limitations.
