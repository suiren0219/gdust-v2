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
rootProject.name = "IndependentCampusApps"
include(":ksu-ui")
val selectedApp = providers.gradleProperty("standaloneApp").orElse("all").get()
require(selectedApp in setOf("all", "learning", "power")) { "standaloneApp must be all, learning or power" }
if (selectedApp != "power") {
    include(":learning-app", ":chaoxing-core")
    project(":chaoxing-core").projectDir = file("../chaoxing-core")
}
if (selectedApp != "learning") {
    include(":power-app", ":power-core")
    project(":power-core").projectDir = file("../power-core")
}
