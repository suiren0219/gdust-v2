# 独立应用与通知修复验收

## 变更

- 学习助手与宿舍电量是可独立构建和安装的两个应用，分别依赖 chaoxing-core 与 power-core。
- 两款的关于页包含「穗稔 · suiren0219」、GitHub 项目和发布入口、按应用附件筛选的更新检查。
- 两款保留 cn.edu.gdust.suiren.learning / cn.edu.gdust.suiren.power 安装标识；支持桌面快捷方式名称与图标自定义，默认图标与主项目一致。
- 电量预警渠道改为启动时创建，设置中增加测试通知、渠道状态提示与系统渠道设置入口。
- 首次允许通知，或从系统设置返回且通知从关闭变为开启时，复查最近 15 分钟的有效缓存读数，不额外请求电费服务。
- 缓存预警发送失败不消耗冷却；发送成功以实际复查时间记录冷却。测试通知使用独立 ID，不读真实余额或更改预警冷却。

## 自动化验证

使用 JDK 21、Gradle 9.7.1、SDK 37 执行：

```text
:ksu-ui:testDebugUnitTest :chaoxing-core:test :power-core:test
:learning-app:testDebugUnitTest :power-app:testDebugUnitTest
:power-app:assembleDebug :power-app:assembleRelease :learning-app:assembleRelease
```

24 组、204 项测试通过，0 failure / 0 error / 0 skipped。Debug、R8 Release 与 lintVital 均通过。

新增缓存预警测试覆盖：权限恢复后重试不触发网络或新增采样、失败不写冷却、成功后防重复、过期/未来/其他宿舍读数拒绝、取消不记成功、按当前时间判定冷却。

module-sources.json 记录最初提取时的核心源码校验值。后续有意修改 PowerMonitor.kt 及 PowerMonitorTest.kt 以实现并验证缓存预警复查，不再声称这两文件与提取时字节一致。

## 设备验证

在 Android API 36 模拟器中安装最终 Debug 包，允许 POST_NOTIFICATIONS 后，通过设置页「测试」触发通知。

- 页面显示「测试通知已发送，请查看系统通知栏」。
- Android NotificationManager 活动记录存在应用通知 ID 7102，渠道 dorm_power_alerts_v1、importance=4。
- 该证据为应用自身 notify 调用的实际系统记录，不以 shell 生成的通知作为应用投递成功证据。
- 之前验证过两款旧预览包覆盖升级、署名和包名、GitHub 缺少对应附件的提示、桌面改名/选图/恢复默认。

## 限制

- 未使用真实电费账号验证余额触发、长期 WorkManager 执行或厂商省电策略；系统可能延后后台任务。
- 保留既有渠道 ID，用户已经选择的静音、关闭或重要性不会被应用强行重置。可从「管理」进入系统设置调整；新渠道默认为高重要性。
- 学习助手目前没有作业系统通知/到期提醒能力；本次通知修复针对宿舍电量预警。
- 无效或超过 15 分钟的旧缓存不会立即提醒，需要取得新读数后判断。
- 本次创建 PR，不代表两个独立 App 已在 GitHub Release 正式发布；无对应附件时更新检查会明确说明。
- APK 与本地签名证书不纳入 Git。预览包使用开发测试证书，正式发布应固定专用证书。
