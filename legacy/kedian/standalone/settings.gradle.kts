pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.20"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "independent-feature-modules"
include(":power-core", ":chaoxing-core")
project(":power-core").projectDir = file("../../../power-core")
project(":chaoxing-core").projectDir = file("../../../chaoxing-core")
