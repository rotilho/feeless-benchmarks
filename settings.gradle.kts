pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "feeless-benchmarks"

include("app", "atto", "core", "nano")

project(":app").projectDir = file("benchmark-app")
project(":atto").projectDir = file("benchmark-atto")
project(":core").projectDir = file("benchmark-core")
project(":nano").projectDir = file("benchmark-nano")
