// NexioUI 0.1.2 原始源码：HaoZai000/NexioUI@3fa6c2fb7ede934d0a6d6bdc6576d39580bd6fd9。
// 仅保留 Android 目标；commonMain / androidMain 的 expect / actual 源文件保持原样。
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.haooz.nexioui"
version = "0.1.2"

kotlin {
    android {
        namespace = "com.haooz.nexioui"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api("com.github.HaoZai000.SoftGlass:softglass:0.1.4")
            implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        }
    }
}
