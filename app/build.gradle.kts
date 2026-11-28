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
    val vCode = 28
    val vName = "2.0.0-alpha.$vCode"

    defaultConfig {
        // 固定安装标识包含学校和作者；桌面名称与图标由独立快捷方式设置。
        applicationId = "cn.edu.gdust.suiren"
        minSdk = 33
        targetSdk = 35
        versionCode = vCode
        versionName = vName
        buildConfigField("boolean", "PREVIEW_TARGET", "false")
        buildConfigField("boolean", "MCP_ENABLED", "true")
    }

    // 正式应用与隔离离线预览分别构建，预览包不生成 Release。
    flavorDimensions += "previewTarget"
    productFlavors {
        create("production") {
            dimension = "previewTarget"
            buildConfigField("boolean", "PREVIEW_TARGET", "false")
            buildConfigField("boolean", "MCP_ENABLED", "true")
        }
        create("preview") {
            dimension = "previewTarget"
            applicationIdSuffix = ".preview"
            buildConfigField("boolean", "PREVIEW_TARGET", "true")
            buildConfigField("boolean", "MCP_ENABLED", "false")
        }
    }
    buildFeatures { buildConfig = true }

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

    androidComponents {
        beforeVariants(selector().withFlavor("previewTarget" to "preview").withBuildType("release")) { variant ->
            variant.enable = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".mcptest"
            versionNameSuffix = "-mcp-debug"
            buildConfigField("int", "MCP_PORT", "26622")
        }
        release {
            buildConfigField("int", "MCP_PORT", "26621")
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

dependencies {
    // 纯 NexioUI + SoftGlass 组件与主题库（不再引入旧版 Miuix / kyant.backdrop）
    implementation(project(":nexio-ui"))
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
    implementation(project(":chaoxing-core"))
    // 学习通作业页是服务端渲染 HTML：用 Jsoup 解析（纯 Java，单测也能跑）
    implementation("org.jsoup:jsoup:1.17.2")
    // 只取二维码矩阵生成（Canvas 自绘，不引完整扫码 SDK）
    implementation("com.google.zxing:core:3.5.3")

    // 莫奈取色：MaterialKolor 纯 Kotlin 引擎（MIT，无 Compose/native 依赖，不会抬 runtime 版本）
    testImplementation("junit:junit:4.13.2")
    implementation("com.materialkolor:material-color-utilities:6.0.0")
    // 桌面小组件：Glance（Compose 风格写 AppWidget）
    implementation("androidx.glance:glance-appwidget:1.1.1")
}
