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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 柔光玻璃库（独立仓库，tag 触发 JitPack 构建）
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.HaoZai000.*") }
        }
    }
}

rootProject.name = "Nexio课程表"

// 固定到本子工程内的源码快照；缺失时立即失败，不读取工作区同级目录。
val softglassSource = file("third_party/softglass")
val nexiouiSource = file("third_party/nexioui")
require(softglassSource.resolve("build.gradle.kts").isFile) { "Missing pinned SoftGlass source" }
require(nexiouiSource.resolve("build.gradle.kts").isFile) { "Missing pinned NexioUI source" }
includeBuild(softglassSource) {
    dependencySubstitution {
        substitute(module("com.github.HaoZai000.SoftGlass:softglass")).using(project(":"))
    }
}
includeBuild(nexiouiSource) {
    dependencySubstitution {
        substitute(module("com.github.HaoZai000.NexioUI:nexioui")).using(project(":nexioui"))
    }
}
include(":androidApp")
include(":core")
include(":ui-shared")
include(":desktopApp")

// 只提供兼容桥和回归测试；默认不参与宿主构建，也不替换现有校园入口。
if (providers.gradleProperty("nexio.gdustCompat").getOrElse("false").toBoolean()) {
    include(":gdust-compat")
    project(":gdust-compat").projectDir = file("adapters/gdust-compat")
}

