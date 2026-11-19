# 开发指南

## 构建与测试

准备 JDK 21、Gradle 9.7.1 和 Android SDK Platform 37。在根目录运行下列命令。将 SDK 路径配置在 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir`。

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

- 构建配置与版本号见 [app/build.gradle.kts](../app/build.gradle.kts)。Android 13（API 33）起可运行。当前 Debug 包使用 `.research` 后缀。
- Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，单元测试报告位于 `app/build/reports/tests/testDebugUnitTest/index.html`。
- `gradle :app:assembleRelease` 启用 R8 和资源压缩。如需本地签名，在 `signing/` 下提供 `gdust-v2-release.jks` 和 `keystore.properties`，后者配置 `storePassword`、`keyAlias`、`keyPassword`。Gradle 会自动读取这两份文件。

## 源码入口

| 改动 | 入口 |
| --- | --- |
| 应用初始化、路由与设置 | `App.kt`、`MainActivity.kt`、`ui/AppRoot.kt`、`ui/screens/SettingsScreen.kt` |
| 数据与本地存储 | `data/Repository.kt` 中的 `Repository` 和 `Store`，接口位于 `data/` |
| 教务与学习通 | `data/TeachingSsoClient.kt`、`data/TeachingGradeApi.kt`、`data/ChxCourseApi.kt`、`ui/screens/ChxWebActivity.kt` |
| 电量采样、课程与待办提醒 | `work/PowerWorker.kt`、`work/LessonReminderAlarms.kt`、`work/TodoReminder.kt` |
| 桌面小部件 | `widget/GdustWidgets.kt`、`widget/WidgetActions.kt`、`widget/WidgetDataSource.kt` |

上表路径相对于 `app/src/main/java/com/menglimi/gdust/v2/`。JUnit 测试使用对应的 `app/src/test/java/` 包结构。

## 贡献与验收

- 功能改动从目标基线开分支。PR 说明包含行为变化、验证结果和仍需设备确认的事项。当前排期以 GitHub Issues 和 PR 为准。[开放 PR](https://github.com/suiren0219/gdust-v2/pulls)
- 登录、系统授权与桌面小部件等交互需要设备验证。并行工作时先核对工作树、设备分配与前台应用包名。[协作约定](../COORDINATION.md)
- 测试用例使用脱敏样本。签名、账号、Cookie、HAR 和本地配置留在本机。[忽略规则](../.gitignore)

## 历史资料

早期教务与学习通研究记录已留存在 Git 快照中。其中工作区路径、接口观测和发布状态属于当时的记录。[旧扩展记录](https://github.com/suiren0219/gdust-v2/blob/66f6b3f207abeaad2175abfc7a0e7116af324600/docs/EXTENDING.md)
