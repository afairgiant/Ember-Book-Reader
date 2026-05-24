# Code Conventions

Match these so your PR reads like the rest of the codebase. The full list lives
in [CLAUDE.md](../../CLAUDE.md); this guide shows the ones that come up most,
with examples.

## Naming — no abbreviations

Use full words. Clarity over brevity.

```kotlin
// Yes
val serverRepository = ServerRepository()

// No
val serverRepo = ServerRepository()
```

## Trailing commas everywhere

Trailing commas keep diffs clean when the next change adds a parameter.

```kotlin
fun connect(
    url: String,
    username: String,
    password: String,   // trailing comma
) { /* ... */ }
```

## `Result<T>` for error handling

Wrap operations that can fail in `Result<T>` rather than throwing across layers.

```kotlin
suspend fun fetchCatalog(): Result<Catalog> = runCatching {
    api.getCatalog()
}
```

## One public class per file

Each file holds one public class or interface, and the file name matches it.
The only exception is a small set of closely related sealed classes.

```
ServerRepository  ->  ServerRepository.kt
GrimmoryApi       ->  GrimmoryApi.kt
```

## A `@Preview` for every Compose screen

Every screen-level composable gets a `@Preview` function so it renders in the
Android Studio preview pane.

```kotlin
@Preview
@Composable
private fun LibraryScreenPreview() {
    EmberTheme {
        LibraryScreen(/* sample state */)
    }
}
```

## One Hilt module per `:core` subpackage

DI wiring is split by subpackage (`DatabaseModule`, `NetworkModule`,
`SyncModule`, …). Use constructor injection; avoid field injection.

```kotlin
class ServerRepository @Inject constructor(
    private val dao: ServerDao,
    private val api: GrimmoryApi,
)
```

## Dependencies via the version catalog

Declare dependencies in `gradle/libs.versions.toml` and reference them with the
`libs.*` accessors. No hardcoded version strings in `build.gradle.kts`.

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.ktor.client.core)
}
```

## Formatting

This project applies [ktlint](https://github.com/JLLeitschuh/ktlint-gradle) to
every module for Kotlin formatting. Before opening a PR:

```bash
./gradlew ktlintCheck     # report formatting issues (use .\gradlew.bat on Windows)
./gradlew ktlintFormat    # auto-fix what it can
```

## More

[CLAUDE.md](../../CLAUDE.md) lists the complete set of conventions, plus the
project's architecture pattern and key decisions.
