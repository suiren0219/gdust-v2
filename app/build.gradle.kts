import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("signing/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = rootProject.file("signing/gdust-v2-release.jks").exists() && keystoreProps.isNotEmpty()

android {
    namespace = "com.menglimi.gdust.v2"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.menglimi.gdust.v2"
        minSdk = 33  // 双风格共存：Glasmio 要求 33+，softglass 本身支持 26+
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0-alpha"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file("signing/gdust-v2-release.jks")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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
    // 只取二维码矩阵生成（Canvas 自绘，不引完整扫码 SDK）
    implementation("com.google.zxing:core:3.5.3")

    // Nexio 作者的 HyperOS 柔光玻璃引擎（Apache-2.0，JitPack，纯 Kotlin 无 native）
    // 风格一：HyperOS 柔光（只取 android 变体，根坐标会拉 jvm jar 导致 duplicate class）
    implementation("com.github.HaoZai000.softglass:softglass-android:0.1.0")
    // 风格二：iOS 27 液态玻璃（Apache-2.0，Maven 三模块同版本 0.1.2）
    implementation("io.github.neoruaa.glasmio:glasmio-scaffold:0.1.2")
    implementation("io.github.neoruaa.glasmio:glasmio-preference:0.1.2")
    implementation("io.github.neoruaa.glasmio:glasmio-ui:0.1.2")
}
