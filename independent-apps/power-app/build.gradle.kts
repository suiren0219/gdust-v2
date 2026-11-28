plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeyStore = providers.gradleProperty("releaseKeyStore")
val releaseStorePassword = providers.gradleProperty("releaseStorePassword")
val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias")
val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword")

android {
    namespace = "com.menglimi.dormpower"
    compileSdk = 37
    defaultConfig {
        applicationId = "cn.edu.gdust.suiren.power"
        minSdk = 33
        targetSdk = 35
        versionCode = 11
        versionName = "0.1.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug { applicationIdSuffix = providers.gradleProperty("powerTestApplicationSuffix").orElse(".debug").get() }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeyStore.isPresent) {
                signingConfig = signingConfigs.create("release").apply {
                    storeFile = file(releaseKeyStore.get())
                    storePassword = releaseStorePassword.orNull
                    keyAlias = releaseKeyAlias.get()
                    keyPassword = releaseKeyPassword.orNull
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":power-core"))
    implementation(project(":ksu-ui"))
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
}
