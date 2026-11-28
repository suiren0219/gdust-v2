# Nexio 集成说明

本目录是 NexioSchedule `feat/island-auth-local-mcp` 的静态整合副本，保留跨端 standalone Gradle 模块和 Android、Desktop、iOS、Harmony/OHOS 平台壳。它不是 gdust 主 App 的存储替换，当前也没有接入主 App 导航或迁移主 App 的 JSON/Preferences 数据。

## 来源与许可

- NexioSchedule：`cfdaedbae2359e9249d1415e1e5d963c66c66cf1`，对应隔离仓 `refs/source/nexio/feat-island-auth-local-mcp`。
- SoftGlass：tag `0.1.3`，commit `34011b51282307db77f8153b7ef1ec597cd284c9`，源码位于 `third_party/softglass/`。
- NexioUI：tag `0.1.1`，commit `499fc1e318cd5fc82aa5709b9ef42655b547d549`，源码位于 `third_party/nexioui/`。
- gdust-app 兼容来源：`68a638da6dc69f06be3782c0835b116a69a87832`，兼容代码位于 `adapters/gdust-compat/`，默认不参与构建。

根工程和兼容模块保留 AGPL-3.0 许可文件；SoftGlass 与 NexioUI 的 Apache-2.0、NOTICE 及其上游归属文件随 `third_party/` 保留。引入或发布时仍需核对各平台第三方二进制和许可证清单。

## 独立构建入口

本目录及 `third_party/` 下的 `.github/workflows/` 是来源工程保留的工作流；GitHub Actions 只发现仓库根目录的工作流，因此这些嵌套工作流不会自动执行。需要启用时，应在根目录新增入口并设置对应工作目录。

以下命令从本目录执行，需要配置 Android SDK 和 Gradle 所需的 JDK；本次整合仅完成静态核查，未运行 Nexio 构建或安装依赖。Windows 使用 `gradlew.bat`。

```text
./gradlew :core:jvmTest
./gradlew :ui-shared:compileKotlinJvm
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:packageDistributionForCurrentOS
```

跨平台目标按需显式开启：

```text
./gradlew -Pnexio.ios=true :ui-shared:compileKotlinIosSimulatorArm64
```

`settings.gradle.kts` 强制从本目录的 `third_party/softglass` 和 `third_party/nexioui` 复合构建解析源码，不读取工作区同级目录。两份 vendor 工程的 iOS target 默认关闭；宿主启用 `-Pnexio.ios=true` 时再同步开启。

Harmony/OHOS 使用 `ohos/` 的独立构建线，需要本地 DevEco/CMake/HAR 工具链。被排除的 `libsqlite3.a` 可由保留的 `sqlite3.c`、`sqlite3.h` 和 `ohos/third_party/sqlite/build-integration.sh` 重建；脚本不在本次整合中执行。

可选加载校园兼容模块：

```text
./gradlew -Pnexio.gdustCompat=true :gdust-compat:jvmTest
./gradlew -Pnexio.gdustCompat=true :gdust-compat:assemble
```

该模块保存 gdust-app 的 CAS/门户/水电客户端、待办存储、平台桥接口和连堂合并回归测试，但不覆盖 Nexio 新实现，不连接当前宿主存储，也不自动替换校园入口。

## 安全与边界

- 上游 Harmony 签名配置已替换为无签名 profile；证书、私钥、密码和本机路径不纳入整合目录。
- Android 可选 MCP 测试签名只从外部环境或 Gradle properties 读取，不在源码写入默认密码。
- 上游测试中的私人备份路径已改为 `NEXIO_TEST_BACKUP` / `NEXIO_TEST_ORPHAN_BACKUP` 或 `build/test-fixtures/` 相对路径。
- `core/src/jvmTest/resources/` 的两份备份 fixture 已改用确定性合成字段，保留原 JSON 结构、引用与测试场景；对应测试字符串预期已同步。
- `core/src/commonMain/sqldelight/databases/1.db` 仅为 SQLDelight schema 基线；静态检查确认其表均为 0 行，不含用户课表。
- `harmonyApp/entry/src/main/resources/rawfile/composeResources/` 的 39 份生成资源副本与 `ohos/third_party/sqlite/lib/` 生成静态库已从导入中排除；资源由 `:ohosApp:syncComposeResourcesToHarmony` 重建，SQLite 由上述脚本重建。`.gitignore` 已覆盖这些生成物。
- 保留的 AAR、HAR、Gradle Wrapper JAR、学校索引 PB 是上游明确依赖或测试输入，SHA-256 已记录到导入清单；它们不是本次构建输出。Wearable AAR 未附许可全文，发布前需核对其再分发条件。
- 历史来源的 `refs/source/*` 和 `source/*` 引用只用于本地复现与审计，不能作为公开发布内容；不使用 `git push --mirror`、`git push --all` 或通配来源引用的 refspec。合入目标分支的祖先对象仍会随该分支传输，定向推送不能代替历史内容核查。

## 与 gdust 主 App 的后续工作

主 App 仍使用自己的 JSON/Preferences 数据模型。本目录没有执行 SQLite 迁移、数据合并或主宿主文件修改。后续若统一存储，需要单独设计版本化迁移，覆盖课表来源、单双周、单日调课、作息配置、ID 冲突、备份往返和失败回滚；迁移实施后应安排构建与设备验收。
