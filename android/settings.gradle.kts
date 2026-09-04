pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "charsorter-android"

// :app (Compose UI) is deliberately absent: it needs the Android SDK, which
// no cloud session here can install. See ROADMAP.md, "Module split".
include(":client")
