# power-core

独立的 Kotlin/JVM 电费模块，包名 `com.menglimi.power`。支持完美校园房间与电量查询、采样策略、历史聚合、用量估算、低电量与掉电预警决策，不依赖学校登录或 Android。

- 只查询：`PowerApi.rooms(...)`、`PowerApi.detail(PowerBinding(...))`。
- 监控：为 `PowerMonitor` 提供自己的 `PowerMonitorStore`，由宿主定时调用 `query(...)`，通过预警回调发送通知。
- 历史：`PowerHistory.group(...)`、`PowerQueryPolicy.estimate(...)`。

存储、后台调度、通知和界面由新 App 提供。监控的并发与绑定隔离契约见 [监控实现](src/main/kotlin/com/menglimi/power/PowerMonitor.kt)。

在仓库根目录运行：

```bash
gradle -p legacy/kedian/standalone :power-core:test :power-core:jar
gradle -p legacy/kedian/examples/power-monitor run
```

[最小接入示例](../legacy/kedian/examples/power-monitor/src/main/kotlin/example/power/Main.kt) 默认离线运行；其中的 `MemoryPowerStore` 展示完整存储接口，正式应用替换为持久化实现即可。模块及源码复用沿用仓库的 [LICENSE](../LICENSE) 与 [NOTICE](../NOTICE.md)。
