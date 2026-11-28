// gdust-app@68a638d 的兼容桥；作为可选模块保存，不接入 Nexio 页面或宿主存储。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.haooz.chedule.gdustcompat"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    if (providers.gradleProperty("nexio.ios").getOrElse("false").toBoolean()) {
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(compose.runtime)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.okhttp)
            implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
            implementation(libs.gson)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
