plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.menglimi.gdust.v2"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.menglimi.gdust.v2"
        minSdk = 33  // Glasmio 要求 Android 13+（native 液态玻璃渲染）
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0-alpha"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 单模块：Compose 原生实现（不引 Miuix fork、不引 KMP、不引 SQLDelight）
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // 网络（本机直连学校系统 / 完美校园 / 用户自填中转站）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // iOS 27 液态玻璃 UI（Apache-2.0，Maven 四模块同版本 0.1.2）
    implementation("io.github.neoruaa.glasmio:glasmio-scaffold:0.1.2")
    implementation("io.github.neoruaa.glasmio:glasmio-preference:0.1.2")
    implementation("io.github.neoruaa.glasmio:glasmio-ui:0.1.2")
}
