# Contributing to Ember

Thanks for your interest in helping out! Ember is an Android EPUB/PDF/audiobook
reader, and contributions of all sizes are welcome. This page gets you oriented;
the linked guides have the detail.

## Prerequisites at a glance

- **JDK 17**
- **Android Studio** (latest stable)
- **Android SDK** — API 35 (Android 15)

Full step-by-step instructions are in
[docs/development/setup.md](docs/development/setup.md).

## Where to start

Not sure what to work on? Browse the open issues labeled for newcomers:

- [Good first issues](https://github.com/afairgiant/Ember_Reading-App/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
  — small, well-scoped tasks ideal for a first contribution.
- [Help wanted](https://github.com/afairgiant/Ember_Reading-App/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)
  — issues where help is especially appreciated.

Comment on an issue to let others know you're taking it.

If you want a task you can do without a Grimmory server, see
[Working without a Grimmory server](docs/development/setup.md#working-without-a-grimmory-server).

## Quickstart

```bash
git clone https://github.com/afairgiant/Ember_Reading-App.git
cd Ember_Reading-App
```

1. Clone the repo (above).
2. Open the project in Android Studio and let the initial Gradle sync finish —
   this generates `local.properties` with your SDK path automatically.
3. Build the debug APK with `./gradlew assembleDebug` (use `.\gradlew.bat` on
   Windows), or just build from Android Studio.
4. Press **Run** to launch the app on an emulator or device.

Open the project in Android Studio first: the Gradle command above needs the
SDK path in `local.properties`, which the first sync creates for you. See
[setup.md](docs/development/setup.md) for emulator/device details and common
gotchas.

## Submitting your change

1. Fork the repository and create a branch for your change.
2. Make your change, following the [code conventions](docs/development/conventions.md).
3. Open a pull request describing what you changed and why.

## Learn the codebase

- [Setup guide](docs/development/setup.md) — get the app building and running.
- [Architecture tour](docs/development/architecture.md) — how the project is
  organized and where things live.
- [Code conventions](docs/development/conventions.md) — the style your PR should
  match.
