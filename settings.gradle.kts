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

rootProject.name = "calevent"

// Pure Kotlin business logic: builds and tests on any machine with a JDK.
include(":core")

// The Android app needs the Android SDK. On a machine without it, only :core is loaded,
// so `./gradlew :core:test` keeps working.
val hasAndroidSdk = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) include(":app")
