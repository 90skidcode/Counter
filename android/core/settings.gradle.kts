// Standalone build for the framework-independent domain layer.
// Included into the Android build via includeBuild("core") so it can also be
// built and unit-tested on a plain JVM without the Android SDK.
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

rootProject.name = "core"
