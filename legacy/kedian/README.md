# legacy/kedian

这是从私有仓 `suiren0219/kedian` 的提交 `6ab932d0b17266cfddb5b240b623823d127bdbe0` 保留的历史发行形态，仅用于本地整合与后续审查，不是主 App 的替代实现。

## 保留范围

- `campus-lite/`：旧的学习通作业 + 宿舍电量合体 Android App。
  - applicationId：`com.menglimi.kedian`
  - 与主 App 及当前 `independent-apps/learning-app`、`power-app` 的包名不同，不能作为覆盖升级包。
- `standalone/`：只加载共享 `chaoxing-core`、`power-core` 的 JVM 模块构建入口。
- `examples/chaoxing-work/`、`examples/power-monitor/`：使用合成数据的离线 CLI 示例；默认不访问真实服务。`power-monitor` 只有显式传入 `--live` 并提供环境变量时才发起实时查询。
- `LICENSE`、`NOTICE.md`：保留旧仓版权和第三方许可声明。

这里不复制 core 源码。各构建入口均引用主仓根目录的 `chaoxing-core/` 与 `power-core/`；本目录相对路径已经按 `legacy/kedian/` 位置调整。

## 后台行为

合体 App 保留旧版可选的 WorkManager 周期巡检：新安装或缺少有效配置时默认是 `MANUAL`，不会自动联网；在设置中选择“均衡”“低电量关注”或“自定间隔”后，才会排队后台查询。该行为只属于 legacy 合体 App，不改变主 App 或新版独立电量 App 对“待更新提示”的语义。系统省电策略可能延后任务，通知需获得用户授权。

## 构建入口

本轮只做文件导入、路径适配和静态检查，未运行 Gradle。构建使用已安装的 Gradle（旧工程验证基线为 9.7.1）、JDK 21，编译目标 Java 17；Android 合体 App 另需 Android SDK 37。旧仓没有随附 Gradle wrapper。

在主仓根目录依次运行以下命令；两个 CLI 默认使用合成数据，不传 `--live`：

```bash
gradle -p legacy/kedian/standalone check
gradle -p legacy/kedian/examples/chaoxing-work run
gradle -p legacy/kedian/examples/power-monitor run
gradle -p legacy/kedian/campus-lite :app:testDebugUnitTest :app:assembleDebug
```

这些入口共享根 core 的源码和构建目录，验证时依次执行，避免与主工程的 core 构建并发。构建结果不代表真实登录、设备后台调度或旧包覆盖升级已验证。

静态检查已确认 6 条 core 路径均解析到主仓根目录、28 个导入类型存在、17 个所用模型/服务构造参数顺序保留且新增参数提供默认值，两个 CLI 无需修改业务源码。`PowerMonitorConfig` 和缺失配置的策略回退沿用当前 core 的 `MANUAL`；设置页自行说明 legacy 的可选联网巡检，不使用新版 core 的“待更新提示”文案。源码导入时保留了合成测试与预览数据；没有导入真实会话、签名或二进制附件。

不要把真实账号、Cookie、测试数据、签名文件或构建产物放入此目录。

## 来源与许可

- `kedian` 来源：`refs/source/kedian/main`，指向上述提交。
- `gdust-schedule` 来源：`refs/source/gdust-schedule/main`；课表线已在主仓历史中，不在这里重复拷贝。
- 本目录按旧仓保留的 GNU AGPL-3.0 与 `NOTICE.md` 说明处理；发布前仍需由主代理复核主仓整体许可、第三方归属及私有历史是否适合公开。
