pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/nicholasgasior/readium-kotlin-toolkit") {
            // Readium artifacts
        }
    }
}

rootProject.name = "Ember"

include(":app")
include(":core")
