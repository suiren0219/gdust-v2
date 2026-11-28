// NexioUI：可复用的 Compose Multiplatform 控件库。
// 所有控件实现在 commonMain，Android / JVM / iOS / 鸿蒙共用同一份源码。

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

group = "com.haooz.nexioui"
version = "0.1.1"

// 与宿主保持同一开关；单独构建可用 nexioui.ios 覆盖。
val buildIos = providers.gradleProperty("nexioui.ios")
    .orElse(providers.gradleProperty("nexio.ios")).getOrElse("false").toBoolean()

kotlin {
    android {
        namespace = "com.haooz.nexioui"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    if (buildIos) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        // 桌面 / iOS 共用 Skia 后端；Android 使用原生 Canvas 实现。
        val skikoMain by creating {
            dependsOn(commonMain.get())
        }
        getByName("jvmMain").dependsOn(skikoMain)
        if (buildIos) {
            val iosMain = maybeCreate("iosMain").apply { dependsOn(commonMain.get()) }
            iosMain.dependsOn(skikoMain)
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }

        commonMain.dependencies {
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            // 柔光 / 液态玻璃材质的唯一实现来源
            api(libs.softglass)
            // 返回键与预测性返回手势（OverlayDialog 跟手动画）
            implementation(libs.navigationevent.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

// JitPack 通过 maven-publish 的产物发布，实际坐标由仓库推导：
// com.github.HaoZai000.NexioUI:nexioui:<tag>
publishing {
    publications.withType<org.gradle.api.publish.maven.MavenPublication>().configureEach {
        pom {
            name.set("NexioUI")
            description.set("可复用的 Compose Multiplatform 控件库，Android / JVM / iOS 共用一套源码。")
            url.set("https://github.com/HaoZai000/NexioUI")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("HaoZai000")
                    name.set("HaoZai000")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/HaoZai000/NexioUI.git")
                developerConnection.set("scm:git:ssh://git@github.com/HaoZai000/NexioUI.git")
                url.set("https://github.com/HaoZai000/NexioUI")
            }
        }
    }
}
