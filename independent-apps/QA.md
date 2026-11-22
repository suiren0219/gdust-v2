# 两个独立 App 验收记录

日期：2026-11-17。

## 构建和测试

在独立工作树 `feat/standalone-learning-power`、JDK 21、Gradle 9.7.1、Android SDK 37 下运行：

```text
:chaoxing-core:test :power-core:test
:learning-app:testDebugUnitTest :power-app:testDebugUnitTest
:learning-app:assembleDebug :power-app:assembleDebug
:learning-app:assembleRelease :power-app:assembleRelease
```

| 范围 | 测试数 | 结果 |
|---|---:|---|
| 学习通核心 | 74 | 全部通过 |
| 电量核心 | 60 | 全部通过 |
| 学习助手 Android 适配 | 29 | 全部通过 |
| 电量 Android 适配 | 31 | 全部通过 |
| 合计 | 194 | 0 failure / 0 error / 0 skipped |

Debug、R8 Release、lintVital 均通过。电量初次编译发现字符串模板紧接中文时缺花括号的问题，已修复并重建通过。

`-PstandaloneApp=learning projects` 仅配置 `learning-app/chaoxing-core/ksu-ui`；`-PstandaloneApp=power projects` 仅配置 `power-app/power-core/ksu-ui`。核心源码副本与解耦工作区提取时的 SHA-256 清单一致。

## APK 隔离与签名

| 项目 | 学习助手 | 宿舍电量 |
|---|---|---|
| 安装标识 | `cn.edu.gdust.suiren.learning` | `cn.edu.gdust.suiren.power` |
| 签名预览包 | `dist/learning-assistant-0.1.0-preview.apk` | `dist/dorm-power-0.1.0-preview.apk` |
| 精确大小 | 2,662,994 bytes | 3,097,885 bytes |
| SHA-256 | `ad94e97904fd9c1a2886fe2f8fcf370d41b9e99c4821310a358e96d976f3b0d6` | `619a4c34612886029fa056ac3c968607736879d5d3844c5900d56ce5e1368a73` |

- 用本机 Android 开发测试证书对精简 Release 签名；`apksigner verify --verbose` v3 签名通过，`zipalign -c 4` 通过。未使用或复制项目正式发布密钥。
- `aapt` 检查包名正确，两个包可以同时安装。
- Dex 检查：学习助手无电量核心、宿舍宿主及 WorkManager；电量包无学习通核心及学习宿主；均无原广科 App 包、合成预览 Activity 或临时测试入口。
- 学习助手仅声明网络相关权限；电量额外有通知及 WorkManager 合并权限。均无相机、勿扰策略或精确闹钟权限。

## 设备与交互

在 API 35 `medium_phone` 上安装两份最终签名 Release 包并冷启动通过，未发现崩溃：

- 学习助手：未登录首页、作业/课程/设置底栏切换、主题切换；深色、关闭动态配色和 AMOLED 选项在重启后保留。
- 电量：独立启动、未知读数与加载反馈。设备出现已有绑定后没有编辑或清除绑定；本轮不据此宣称真实电量查询成功。
- 前一轮 Debug 设备检查已覆盖未绑定页、账号与学校代码输入、查询和确认按钮布局，未输入或未选择房间时确认绑定不可用。

在独立只读 API 36 `farm_api36` 的 debug 合成预览中检查：

- 学习助手部分成功状态、逐课错误和重试入口；“待交/已交”筛选与数量和内容一致。
- 课程页可展示已知任务点百分比与完成数，未知进度独立提示；深色 AMOLED、1.3 倍字号文字清晰。
- 电量首页合成余额、统计与估算；历史 1/7/30 天入口、余额曲线、横纵轴刻度、充值变化说明及按日记录。
- 电量历史在深色、1.3 倍字号下可读；1600×1200、240dpi 宽屏会切换为侧栏，图表和列表保持可读。
- 所有预览均明确标记“合成预览”，不请求网络、不写正式数据。

截图保存在当前主工作区的 `build/issue-verification/standalone-delivery/`，交付项目的 `qa/` 副本中仅保留合成或无账号页面。截图和构建日志不入库。

## 未完成在线验证的范围

- 本轮未提交真实学习通账号密码，没有取得新的有效学习通业务会话；课程/作业真实页面适配仍需用户登录后复测。
- 本轮未使用真实电费账号发起房间查询、换绑、充值或其他业务写操作；有效绑定下的真实余额与长期后台预警仍需复测。
- WorkManager 受系统省电和网络限制，测试证明策略、持久化及宿主调用，不能保证所有厂商后台均准点执行。
- Preview 使用测试证书，正式发布前应固定专用证书；换证书不能直接覆盖此版本。

原工作区、模块解耦工作区和已有 PR 未被提交、推送或合并。本次源码只写入独立工作树，两个新 App 暂未提交。
