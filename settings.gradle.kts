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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor (the YouTube engine) and its nanojson only ship on JitPack.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") } }
    }
}

rootProject.name = "Legato"

include(":app")
