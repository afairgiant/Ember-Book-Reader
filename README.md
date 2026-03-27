# Ember

An unofficial Android EPUB/PDF reader built to work with [Grimmory](https://github.com/afairgiant/booklore-n) (formerly Booklore). Also works standalone with local files.

## Features

**Reader**
- EPUB and PDF support via [Readium](https://readium.org/) Kotlin Toolkit
- 13 color themes (including AMOLED true black)
- Customizable fonts, text size, line height, margins, text alignment, hyphenation
- Configurable tap zones for page navigation
- In-book search
- Bookmarks
- Brightness and orientation lock
- Paginated or scroll mode

**Grimmory Integration**
- Browse and download books via OPDS catalog or Grimmory App API
- Reading progress sync via kosync (KOReader-compatible) and Grimmory native API
- Read status tracking (Unread/Reading/Read/DNF)
- Reading session recording
- Series and author browsing
- JWT authentication with auto-refresh

**Library**
- Import local EPUB/PDF files
- Rich metadata extraction from book files (cover, author, publisher, subjects, page count)
- Search, sort, and filter across all downloaded books
- Batch operations (delete, sync)
- Book detail screen with full metadata and read status

**Other**
- Material You dynamic theming (Android 12+)
- Reading statistics with streak calendar
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
2. Enter the OPDS URL (e.g. `https://your-server/api/v1/opds`) and OPDS credentials
3. Optionally add kosync credentials for progress sync
4. Optionally add Grimmory API credentials for read status, sessions, and enhanced catalog browsing
5. Use the test buttons to verify each connection

## Architecture

```
:app    — UI (Jetpack Compose, ViewModels, Navigation, Hilt)
:core   — Data (Room, Ktor, OPDS, Kosync, Grimmory API, Readium, Repositories)
```

See [CLAUDE.md](CLAUDE.md) for detailed architecture docs.

## License

[Apache License 2.0](LICENSE)
