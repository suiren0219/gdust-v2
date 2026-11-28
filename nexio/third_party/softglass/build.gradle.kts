plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    `maven-publish`
}

group = "com.haooz.softglass"
version = providers.gradleProperty("softglass.version").getOrElse("0.1.3")

kotlin {
    android {
        namespace = "com.haooz.softglass"
        compileSdk = 37
        minSdk = 26
    }
    jvm()

    // 与宿主保持同一开关；单独构建可用 softglass.ios 覆盖。
    val enableIos = providers.gradleProperty("softglass.ios")
        .orElse(providers.gradleProperty("nexio.ios")).getOrElse("false").toBoolean()
    if (enableIos) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        // jvm 与 iOS 共用 Skiko 后端
        val skikoMain by creating {
            dependsOn(commonMain.get())
        }
        getByName("jvmMain").dependsOn(skikoMain)
        if (enableIos) {
            val iosMain = maybeCreate("iosMain").apply { dependsOn(commonMain.get()) }
            iosMain.dependsOn(skikoMain)
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }
    }
}

/**
 * 各 target 的 publication 由 KMP / AGP 自动注册，这里只补 POM 元数据。
 *
 * 本地验证：./gradlew publishToMavenLocal
 */
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("SoftGlass")
            description.set("Compose Multiplatform 的柔光玻璃渲染引擎：采样、折射、7 级模糊金字塔与连续圆角几何")
            url.set("https://github.com/HaoZai000/softglass")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            scm {
                url.set("https://github.com/HaoZai000/softglass")
                connection.set("scm:git:git://github.com/HaoZai000/softglass.git")
                developerConnection.set("scm:git:ssh://git@github.com/HaoZai000/softglass.git")
            }
            developers {
                developer {
                    id.set("HaoZai000")
                    name.set("Haooz")
                }
            }
        }
    }
}
