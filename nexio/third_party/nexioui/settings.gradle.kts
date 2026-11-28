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
        // 柔光玻璃材质库（独立仓库，tag 触发 JitPack 构建）
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.HaoZai000.*") }
        }
    }
}

rootProject.name = "nexioui"
include(":nexioui")

// 仅使用同一导入中的固定 SoftGlass 源码，禁止静默回退到工作区外版本。
val softglassSource = file("../softglass")
require(softglassSource.resolve("build.gradle.kts").isFile) { "Missing pinned SoftGlass source" }
includeBuild(softglassSource) {
    dependencySubstitution {
        substitute(module("com.github.HaoZai000.SoftGlass:softglass")).using(project(":"))
    }
}
