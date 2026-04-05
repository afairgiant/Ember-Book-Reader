# Ember

An unofficial Android EPUB/PDF/Audiobook reader built to work with [Grimmory](https://github.com/afairgiant/booklore-n) (formerly Booklore). Also works standalone with local files.

## Features

**Reader**
- EPUB, PDF, and audiobook support via [Readium](https://readium.org/) Kotlin Toolkit
- 13 color themes (including AMOLED true black)
- Customizable fonts, text size, line height, margins, text alignment, hyphenation
- Configurable tap zones for page navigation
- Paginated or scroll mode
- In-book search
- Bookmarks with custom names and ribbon indicator
- Highlights with color sync to Grimmory
- Brightness and orientation lock

**Grimmory Integration**
- Browse and download books via Grimmory App API (libraries, shelves, series, authors)
- Reading progress sync via kosync (KOReader-compatible) and Grimmory native API
- Read status tracking (Unread/Reading/Read/DNF)
- Reading session recording with cross-device stats
- Book Drop — review, edit metadata, and finalize pending books from mobile
- Reading streak and statistics pulled from Grimmory
- Recently added books on home screen
- JWT authentication with auto-refresh

**Hardcover Integration**
- Connect your [Hardcover](https://hardcover.app) account to view reading lists
- Browse currently reading, want to read, and completed books
- In-app book detail with ratings, description, and edition info
- Search Grimmory library from Hardcover book detail

**Library**
- Import local EPUB/PDF files
- Rich metadata extraction from book files (cover, author, publisher, subjects, page count)
- Search, sort, and filter across all downloaded books
- Batch operations (delete, sync)
- Book detail with hero cover, enriched metadata from Hardcover, and related series books

**Home & Navigation**
- Reading-focused home screen with currently reading, recently added, and quick stats
- Browse tab for server catalogs and Hardcover
- Settings hub with appearance, sync, downloads, storage, and stats sub-pages
- Material You dynamic theming (Android 12+)

**Other**
- Audiobook playback with notification controls and backdrop artwork
- Background sync via WorkManager
- Offline support — downloaded books and cached data work without connectivity
- Multi-server support
- Encrypted credential storage

## Requirements

- Android 9+ (API 28)
- [Grimmory](https://github.com/afairgiant/booklore-n) server (optional)

## Install

Download the latest APK from [Releases](https://github.com/afairgiant/Ember_Reading-App/releases), or build from source:

```bash
git clone https://github.com/afairgiant/Ember_Reading-App.git
cd Ember_Reading-App
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/`

### Build requirements

- JDK 17
- Android SDK (API 35)

## Connecting to Grimmory

1. In Ember, tap **Add Server** on the Home screen
2. Enter the server URL and OPDS credentials for catalog access
3. Optionally add kosync credentials for progress sync
4. Optionally add Grimmory API credentials for read status, sessions, Book Drop, and enhanced catalog browsing
5. Use the test buttons to verify each connection

## Architecture

```
:app    — UI (Jetpack Compose, ViewModels, Navigation, Hilt)
:core   — Data (Room, Ktor, OPDS, Kosync, Grimmory API, Hardcover API, Readium, Repositories)
```

See [CLAUDE.md](CLAUDE.md) for detailed architecture docs.

## Built with AI

This app was developed with the assistance of [Claude Code](https://claude.ai/code) (Anthropic).

## License

[Apache License 2.0](LICENSE)
