# NexioUI

可复用的 Compose Multiplatform 控件库。所有控件实现在 `commonMain`，Android / JVM（桌面）/ iOS 共用同一份源码，不依赖任何系统私有框架。

> 状态：`0.1.0`，API 仍在收敛，升级前请留意 [CHANGELOG](CHANGELOG.md)。

## 特性

- **一套源码多端复用**：Android、Desktop(JVM)、iOS（arm64 + Simulator arm64）。
- **HyperOS 风格控件**：卡片、按钮、开关、滑块、分段控件、搜索栏、数字选择器、日期区间日历、偏好设置项、顶栏 / 底栏、底部弹窗、对话框等。
- **液态玻璃材质**：基于 [SoftGlass](https://github.com/HaoZai000/SoftGlass) 的实时高斯模糊与柔光着色器，Android / Skia 后端通用。
- **统一交互反馈**：全库共用一套按压高光（AGSL RuntimeShader）与整块淡色按压指示器，不使用 Material 水波纹。
- **滚动与动效**：弹簧回弹 overscroll、滚动触感、共享元素转场、弧形弯折、水波纹等。

## 支持平台

| 平台 | 目标 | 最低版本 |
| --- | --- | --- |
| Android | `android` | minSdk 26 |
| Desktop | `jvm` | JDK 17+ |
| iOS | `iosArm64` / `iosSimulatorArm64` | iOS 13+ |

依赖：Compose Multiplatform 1.12、Kotlin 2.4。

## 安装

JitPack 仓库：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.HaoZai000.NexioUI:nexioui:0.1.0")
}
```

> 玻璃材质依赖 `com.github.HaoZai000.SoftGlass:softglass`，会随本库传递引入，同样来自 JitPack。
>
> JitPack 的构建机是 Linux，只能产出 Android / JVM 制品；iOS 请在 macOS 上克隆源码后本地发布。需要 iOS 制品时可以提 issue，后续会改用支持全平台的 CI 发布。

## 快速开始

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import com.haooz.nexioui.button.Button
import com.haooz.nexioui.card.Card
import com.haooz.nexioui.scaffold.Scaffold
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme

@Composable
fun App() {
    NexioTheme(isDark = isSystemInDarkTheme()) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Card {
                    NexioText("NexioUI")
                }
                Button(onClick = { /* ... */ }) {
                    NexioText("确定")
                }
            }
        }
    }
}
```

`NexioTheme` 会一并提供语义色板、文字样式、按压指示器与滚动回弹：

```kotlin
NexioTheme.colorScheme.primary
NexioTheme.textStyles.body1
NexioTheme.isDark
```

### 液态玻璃组件

按钮、弹窗、底栏等玻璃组件需要传入 softglass 的 `GlassBackdrop`（采样源）。最简用法是先录一层页面内容，再把它交给玻璃组件：

```kotlin
val backdrop = rememberLayerBackdrop()

Box(
    modifier = Modifier
        .fillMaxSize()
        .layerBackdrop(backdrop),
) {
    // 页面内容
    TextButton(
        text = "确定",
        onClick = { /* ... */ },
        backdrop = backdrop,
    )
}
```

控件在绘制期实时采样，滚动、翻页、主题切换后都会自动重新采样，不需要手动刷新。

## 组件概览

| 分类 | 包 | 内容 |
| --- | --- | --- |
| 主题与反馈 | `theme` `color` `indication` `pressfeedback` `scrollhaptic` `overscroll` | 色板、文字样式、按压指示器、滚动触感、弹簧回弹 |
| 布局与容器 | `scaffold` `surface` `card` `cardexpand` `divider` `smalltitle` | 脚手架、卡片、可展开卡片、分隔线、小标题 |
| 顶栏 / 底栏 | `topappbar` `progressiveblurtopbar` `topbarmaterial` `bottombar` `tabletnav` | 顶栏、渐进模糊顶栏、底栏、平板导航 |
| 按钮与选择 | `button` `textbutton` `iconbutton` `checkbox` `switch` `slider` `capsuleslider` `segmentedcontrol` | 按钮、开关、滑块、分段控件 |
| 输入 | `textfield` `nativetextfield` `searchbar` `numberpicker` `daterangecalendar` | 输入框、搜索栏、数字/日期选择 |
| 弹层 | `overlaydialog` `overlaypopup` `blurbottomsheet` `dropdown` | 对话框、弹出菜单、底部弹窗、下拉菜单 |
| 偏好设置 | `arrowpreference` `checkboxpreference` `switchpreference` | 设置页常用条目 |
| 进度 | `progressbar` `progressindicator` | 进度条、加载指示器 |
| 动效与特效 | `anim` `motion` `ripple` `background` `interaction` `window` `colorpalette` | 弹簧、转场、水波纹、背景特效、窗口圆角 |
| 图标 | `icon` | 随库提供的图标集 |

## 构建

需要 JDK 17+，Android 目标需要 Android SDK（`ANDROID_HOME` 或 `local.properties`）。

```bash
./gradlew :nexioui:jvmTest                 # JVM 编译 + 测试
./gradlew :nexioui:compileKotlinIosSimulatorArm64   # iOS 编译（需 macOS）
./gradlew :nexioui:publishToMavenLocal     # 本地发布，验证产物
```

发布：仓库打 tag 后由 JitPack 构建，坐标为 `com.github.HaoZai000.NexioUI:nexioui:<tag>`。

## 第三方与许可

本项目以 [Apache License 2.0](LICENSE) 开源。部分控件派生自同样使用 Apache-2.0 的开源项目，另有少量视觉参数参考自 HyperOS / COUI，详见 [NOTICE](NOTICE)。使用或再分发前请一并阅读。
