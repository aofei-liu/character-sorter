pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "charsorter-android"

// :app needs the Android SDK, installed under ~/opt on the WSL box per
// ROADMAP.md ("Toolchain lives on the WSL box"). It is still absent from a
// cloud session, where Google's Maven hosts are off the network allowlist.
include(":client")
include(":app")
