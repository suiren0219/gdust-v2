# 广科课程表：功能、待完善项与接口清单

核查日期：2026-11-28。本文记录发布整合工作树的代码状态；不等同于 main 已合并、Release 已签名或 APK 已发布。

维护记录（2026-11-28）：基线为远端 `main` 的 `3a7f60e8967d745a741a80e251f115f0169fbad0`。在隔离发布工作树整合主工程快照、MCP/课表分享和电量更新提示，同步 PR #17 的维护文档，并核对 PR #18 的风控增量。本次设备运行验证尚未执行；构建、测试、签名和发布状态分项记录。

## 1. 核查范围与状态口径

| 项目 | 本次依据 |
| --- | --- |
| 仓库 | `suiren0219/gdust-v2` |
| 远端基线 | `main` / `3a7f60e8967d745a741a80e251f115f0169fbad0` |
| 发布分支 | `release/all-changes-alpha26`；当前合并结果尚未上传或进入 `main` |
| 快照来源 | 主工程 `01ab860`、MCP `92f2380`、电量 `3e60e09`；本次核对 PR #17 维护规则及 PR #18 风控增量 |
| 版本候选 | 主应用 `2.0.0-alpha.26` / versionCode 26；学习助手 `0.1.13` / versionCode 14；宿舍电量 `0.1.9` / versionCode 10；候选值不代表 Release 已发布 |
| 构建配置 | 最低 API 33、目标 API 35、SDK 37；JDK 21、Gradle 9.7.1 |
| 模块边界 | 根工程含 `:app`、`:nexio-ui`、`:chaoxing-core`；独立学习与电量应用由 `independent-apps/` 分别构建 |
| 验证方法 | 单元测试、Release 构建、签名校验与设备验证分开记录；未连接校园或 AI 真实服务，未在真机上完成验证码恢复 |

状态口径：**已实现**表示有实现与可达调用；**部分实现**表示存在明确功能缺口；**待验证**表示仍需运行时/真实服务证据；**建议计划**不代表用户承诺的排期。源码实现、测试通过、设备验证和 APK 发布分别陈述。

源码链接指向当前候选树的相应文件。历史维护记录见第 7 节。

## 2. 已实现与部分实现功能

| 编号 | 功能 | 状态 | 当前实现与入口 | 边界与条件 | 代码依据 |
| --- | --- | --- | --- | --- | --- |
| F01 | 学校登录与服务状态 | 已实现 | 统一网页登录、兼容账号验证码、钉钉扫码、免登录预览；门户、教务、学习通分别显示连接状态，支持逐项验证和补连 | 预览或本机缓存不代表认证成功；状态卡进入时不自动探活，验证由按钮触发 | [LoginScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/LoginScreen.kt)；[ServiceStatusCard.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ServiceStatusCard.kt)；[SettingsScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/SettingsScreen.kt) |
| F02 | 整学期课表与校历 | 已实现 | 主导航课表；整学期导入、切周/选周、回本周、切学期、课程详情，保留实际周次、单双周和调课信息 | 依赖有效会话和导入数据；门户快照与正方整学期课表分开处理；独立课表允许另选、导入或分享，不覆盖学校同步数据 | [ScheduleScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/schedule/ScheduleScreen.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| F03 | | 本机课表自动同步 | 已实现 | 设置开启后，以四小时为目标周期维护学校会话并同步，复用电量任务唤醒 | Android 调度不保证精确到点；明确认证失效仍需重新登录 | [ScheduleSyncWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/ScheduleSyncWorker.kt)；[PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt)；[ScheduleSyncPolicy.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ScheduleSyncPolicy.kt) |
| F04 | 成绩与生日资料读取 | 已实现 | 成绩页按学年学期查询成绩、学分、绩点及课程详情；支持从教务个人资料读取生日月日并用于生日展示 | 需要有效教务会话；生日资料只读，不提交修改；真实数据未在本轮验证 | [GradesScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/GradesScreen.kt)；[TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt)；[AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt) |
| F05 | 今日概览 | 已实现 | 今日课程、当前/下一课、时间线、待办/逾期数量、公告未读、服务快捷入口及电量绑定状态 | 内容取决于已导入或同步的数据 | [TodayScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodayScreen.kt) |
| F06 | 宿舍电量查询与历史 | 已实现 | 绑定房间、刷新余量、近期用量、本机趋势、日历历史、可用天数估算；采样保留 30 天；绑定标识变化时清除旧历史及采集状态 | 当前通过清除防止旧历史沿用，尚不支持按宿舍分别保留历史，见 G04 | [PowerScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/PowerScreen.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| F07 | 电量后台采集与预警 | 已实现 | 后台查询、缓存复用、低电量及异常掉电预警；同类预警冷却 12 小时 | 依赖绑定、配置和系统调度；查询预设缺省回退为 `BALANCED`，实际调度仍受绑定等条件约束 | [PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt)；[PowerQueryPolicy.kt](../app/src/main/java/com/menglimi/gdust/v2/data/PowerQueryPolicy.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| F08 | 手动待办管理 | 部分实现 | 新增文本、自动分类、完成/恢复、删除和清理；考试优先，按课程及截止时间整理 | 手动添加入口未传截止时间，见 G03 | [TodoScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoScreen.kt)；[TodoModel.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TodoModel.kt) |
| F09 | 学习通课程、进度及作业同步 | 已实现；真实服务端恢复待验证 | 请求闸门识别 9010/验证码和 HTTP 429；暂停后阻止排队请求并保留旧结果，用户在官方网页人工验证后重试原受阻业务 | Cookie 写入失败不视作成功；同账号取消或迟到响应不应丢失暂停；更换账号后隔离旧请求和暂停状态；只有原业务重新解析成功才恢复；尚未对真实账号做验证码恢复端到端验证 | [ChxRequestGate.kt](../chaoxing-core/src/main/kotlin/com/menglimi/chaoxing/ChxRequestGate.kt)；[ChxRequestControl.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxRequestControl.kt)；[ChxWebActivity.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxWebActivity.kt) |
| F10 | 学习通通知转待办 | 已实现 | 通知监听、符合条件的消息提取及去重，配有说明和开关页 | 需要通知使用权和应用内开关，不获取全部历史通知 | [ChxNotificationScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxNotificationScreen.kt)；[ChxNotificationListener.kt](../app/src/main/java/com/menglimi/gdust/v2/service/ChxNotificationListener.kt) |
| F11 | 课程/待办提醒与勿扰 | 部分实现 | 课前、课中、次日课程、待办到期提醒，上课勿扰及超级岛设置 | 需要对应权限；超级岛兼容性未实测；待办目标绑定和统一落库重排见 G01/G02 | [ReminderScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ReminderScreen.kt)；[LessonReminderAlarms.kt](../app/src/main/java/com/menglimi/gdust/v2/work/LessonReminderAlarms.kt)；[ClassQuietMode.kt](../app/src/main/java/com/menglimi/gdust/v2/work/ClassQuietMode.kt) |
| F12 | 桌面小部件 | 已实现 | 11 款：课表 4、电量 4、待办 3；预览、请求固定、跳转业务页；待办一键完成 | 课表/电量各为 4×2、2×2、1×2、1×4；通用刷新主要重读缓存 | [WidgetPinner.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetPinner.kt)；[WidgetActions.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetActions.kt)；[WidgetRefreshWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetRefreshWorker.kt) |
| F13 | 外观与桌面快捷方式 | 已实现 | 接入 NexioUI、跟随系统深浅色、课表显示参数、自定义桌面快捷方式名称和图片 | 快捷方式不改变安装包图标；残留壁纸存储字段不等于可用配置入口 | [Themes.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/Themes.kt)；[DesktopShortcut.kt](../app/src/main/java/com/menglimi/gdust/v2/launcher/DesktopShortcut.kt)；[app/build.gradle.kts](../app/build.gradle.kts) |
| F14 | JSON 备份与恢复 | 已实现 | 设置中导入/导出课表、校历、待办、电量采样、部分偏好及宿舍绑定 | 非全量偏好备份；不含学校登录凭证或 AI 配置，分享前需检查个人数据 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| F15 | AI 学习助手与对话历史 | 已实现 | 多轮对话、快捷问题、课程/待办上下文开关和本机历史恢复 | 用户自配兼容接口；上下文会发送到该接口；离线预览明确禁用请求和配置保存 | [AiChatScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/AiChatScreen.kt)；[AiChatHistory.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/AiChatHistory.kt)；[AiServiceSettingsScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/AiServiceSettingsScreen.kt) |
| F16 | 公告与 AI 总结 | 已实现 | 门户、教务处、官网切源与刷新，本机已读、浏览器原文及 AI 总结 | 总结需用户配置 AI；不同公告来源的认证条件见 A08–A10；标已读不证明浏览器加载成功 | [NoticeScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/NoticeScreen.kt)；[GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| F17 | 本机/云端 MCP 与课表分享 | 已实现 | 用户显式启停本机只读 MCP；可选云端 MCP 配置和独立课表分享 | 本机服务由用户设置控制；云端功能需要单独配置；预览模式不得启动服务 | [LocalMcpService.kt](../app/src/main/java/com/menglimi/gdust/v2/service/LocalMcpService.kt)；[CloudMcpRepository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/CloudMcpRepository.kt)；[McpScreens.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/McpScreens.kt) |
| F18 | 独立宿舍电量更新提示节奏 | 已实现 | 手动/均衡/低电量/自定间隔只标记缓存新鲜度；开机单次查询为独立默认关闭开关 | 提示本身不联网；开机任务为一次性任务 | [PowerQueryPolicy.kt](../power-core/src/main/kotlin/com/menglimi/power/PowerQueryPolicy.kt)；[PowerSettingsScreen.kt](../independent-apps/power-app/src/main/java/com/menglimi/dormpower/ui/PowerSettingsScreen.kt) |
| F19 | 独立宿舍电量查询、历史与预警 | 已实现 | 绑定已有房间、读取余额/趋势/历史、本机账本、低电量及掉电预警 | 余额上升可能是充值；估算仅依据本机样本；换绑时清旧数据，不支持按多宿舍分别保留 | [PowerScreen.kt](../independent-apps/power-app/src/main/java/com/menglimi/dormpower/ui/PowerScreen.kt)；[PowerLedger.kt](../independent-apps/power-app/src/main/java/com/menglimi/dormpower/power/PowerLedger.kt) |

### 文档与实现差异

| 现有描述 | 核对结果 | 后续维护要求 |
| --- | --- | --- |
| README：三类小部件各三种尺寸 | 当前为 4＋4＋3，共 11 款 | 调整相关描述时按注册清单核对数量 |
| README：手动待办设置到期提醒 | 提醒机制存在，手动添加入口仍缺截止时间输入 | 补齐 G03 后再更新为完整设时能力 |
| README：电量后台默认关闭 | 查询预设缺省回退为 `BALANCED`，调度还有绑定等条件 | 默认值说明需与配置及调度条件同时核对 |
| README：电量小部件刷新按钮主动查询 | 本轮未核实该入口；通用刷新主要读取缓存 | 核实具体 action 的发送链后再声称主动联网 |

## 3. 未实现、未完善与待核实项目

| 编号 | 功能/问题 | 状态 | 当前边界与影响 | 代码依据 | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| G01 | 待办闹钟绑定具体条目 ID | 未实现的子能力 | 广播只携带 action，触发时重新选择最早截止的未完成项，存在在 B 的时点再次提醒 A 的风险 | [LessonReminderAlarms.kt](../app/src/main/java/com/menglimi/gdust/v2/work/LessonReminderAlarms.kt)；[TodoReminder.kt](../app/src/main/java/com/menglimi/gdust/v2/work/TodoReminder.kt) | P1 |
| G02 | 待办统一落库后即时重排 | 未实现的子能力 | `updateTodos` 只写 JSON，作业同步也经此入口；已有启动、小部件操作、提醒设置、广播和兜底重排，但未覆盖统一落库后的即时重排 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt)；[MainActivity.kt](../app/src/main/java/com/menglimi/gdust/v2/MainActivity.kt)；[WidgetActions.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetActions.kt) | P1 |
| G03 | 手动待办截止日期/时间输入与编辑 | 部分实现 | 模型有 `dueAt`，添加入口未传入，使用默认值 `0`；提醒说明超过该入口能力 | [TodoScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoScreen.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt)；[TodoReminderScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoReminderScreen.kt) | P2 |
| G04 | 按宿舍分别保留电量历史 | 可选增强，尚未实现 | account/code/verify 变化会清除样本、Worker 缓存及预警状态，避免直接沿用旧历史；尚无按宿舍分别保存与切换历史的能力 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt)；[换绑入口](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) | P3 |
| G05 | 门户失效后完整恢复/重登回归 | 待运行验证 | 已识别业务码 `116/117` 与 HTTP 401，且在普通 HTTP 错误处理前执行；已有 `500 + code=117` 测试用例，本轮未运行完整流程 | [SessionPolicy.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SessionPolicy.kt)；[GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt)；[SessionPolicyTest.kt](../app/src/test/java/com/menglimi/gdust/v2/data/SessionPolicyTest.kt) | P2 |
| G06 | 整合候选与最终发布范围核对 | 发布前核对 | 已纳入独立课表、MCP 和电量增量；尚需验证预览隔离、正式产物版本及上游实际合并范围 | 第 1 节源码基线；合并时核对对应提交差异 | 发布前核对 |
| G07 | 真实学习通风控与恢复端到端验证 | 待验证 | 9010/429由脱敏合成测试覆盖；人工验证码后服务端是否允许原请求恢复尚未实测 | [ChxRequestPauseRegressionTest.kt](../app/src/test/java/com/menglimi/gdust/v2/data/ChxRequestPauseRegressionTest.kt) | P1：取得授权后验证 |
| G08 | APK 签名兼容和安装升级验证 | 待最终构建核验 | 使用历史证书构建后仍需检查包名、versionCode、证书摘要和安装覆盖 | 发布构建与 `apksigner` 验证记录 | 发布前 |

### 旧问题核对结论

| 旧记录 | 候选树中的处理 | 代码依据 |
| --- | --- | --- |
| 单门学习通课程失败取消整批 | 已按课程隔离普通异常，成功结果继续合并，取消异常传播；失败区分认证和读取问题 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt)、[逐课读取](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| 任务点全完成后跳过作业 | 没有该预过滤，遍历全部课程读取作业并合并远端完成态 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| 作业绝对截止时间未解析 | 已读取未交作业详情；绝对时间优先，相对时间兜底，均未知为 `0`；普通详情错误可回退，认证异常作为该课程失败处理 | [详情读取](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt)、[时间选择](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| 换绑宿舍未清除旧历史 | 当前在绑定标识变化时清理历史及后台状态，不再列为直接串用缺陷 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| 风控响应后其它课程继续读取 | 共享请求闸门与合成用例阻止排队传输 | [ChxRequestControlTest.kt](../app/src/test/java/com/menglimi/gdust/v2/data/ChxRequestControlTest.kt) |
| Cookie 写回失败被当部分成功 | 固定类型异常保留暂停原因，避免缓存被误更新 | [ChxCookieWriteException.kt](../chaoxing-core/src/main/kotlin/com/menglimi/chaoxing/ChxCookieWriteException.kt) |
| 取消/迟到回调导致暂停恢复目标丢失 | 合成用例检查 owner 绑定、generation 与旧请求收尾 | [LearningControllerTest.kt](../independent-apps/learning-app/src/test/java/com/menglimi/study/learning/LearningControllerTest.kt) |

## 4. 计划及建议推进顺序

[项目进度](../PROGRESS.md)和[开发指南](EXTENDING.md)规定，排期以 GitHub Issues/PR 为准。此次未读取在线排期，不据此编造负责人或完成时间。独立学习、电量应用及 Xposed 模块不列为计划接回主 App。

| 顺序 | 建议事项 | 对应条目 | 验收方向 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 提醒携带待办 ID，并处理完成、删除、改时与过期 | G01 | 相邻截止事项各自提醒对应条目，完成或删除后不再提醒 | 建议计划 |
| 2 | 新增、编辑、完成、删除及同步落库后统一重排提醒 | G02 | 无需等待兜底任务即可更新下一条提醒 | 建议计划 |
| 3 | 增加手动截止时间输入、编辑和清除 | G03 | 时间能正确保存并进入提醒链 | 建议计划 |
| 4 | 运行门户失效后的集成与设备回归 | G05 | 已识别错误后的恢复或重登提示明确 | 建议计划 |
| 5 | 如需保留多宿舍历史，设计分宿舍存储与迁移 | G04 | 换绑清理策略和历史保留策略明确，无法确认归属的旧样本不乱归类 | 可选建议 |
| 6 | 发布前核对整合差异与预览隔离 | G06 | 清单、README、设置说明与最终发布基线一致 | 建议计划 |
| 7 | 用户人工验证后重读原受阻学习通业务 | G07 | 不重放其他课程、不自动处理验证码，记录真实服务端恢复结果 | 待授权与验证 |
| 8 | 核对新版证书及安装升级 | G08 | 校验包名、版本和证书一致；在隔离设备按条件验证覆盖 | 发布前核对 |

## 5. 当前调用的接口

以下“可达”指存在页面或后台任务到请求发送点的调用链，未进行真实接口验证。参数使用占位符。HTML、JSON、SSE 和网页入口分别注明。

### 5.1 主机

| 简称 | Base URL/主机 | 说明 |
| --- | --- | --- |
| CAS | `https://cas.gdust.edu.cn` | 学校统一认证 |
| 门户 | `https://portal.gdust.edu.cn` | 门户数据与公告 |
| 教务 | `http://172.16.254.1`、`http(s)://gkjx.gdust.edu.cn` 或保留转发前缀的 WebVPN base | 取决于当前教务会话，见 [TeachingEndpoints.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingEndpoints.kt) |
| M1 | `https://mooc1.chaoxing.com` | 学习通课程及作业页面 |
| M2 | `https://mooc2-ans.chaoxing.com` | 学习通首页、课程列表及任务点 |
| 电量 | `https://xqh5.17wanxiao.com` | 完美校园水电服务 |
| AI | 用户配置的 Base URL | 项目使用聊天补全兼容协议 |

### 5.2 学校认证与门户

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A01 | `GET CAS /cas-api/cas/loginCode` | 验证码 JSON | 展开兼容登录或刷新验证码 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A02 | `POST CAS /cas-api/cas/loginByAccount` | 账号、密码及验证码换 ticket，JSON | 用户主动提交表单 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A03 | `GET CAS /cas-api/sse/subscribe` | 扫码 SSE 事件流 | 用户进入扫码流程 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A04 | `GET 门户 /smart-admin-api/user/login?loginCode={ticket}&appId=portalRemote` | 一次性 ticket 换 TOKEN，JSON | 兼容登录成功后 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A05 | `GET 门户 /smart-admin-api/app/userInfo/addition` | 身份信息与会话验证，JSON | `TOKEN` 头；登录验证、启动或状态探活 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A06 | `GET 门户 /smart-admin-api/app/zf/get_school_calendar?jobNumber={学号}` | 校历 JSON | `TOKEN`；同步或本机缺少校历时 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A07 | `GET 门户 /smart-admin-api/app/zf/get_student_course?jobNumber={学号}&week={周次}&year={学年}&semester={学期}` | 门户课程快照 JSON | `TOKEN`；当前允许周次/学年/学期为空；已有正方整学期课表时不以快照覆盖 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| A08 | `GET 门户 /smart-admin-api/app/ding/get_announcement_items?jobNumber={学号}` | 门户公告列表 JSON | `TOKEN`；有门户会话时加载或刷新 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A09 | `GET 门户 /smart-admin-api/app/news/list?source=jiaowu` | 教务处公告列表 | 独立客户端不附 TOKEN/Cookie；切源或刷新 | [SchoolNoticeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SchoolNoticeApi.kt)；[PortalNews.kt](../app/src/main/java/com/menglimi/gdust/v2/data/PortalNews.kt) |
| A10 | `GET 门户 /smart-admin-api/app/news/list?source=home` | 官网新闻列表 | 当前实现附 TOKEN；切源或刷新 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |

### 5.3 正方教务

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A11 | `POST 教务 /kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151` | 学期课表 JSON，包含 `kbList` | 教务 Cookie；导入、同步、会话验证及启用后的四小时维护 | [TeachingSession.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingSession.kt) |
| A12 | `GET 教务 /cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default` | 成绩表单 HTML，读取 pkey 和学期选项 | 教务 Cookie；成绩查询 | [TeachingGradeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingGradeApi.kt) |
| A13 | `POST 教务 /cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005` | 成绩分页 JSON；每页 15 条，最多 100 页 | 教务 Cookie 与 pkey；成绩查询 | [TeachingGradeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingGradeApi.kt) |
| A14 | `GET 教务 {已认证落地页path}` | 从 HTML 发现只读个人信息菜单，清除落地页 query/fragment | 教务 Cookie；条件性自动或手动读取生日 | [TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt) |
| A15 | `GET 教务 /xsxxxggl/xsgrxxwh_cxXsgrxx.html?gnmkdm={菜单代码}` | 个人资料 HTML，提取生日月日 | 教务 Cookie；只读，不提交资料修改 | [TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt) |

### 5.4 学习通

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A16 | `GET M2 /mooc2-ans/visit/interaction` | 首页探活及课程发现前置 HTML | 超星 Cookie；验证连接、加载课程或同步作业 | [ChxCourseApi.kt](../chaoxing-core/src/main/kotlin/com/menglimi/chaoxing/ChxCourseApi.kt) |
| A17 | `POST M2 /mooc2-ans/visit/courselistdata` | 课程列表 HTML | 超星 Cookie；加载课程或同步作业 | [ChxCourseApi.kt](../chaoxing-core/src/main/kotlin/com/menglimi/chaoxing/ChxCourseApi.kt) |
| A18 | `GET M2 /mooc2-ans/mycourse/stu-job-info?clazzPersonStr={班级ID_个人ID列表}` | 任务点统计 JSON | 超星 Cookie；课程概览 | [ChxCourseApi.kt](../chaoxing-core/src/main/kotlin/com/menglimi/chaoxing/ChxCourseApi.kt) |
| A19 | `GET M1 /visit/stucoursemiddle?courseid={courseId}&clazzid={classId}&cpi={cpi}&ismooc2=1&v=2` 或课程提供的 `M2 /mooc2-ans/mycourse/stu?...` | 课程 HTML，读取签名字段，允许经校验的重定向 | 超星 Cookie；逐课同步 | [ChxApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt) |
| A20 | `GET M1 {前缀}/mooc2/work/list?courseId={courseId}&classId={classId}&cpi={cpi}&ut=s&t={t}&stuenc={enc}&enc={workEnc}` | 作业列表 HTML；前缀为空或 `/mooc-ans`；由 `moocDomain` 拼接，不接受 `.html` 后缀 | 超星 Cookie 与页面签名；手动作业同步 | [允许路径](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt)、[地址构造](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt) |
| A21 | `GET M1 {前缀}/mooc2/work/task?workId={workId}&answerId={answerId}&enc={enc}&{页面其余参数}` | 未交作业详情 HTML，提取绝对截止时间；前缀同上 | 超星 Cookie 与经校验的签名链接；同步时条件性读取 | [ChxApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |

### 5.5 完美校园电量、AI 与 MCP

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A22 | `POST 电量 /smartWaterAndElectricityService/SWAEServlet`，表单 `method=getbindroom` | 绑定房间；另含 `command=JBSWaterElecService`、`customercode` 和 JSON 字符串 `param` | account、customercode；绑定查询，无需门户登录 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt) |
| A23 | 同一 Servlet `POST`，表单 `method=h5_getstuindexpage` | 余量及用量详情；余额无单独 endpoint | account、customercode、roomverify；前台刷新及满足条件的后台采集 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt)；[PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt) |
| A24 | `POST {用户配置的 API 根路径}/chat/completions` | AI 对话、公告总结与合成连通测试，JSON | `Authorization: Bearer {key}`；用户主动发送、请求总结或测试；预览不发送 | [LlmApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/LlmApi.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| A25 | `GET {用户配置的 API 根路径}/models` | 读取可用模型 | 同 A24；设置中由用户显式查询；失败仍可手填 | [LlmApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/LlmApi.kt) |
| A26 | 本机回环 MCP 的 `POST /mcp` | MCP JSON-RPC，提供只读课表工具 | 用户显式开启；仅本机回环，预览不得启动 | [LocalMcpServer.kt](../app/src/main/java/com/menglimi/gdust/v2/service/LocalMcpServer.kt) |
| A27 | `POST {用户配置的 MCP URL}` | 用户选择的远程 MCP 操作 | 可选凭据发往用户配置服务；仅在设置/操作入口触发 | [CloudMcpRepository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/CloudMcpRepository.kt)；[CloudMcpClient.kt](../app/src/main/java/com/menglimi/gdust/v2/data/CloudMcpClient.kt) |

AI 地址会清理末尾斜杠并规范化根路径；无路径时默认 `/v1`，已包含 `/chat/completions` 或 `/models` 时先去掉该资源后缀。该行为不代表已验证所有服务商。

### 5.6 网页入口与外部跳转

| 方法/入口 | 用途与条件 | 代码依据 |
| --- | --- | --- |
| `GET CAS /cas/?service={urlencode(门户回调)}` | 学校统一登录；回调为门户 `/smart-admin-api/user/ssoLogin/?appId=portalRemote`，学校另附认证参数 | [SchoolLoginFlow.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SchoolLoginFlow.kt) |
| `GET CAS /lyuapServer/login?service={urlencode(目标服务)}` | 教务/学习通 SSO；目标为教务 `/sso/lyiotlogin` 或 `http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy` | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt) |
| `GET https://webvpn.gdust.edu.cn/{VPN认证前缀}/cas/?service={WebVPN回调}` | 用户选择 VPN 或对应会话续租时的网页登录 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt) |
| `GET https://passport2.chaoxing.com/login?fid=420&refer={学习通首页}` | 用户选择学习通账号登录 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt) |
| `GET CAS /cas/logout` | 显式退出学校网页登录 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt)；[ChxEntryScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxEntryScreen.kt) |
| `GET https://www.gdust.edu.cn/jwc/jiaowu/{日期}/{id}.html`、`/article/{目录编号}/{id}.html` 或公告 URL | 用户点击后外部浏览器打开原文；App 不显式转发门户 TOKEN | [PortalNews.kt](../app/src/main/java/com/menglimi/gdust/v2/data/PortalNews.kt)；[NoticeScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/NoticeScreen.kt) |
| `GET http://172.16.1.232/index` | 五育服务 WebView 入口 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt) |
| 项目 GitHub 页面、`https://www.gdust.edu.cn/` | 关于页外部链接 | [SettingSubPages.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/SettingSubPages.kt) |

网页内部表单提交、动态重定向及子资源未逐项展开，不将上述网页入口等同于 JSON API。

### 5.7 独立应用与保留定义

| 范围 | 结论 | 代码依据 |
| --- | --- | --- |
| 独立学习应用 | 使用同组学习通接口及条件性正文导航；账号登录为 `https://passport2.chaoxing.com/login?refer={学习通HOME}` | [LearningSession.kt](../independent-apps/learning-app/src/main/java/com/menglimi/study/learning/LearningSession.kt)；[ChxCourseNavigation.kt](../chaoxing-core/src/main/kotlin/com/menglimi/chaoxing/ChxCourseNavigation.kt) |
| 独立电量应用 | `power-core.PowerApi` 使用同一 SWAEServlet 和两个业务 method；后台管理及账本不能直接算为主 App 能力 | [PowerApi.kt](../power-core/src/main/kotlin/com/menglimi/power/PowerApi.kt)；[独立应用说明](../independent-apps/README.md) |
| 独立应用检查更新 | 用户点击后 `GET https://api.github.com/repos/suiren0219/gdust-v2/releases?per_page=100`，无 Authorization | [AppRelease.kt](../independent-apps/ksu-ui/src/main/java/com/menglimi/ksu/ui/AppRelease.kt)；[AboutProjectCard.kt](../independent-apps/ksu-ui/src/main/java/com/menglimi/ksu/ui/AboutProjectCard.kt) |
| 旧教务原生流程 | 保留 `GET {base}/sso/lyiotlogin?ticket={ticket}&service={service}` 和旧 GET 课表探活；未找到 `openSession/loginAndFetch/probe` 当前业务调用点 | [TeachingSsoClient.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingSsoClient.kt) |
| 新版超星 SSO 定义 | `https://gdkj.mh.chaoxing.com/sso/login/3rd` 的 `mhUrl` 仅找到定义 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt) |
| Debug 探测 | WebView 探测 Activity 可加载调试 URL，不计正常发布业务 | [WebViewProbeActivity.kt](../app/src/debug/java/com/menglimi/gdust/v2/debug/WebViewProbeActivity.kt) |
| 主 App 其他网络能力 | 本次没有访问校园壁纸或任何真实学校/第三方业务服务；静态清单不覆盖 WebView 全部子请求 | 以第 1 节基线及上述调用范围为限 |

接口编号用于清单定位，不代表唯一 URL 数量；同一 Servlet 可承载多个业务 method，学习通还有受校验的路径变体。

## 6. 验证范围

- 本轮单元测试、Release 构建、APK 签名及资产结果待最终运行后逐项填写。
- 未连接学校认证、门户、教务、学习通、电量或 AI 真实服务，未在真机/模拟器进行登录或风控恢复。
- 合成测试不等于服务端风控阈值或人工验证码恢复的端到端证据。
- 本地 keystore 与签名属性仅在被忽略的 `signing/` 下使用，不纳入提交。

## 7. 同步维护与记录

每次更新都要核对本清单，并在同一提交或 PR 中同步维护。功能、接口、鉴权、默认值、权限、后台调度、存储或模块边界有变化时，修改相应条目、状态、条件及源码依据。无内容影响时，在维护记录中说明已核对及原因；同一天同批修改可合并记录。不能仅改日期而跳过核对。

规则见 [AGENTS.md](../AGENTS.md)和[开发指南](EXTENDING.md)，PR 使用[检查模板](../.github/pull_request_template.md)。这些是协作要求，清单内容仍需结合代码和验证结果维护。

| 日期 | 源码基线/变更范围 | 核对及维护结果 |
| --- | --- | --- |
| 2026-11-26 | `3a7f60e`；本次清单及维护规则提交 | 将原开发分支盘点对齐主干；补记 NexioUI、独立服务状态和生日资料读取；更正换绑历史清理与学习通列表路径；保留提醒缺口及验证边界；新增每次更新同步维护规则、README 入口和 PR 检查项，未改业务行为 |
| 2026-11-28 | `3a7f60e` 与本次发布整合 | 保留既有 F/G/A 编号与接口分类，新增 F17–F19、G07–G08、A25–A27；按最终候选源码更新实现边界、MCP/AI 用途和预览隔离状态。构建、测试和发布验证见第 6 节。 |
