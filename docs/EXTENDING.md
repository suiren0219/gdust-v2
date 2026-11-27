# 开发指南

## 构建与测试

准备 JDK 21、Gradle 9.7.1 和 Android SDK Platform 37。将 SDK 路径配置在 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir`。本仓库没有 Gradle Wrapper；将 `GRADLE_HOME` 设为 Gradle 安装目录的绝对路径，然后在仓库根目录用 PowerShell 按绝对路径调用 `gradle.bat`：

```powershell
$gradle = Join-Path $env:GRADLE_HOME 'bin\gradle.bat'
& $gradle :app:testProductionDebugUnitTest :app:assembleProductionDebug
& $gradle :app:assembleProductionRelease
```

- 构建配置与版本号见 [app/build.gradle.kts](../app/build.gradle.kts)。主包支持 Android 13（API 33）及以上；源码中的候选版本为 `2.0.0-alpha.26`（`versionCode` 26），不代表 APK 已构建、已签名或已发布。
- `productionDebug` 包名带 `.mcptest` 后缀。APK 位于 `app/build/outputs/apk/production/debug/app-production-debug.apk`，单元测试报告位于 `app/build/reports/tests/testProductionDebugUnitTest/index.html`。
- `previewDebug` 用于隔离的离线预览，可用 `:app:assemblePreviewDebug` 单独构建；`previewRelease` 已禁用，不产出 Release APK。
- `productionRelease` 启用 R8 和资源压缩。正式签名由 Git 忽略的 `signing/gdust-v2-release.jks` 与 `signing/keystore.properties` 提供，后者配置 `storePassword`、`keyAlias`、`keyPassword`。签名配置有效时产物为 `app/build/outputs/apk/production/release/app-production-release.apk`；未提供正式签名配置时为同目录下的 `app-production-release-unsigned.apk`，不能作为安装附件发布。

主工程与[独立应用](../independent-apps/README.md)中的学习助手共用 `chaoxing-core/build/`，相关 Gradle 调用须顺序执行，不要并行构建。

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
- 每次更新都要核对并同步维护[功能与接口清单](FEATURES_AND_APIS.md)，与代码放在同一提交或 PR。同步更新功能状态、未完成项、计划、接口及源码依据；无内容影响时，在清单维护记录中说明已核对及原因。具体要求见[仓库规则](../AGENTS.md)，提交 PR 时完成清单检查项。
- 登录、系统授权与桌面小部件等交互需要设备验证。并行工作时先核对工作树、设备分配与前台应用包名。[协作约定](../COORDINATION.md)
- 测试用例使用脱敏样本。签名、账号、Cookie、HAR 和本地配置留在本机。[忽略规则](../.gitignore)

## 历史资料

早期教务与学习通研究记录已留存在 Git 快照中。其中工作区路径、接口观测和发布状态属于当时的记录。[旧扩展记录](https://github.com/suiren0219/gdust-v2/blob/66f6b3f207abeaad2175abfc7a0e7116af324600/docs/EXTENDING.md)
