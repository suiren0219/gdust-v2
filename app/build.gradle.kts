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

    // 版本标识单一来源：versionName 直接带构建号，装的是不是最新一眼可见
    val vCode = 17
    val vName = "2.0.0-alpha.$vCode"

    defaultConfig {
        applicationId = "com.menglimi.gdust.v2"
        minSdk = 33
        targetSdk = 35
        versionCode = vCode
        versionName = vName
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
            applicationIdSuffix = ".research"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

// 排除外部子库传递的官方 miuix-ui，避免与 vendor 进源码的 fork 重复。
// 官方 miuix-ui 的类由本项目源码顶替（同名同包同 API）。
configurations.configureEach {
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui-android")
}

fun ExternalModuleDependency.excludeMiuixUi() {
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui-android")
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
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // 学习通作业页是服务端渲染 HTML：用 Jsoup 解析（纯 Java，单测也能跑）
    implementation("org.jsoup:jsoup:1.17.2")
    // 只取二维码矩阵生成（Canvas 自绘，不引完整扫码 SDK）
    implementation("com.google.zxing:core:3.5.3")

    // Miuix（小米 HyperOS 风格，Apache-2.0）：:miuix / :backdrop 两个 fork 已 vendor 进源码（移植来源见 NOTICE.md），
    // 因此**不再**依赖官方 miuix-ui / kyant backdrop 构件，只保留图标等独立子库并排除会带来重复类的 miuix-ui。
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4") { excludeMiuixUi() }
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4") { excludeMiuixUi() }
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3") { excludeMiuixUi() }
    // vendor Miuix / backdrop 仍使用的连续圆角形状（Apache-2.0）
    implementation("io.github.kyant0:capsule:2.1.3")
    implementation("io.github.kyant0:shapes:1.2.1")

    // 莫奈取色：MaterialKolor 纯 Kotlin 引擎（MIT，无 Compose/native 依赖，不会抬 runtime 版本）
    testImplementation("junit:junit:4.13.2")
    implementation("com.materialkolor:material-color-utilities:6.0.0")
    // 桌面小组件：Glance（Compose 风格写 AppWidget）
    implementation("androidx.glance:glance-appwidget:1.1.1")
}
