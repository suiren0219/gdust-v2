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
rootProject.name = "Kedian"
include(":app", ":power-core", ":chaoxing-core")
project(":power-core").projectDir = file("../../../power-core")
project(":chaoxing-core").projectDir = file("../../../chaoxing-core")
