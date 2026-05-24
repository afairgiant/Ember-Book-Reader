# Development Setup

This guide gets Ember building and running on your machine from scratch. It
assumes you can program but have never built an Android app — no prior Android,
Kotlin, or Gradle knowledge required.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | Android Studio bundles a compatible JDK; a standalone install also works. |
| Android Studio | Latest stable | Easiest way to get the SDK, an emulator, and Gradle. |
| Android SDK | API 35 (Android 15) | Installed via Android Studio's SDK Manager. |

## 1. Install the JDK

Ember builds with JDK 17. If you use the JDK bundled with Android Studio you can
skip a manual install. To check a standalone install:

```bash
java -version
```

You should see a version starting with `17`.

## 2. Install Android Studio and the SDK

1. Install Android Studio (latest stable).
2. Open **SDK Manager** (Settings → Languages & Frameworks → Android SDK).
3. Under **SDK Platforms**, install **Android 15 (API 35)**.
4. Under **SDK Tools**, ensure **Android SDK Build-Tools** and
   **Android SDK Platform-Tools** are installed.

## 3. Clone the repository

```bash
git clone https://github.com/afairgiant/Ember-Book-Reader.git
cd Ember-Book-Reader
```

## 4. Point the build at your SDK

Gradle needs to know where the Android SDK lives. The simplest path is to open
the project in Android Studio — it creates `local.properties` with the correct
`sdk.dir` automatically on first sync.

If you build from the command line without opening Android Studio, set the
`ANDROID_HOME` environment variable to your SDK location, or create
`local.properties` in the repo root:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

On Windows, escape the backslashes (e.g. `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`), or just open the project in Android Studio to avoid the issue entirely.

`local.properties` is machine-specific and is not committed.

## 5. Build the debug APK

Use the Gradle wrapper that ships with the repo (no separate Gradle install
needed):

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows (PowerShell or cmd)
.\gradlew.bat assembleDebug
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## 6. Run the app

**Emulator:** In Android Studio, open **Device Manager**, create a virtual
device running API 28+ (the app's minimum is Android 9 / API 28), then press
**Run** (▶).

**Physical device:** Enable Developer Options and USB debugging on the device,
connect it, and press **Run** — or install the APK directly with:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Common gotchas

- **Wrong JDK.** If the build complains about an unsupported class file or
  Java version, confirm Gradle is using JDK 17: run `./gradlew --version` and
  check the "JVM" line, or set the Gradle JDK in Android Studio
  (Settings → Build → Build Tools → Gradle → Gradle JDK).
- **Missing API 35.** A "failed to find target with hash string android-35"
  error means API 35 isn't installed — add it in the SDK Manager.
- **Gradle sync.** After cloning, let Android Studio finish its initial Gradle
  sync before building or running; the first sync downloads dependencies and
  can take a few minutes.

## Working without a Grimmory server

Ember is a standalone reader **and** a companion to a self-hosted
[Grimmory](https://github.com/afairgiant/booklore-n) book server. You do **not**
need a server to work on much of the app:

- **Works standalone (no server):** the reader, local EPUB/PDF import, local
  audiobook playback, the local library, themes, and reader settings.
- **Needs a running Grimmory server:** catalog browsing, progress sync,
  read-status tracking, reading sessions, and Book Drop.

If you don't have a Grimmory server, pick a starter task in the standalone areas
above. See [architecture.md](architecture.md) for where those features live.

## Next steps

- [Architecture tour](architecture.md) — how the codebase is organized.
- [Code conventions](conventions.md) — the style your PR should match.
