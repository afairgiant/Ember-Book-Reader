# Ember Book Reader

Modern Android EPUB/PDF reader. Companion app for self-hosted [Grimmory](https://github.com/grimmory-tools/grimmory) book servers with native reading progress sync via kosync. Also works as a standalone reader for local files.

## Features

- **EPUB and PDF reading** powered by [Readium](https://readium.org/) Kotlin Toolkit
- **OPDS 1.2 catalog browsing** — browse, search, and download books from Grimmory
- **Kosync progress sync** — bidirectional reading position sync compatible with KOReader
- **Material You** — dynamic color theming on Android 12+
- **Bookmarks and highlights** with 6 preset highlight colors
- **Table of contents** navigation
- **Reader preferences** — font family, size, line height, theme (light/dark/sepia), pagination mode, brightness
- **Background sync** via WorkManager with configurable frequency
- **Offline-first** — downloaded books and cached progress work without connectivity
- **Encrypted credential storage** via Android EncryptedSharedPreferences
- **Multi-server support** — connect to multiple Grimmory instances

## Requirements

- Android 9+ (API 28)
- A [Grimmory](https://github.com/grimmory-tools/grimmory) server (optional — works standalone with local files)

## Building

```bash
git clone https://github.com/afairgiant/Ember-Book-Reader.git
cd Ember-Book-Reader
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/`.

### Prerequisites

- JDK 17
- Android SDK (API 35 for target, API 28 for min SDK testing)
- Android Studio Ladybug (2024.2+) recommended

## Architecture

```
:app    — UI layer (Jetpack Compose, ViewModels, Navigation, Hilt DI)
:core   — Data layer (Room, Ktor, OPDS, Kosync, Readium, Repositories)
```

- **UI**: Jetpack Compose + Material 3
- **Reader**: Readium Kotlin Toolkit 3.1.2 (Fragment-based navigator wrapped in Compose)
- **Database**: Room (SQLite)
- **Networking**: Ktor Client + Kotlin Serialization
- **DI**: Hilt
- **Async**: Kotlin Coroutines + Flow
- **Image Loading**: Coil

See [CLAUDE.md](CLAUDE.md) for detailed architecture decisions and conventions.

## Server Setup

1. Install and run a [Grimmory](https://github.com/grimmory-tools/grimmory) instance
2. Create OPDS credentials in Grimmory settings (for browsing/downloading)
3. Create KOReader sync credentials in Grimmory settings (for progress sync)
4. In Ember, tap **Add Server** and enter the server URL and both credential sets
5. Use **Test OPDS** and **Test Kosync** to verify connectivity

## License

[Apache License 2.0](LICENSE)
