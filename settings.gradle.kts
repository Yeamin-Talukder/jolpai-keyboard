pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "JolpaiKeyboard"

include(":app-shell")
include(":keyboard-core")
include(":keyboard-ui")
include(":bangla-engine")
include(":suggestion-engine")
include(":core-data")
include(":clipboard-manager")
include(":emoji-engine")
include(":voice-input")
include(":media")
include(":theme-engine")

// PC-only tool: trains the next-word models (not part of the app).
include(":lm-trainer")
