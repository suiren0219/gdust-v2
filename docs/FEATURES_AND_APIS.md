# 广科课程表：功能、待完善项与接口清单

核查日期：2026-11-26。本文是一份源码盘点表，不是发布公告或在线服务可用性报告。

## 1. 核查范围与状态口径

| 项目 | 本次依据 |
| --- | --- |
| 主工程 | `D:/projects/gdust/gdust-v2`；不以已停用的 `gdust-app` 为开发基线 |
| 当前检出分支 | `feat/four-hour-local-schedule-sync` |
| 固定源码基线 | `319c15dbf3a052dbcd9922d50a5e97e7bb121fa5`，简称 `319c15d` |
| 构建配置 | `versionName=2.0.0-alpha.25`，`versionCode=25`；最低 API 33；不代表已发布版本 |
| 本地 main | `97f88e3` |
| 本地缓存 origin/main | `3a7f60e`；与 HEAD 的共同基线是 `b68b424`。本轮未 fetch，不声称这是远程实时最新版本 |
| 并行修改 | 核查期间学习通 API/解析器及成绩相关测试出现未提交改动。保留这些改动；未提交能力不自动计为基线已完成 |
| 模块边界 | 根 [settings.gradle.kts](../settings.gradle.kts#L20) 仅包含 `:app`；`power-core`、`chaoxing-core`、`independent-apps` 不因位于同仓库就自动成为主 App 功能 |
| 验证方法 | 静态读取源码、调用关系及本地 Git 记录；未登录校园系统、未请求业务接口、未运行应用或设备回归 |

状态含义：**已实现**＝存在对应实现及可达调用；**部分实现**＝已有功能但仍有明确缺口；**未实现**＝指定子能力缺少实现；**待核实**＝证据不足；**建议计划**＝本次建议，尚非用户确认的排期。代码已实现不等于接口当前在线、所有机型验证通过或 APK 已发布。

下文源码链接均相对主工程；行号主要对应 `319c15d`，并行修改可能使工作区行号漂移。

## 2. 已实现与部分实现功能

下表按用户可见能力分组。提醒、电量与待办已有主要功能，但不代表第 3 节所列子能力已完善。

| 编号 | 功能 | 状态 | 当前实现与入口 | 边界/条件 | 代码依据 |
| --- | --- | --- | --- | --- | --- |
| F01 | 学校登录与服务入口 | 已实现 | 学校统一网页登录、兼容账号验证码登录、钉钉二维码登录、免登录预览；服务页进入教务、学习通和五育网页 | 预览或显示缓存不代表认证成功；网页与原生接口会话有各自条件 | [LoginScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/LoginScreen.kt#L95) L95、L155；[AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt#L203) L203 |
| F02 | 整学期课表与校历 | 已实现 | 主导航课表；整学期导入、切周/选周、回本周、切学期、课程详情；保留实际周次、单双周和调课信息，区分未导入与已导入无课 | 依赖有效会话与导入数据；门户快照与正方整学期课表不是同一数据源 | [ScheduleScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/schedule/ScheduleScreen.kt#L143) L143；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1036) L1036 |
| F03 | 本机课表自动同步 | 已实现 | 设置开启后，以四小时为目标周期维护学校会话并同步，复用电量任务唤醒 | Android 调度不保证精确到点；明确认证失效仍需重登，不保证永久在线 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L924) L924；[PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt#L44) L44 |
| F04 | 成绩查询 | 已实现 | 服务/设置入口；按学年学期查询成绩、学分、绩点及课程详情 | 需要有效教务会话；本轮未验证真实成绩数据 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt#L439) L439；[GradesScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/GradesScreen.kt#L116) L116 |
| F05 | 今日概览 | 已实现 | 今日课程、当前/下一课、分时段时间线、待办/逾期数量、公告未读、服务快捷入口、电量绑定状态 | 内容来自已导入或已同步的数据 | [TodayScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodayScreen.kt#L99) L99、L168 |
| F06 | 宿舍电量查询与历史 | 已实现，历史隔离待完善 | 主导航电量；绑定房间、刷新余量、近期用量、本机趋势、日历历史、可用天数估算；采样保留 30 天 | 估算不等于供电承诺；换宿舍历史隔离见 G04 | [PowerScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/PowerScreen.kt#L105) L105、L229；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L277) L277 |
| F07 | 电量后台采集与预警 | 已实现 | 后台查询、缓存复用、低电量及异常掉电预警；同类预警冷却 12 小时 | 依赖绑定、配置和系统调度；查询预设回退为 `BALANCED`，不能直接沿用 README 的“默认关闭”描述 | [PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt#L119) L119、L160；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L542) L542 |
| F08 | 手动待办管理 | 部分实现 | 主导航待办；新增文本、自动分类、完成/恢复、删除和清理；考试优先，按课程及截止时间整理 | 当前手动新增未传截止时间，日期/时间输入见 G03 | [TodoScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoScreen.kt#L95) L95、L162 |
| F09 | 学习通课程与作业同步 | 已实现 | 服务/设置入口；连接、课程列表、任务点统计、手动作业同步、截止时间解析、部分失败隔离、远端完成态及通知重复项合并 | 首页身份验证不保证所有课程/作业接口有效；并行中的正文导航改动不计入本基线 | [ChxEntryScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxEntryScreen.kt#L226) L226；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1220) L1220 |
| F10 | 学习通通知转待办 | 已实现 | 通知监听、符合条件的消息提取与去重，配有说明/开关页 | 需通知使用权和应用内开关；不是云端获取全部历史通知 | [ChxNotificationScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxNotificationScreen.kt#L27) L27；[ChxNotificationListener.kt](../app/src/main/java/com/menglimi/gdust/v2/service/ChxNotificationListener.kt#L10) L10 |
| F11 | 课程/待办提醒与勿扰 | 部分实现 | 课前、课中、次日课程、待办到期提醒，上课勿扰及超级岛设置 | 需对应系统权限；超级岛兼容性未实测；待办目标绑定及即时重排见 G01/G02 | [ReminderScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ReminderScreen.kt#L73) L73；[LessonReminderAlarms.kt](../app/src/main/java/com/menglimi/gdust/v2/work/LessonReminderAlarms.kt#L55) L55 |
| F12 | 桌面小部件 | 已实现 | **11 款：课表 4、电量 4、待办 3**；可预览、请求固定到桌面、跳转业务页；待办支持一键完成 | 课表/电量各有 4×2、2×2、1×2、1×4；刷新主要重读本机缓存，不应把刷新调度当作联网查询按钮 | [WidgetPinner.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetPinner.kt#L18) L18；[WidgetActions.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetActions.kt#L57) L57；[WidgetRefreshWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetRefreshWorker.kt#L21) L21 |
| F13 | 外观与桌面快捷方式 | 已实现 | 跟随系统深浅色、课表显示参数、自定义桌面快捷方式名称和图片 | 快捷方式不改变安装包图标；残留壁纸存储字段不等于已有可用配置入口 | [Themes.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/Themes.kt#L19) L19；[DesktopShortcut.kt](../app/src/main/java/com/menglimi/gdust/v2/launcher/DesktopShortcut.kt#L70) L70 |
| F14 | JSON 备份与恢复 | 已实现 | 设置导入/导出课表、校历、待办、电量采样、部分偏好和宿舍绑定 | 不是全量偏好备份；不含学校登录凭证或 AI 配置，分享前仍需检查宿舍等个人数据 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt#L523) L523；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L618) L618 |
| F15 | AI 学习助手 | 已实现 | 有页面入口；多轮对话、快捷问题、清空、内置高校学习建议；默认附加可关闭的本机课表/待办摘要 | 用户自配兼容服务；未见对话历史持久化；保存配置不代表连通性已验证；附加摘要会发送给所配服务 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt#L719) L719；[AiChatScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/AiChatScreen.kt#L103) L103；[AiServiceSettingsScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/AiServiceSettingsScreen.kt#L112) L112 |
| F16 | 公告与 AI 总结 | 已实现 | 今日/服务/设置可达；门户、教务处、官网切源与刷新，本机已读标记、浏览器原文、AI 总结 | 总结操作仍要求门户会话和 AI 配置；标记已读不证明浏览器已加载正文 | [NoticeScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/NoticeScreen.kt#L114) L114、L203 |

### 文档与实现差异

| 现有描述 | 本次核对结果 | 处理口径 |
| --- | --- | --- |
| README：课表、电量、待办各三种小部件 | 当前注册/固定清单为 4＋4＋3，共 11 款 | 本清单按 11 款记录；未顺手修改 README |
| README：手动待办可设置到期提醒 | 提醒机制存在，但手动新增入口未提供截止时间输入 | 不将端到端手动设时能力写成已完成 |
| README：电量后台默认关闭 | 当前主 App 的查询预设缺省回退为 `BALANCED` | 默认行为应以代码中的绑定/调度条件一起判断，不能只复述旧说明 |
| README：电量小部件刷新按钮主动查询 | 本轮未核实该用户入口；已核实的通用刷新主要读取缓存 | 不将 README 描述直接计为已实现联网刷新入口 |
| AI 配置保存 | 基线可保存参数；核查期间设置页和客户端正在并行修改 | 新连通性验证能力与路径变化不并入 `319c15d` 的结论 |

## 3. 未实现、未完善与待核实项目

| 编号 | 功能/问题 | 状态 | 当前边界及影响 | 代码依据 | 建议优先级 |
| --- | --- | --- | --- | --- | --- |
| G01 | 待办闹钟绑定具体待办 ID | 未实现的子能力 | 排程只保留时间，广播不携带目标条目；触发时重新挑选最早到期的未完成事项，可能在 B 的时点再次提醒 A。不能把现有提醒功能等同于逐项准确提醒 | [LessonReminderAlarms.kt](../app/src/main/java/com/menglimi/gdust/v2/work/LessonReminderAlarms.kt#L77) L77、L189；[TodoReminder.kt](../app/src/main/java/com/menglimi/gdust/v2/work/TodoReminder.kt#L35) L35 | P1 |
| G02 | 待办变更后即时重排提醒 | 未实现的子能力 | 通用待办保存只写 JSON；学习通同步也经该入口。现有重排依赖提醒设置、广播和六小时兜底，新同步截止事项可能未及时进入提醒链 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L199) L199、L1319；[TodoReminderScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoReminderScreen.kt#L30) L30；[LessonReminderAlarms.kt](../app/src/main/java/com/menglimi/gdust/v2/work/LessonReminderAlarms.kt#L216) L216 | P1 |
| G03 | 手动待办截止时间输入/编辑 | 部分实现 | 数据模型有 `dueAt`，但当前手动添加界面只传文本，截止时间使用默认值 `0`；提醒页关于手动设置时间的说明超过这个入口的能力 | [TodoScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoScreen.kt#L162) L162；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L211) L211；[TodoReminderScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoReminderScreen.kt#L103) L103 | P2 |
| G04 | 主 App 电量历史按宿舍隔离 | 部分实现 | 样本只记录时间和余额，统一写入 `samples`；换绑覆盖房间配置但未切换历史，存在不同宿舍样本混用风险。本结论不外推到独立电量 App | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L277) L277、L1534 | P2 |
| G05 | 门户会话失效后的端到端恢复/重登回归 | 待运行验证 | `schoolAuthFailure` 已识别业务码 `116/117` 和 HTTP 401，且在一般 HTTP 错误前执行；已有 `HTTP 500 + code=117` 测试用例。识别子能力已实现，真实服务下的完整恢复/提示流程本轮未运行验证 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L109) L109；[SessionPolicy.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SessionPolicy.kt#L6) L6；[SessionPolicyTest.kt](../app/src/test/java/com/menglimi/gdust/v2/data/SessionPolicyTest.kt#L19) L19 | P2：回归验证 |
| G06 | 当前分支与缓存主干功能对齐 | 版本分叉，不是全项目未实现 | 缓存主干包含 #15 集成和 #16 NexioUI；当前分支有专属 AI/作业解析提交。需逐项对齐，不能直接把较旧工作区当最新主干，也不能把另一分支已有功能写成未开发 | 本地 `git log HEAD..origin/main`、`git log origin/main..HEAD`；见第 1 节基线 | P1：发布前明确基线 |

### 已有旧问题不能重复列为待实现

| 旧记录 | 当前核查结论 | 代码依据 |
| --- | --- | --- |
| 一门学习通课程失败导致整批同步取消 | 当前按课程捕获普通异常并返回失败结果，其他成功课程继续合并；会话变化取消仍正常传播 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1241) L1241、L1276 |
| 任务点全部完成就跳过作业，漏掉远端完成状态 | 当前遍历全部课程，不再使用 `jobFinish >= jobCount` 预过滤；远端完成状态进入合并 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1241) L1241、L1286、L1350 |
| 作业绝对截止时间完全未解析 | 当前会读取未交作业详情并优先使用绝对截止时间，失败再回退相对时间；均未知时保留 `0`。旧“待补抓包”注释已经滞后，但不代表所有页面格式通过实测 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1216) L1216、L1252、L1290 |

## 4. 计划：现有依据与本次建议分开

### 4.1 可以确认的既有安排

- [PROGRESS.md](../PROGRESS.md#L3) 和 [开发指南](EXTENDING.md#L28) 明确：开发进度及排期以 GitHub Issues/PR 为准。仓库当前说明未提供可据此列出的统一路线图；本轮未查询在线 Issues/PR，因此不虚构已承诺功能、负责人或完成日期。
- 独立电量/学习应用是独立工程边界，不能写成主 App 尚缺的功能。独立课电和 Xposed 模块也不列为“计划接回主 App”。

### 4.2 建议推进表（未确认排期）

| 顺序 | 建议实现/完善事项 | 对应问题 | 建议验收标准 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 明确后续开发和发布基线，合并时逐项核对能力而不是只看分支日期 | G06 | 可指向一个确定提交；AI、登录状态、UI 与作业解析的保留/变化有说明 | 建议计划 |
| 2 | 提醒携带待办 ID，并处理完成、删除、修改时间及过期情形 | G01 | 两条相邻截止待办各自只提醒对应条目；删除/完成后不再通知该条目 | 建议计划 |
| 3 | 在待办新增、编辑、完成、删除及同步落库后重排提醒 | G02 | 变更后无需等六小时兜底，下一条提醒立即更新 | 建议计划 |
| 4 | 增加手动截止日期/时间编辑，与文案保持一致 | G03 | 手动事项能保存、修改和清除截止时间，并进入提醒链 | 建议计划 |
| 5 | 按校区/楼栋/房间区分电量历史，并设计旧数据迁移 | G04 | 换绑不串历史；旧样本归属无法确认时有明确提示 | 建议计划 |
| 6 | 运行门户会话失效后的集成/设备回归，确认识别后的提示与恢复链路 | G05 | 覆盖已确认的失效响应；恢复或提示重登行为明确，不以自动续期保证永久在线 | 建议计划 |
| 7 | 更新滞后注释、功能描述与数量说明 | 文档与实现差异 | README、设置页说明、源码注释与最终发布基线一致 | 建议计划 |

## 5. 当前调用的接口

以下为源码中已接入调用链的显式业务请求。“可达”指存在界面或后台任务到发送点的代码路径，未实际调用远端验证。参数均为占位符，不包含真实账号或凭证。HTML 页面读取、SSE 和网页登录单独注明。

### 5.1 主机与返回形式

| 简称 | Base URL/主机 | 说明 |
| --- | --- | --- |
| CAS | `https://cas.gdust.edu.cn` | 学校统一认证、验证码、扫码事件流 |
| 门户 | `https://portal.gdust.edu.cn` | 门户身份、校历、课表及公告 |
| 教务 | `http://172.16.254.1`、`http(s)://gkjx.gdust.edu.cn`，或带原有转发前缀的 WebVPN base | 根据教务会话入口确定，不把内网地址当作公网可用地址；见 [TeachingEndpoints.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingEndpoints.kt#L8) |
| M1 | `https://mooc1.chaoxing.com` | 学习通课程入口、作业页面 |
| M2 | `https://mooc2-ans.chaoxing.com` | 学习通首页、课程列表、任务点及另一种课程入口 |
| 电量 | `https://xqh5.17wanxiao.com` | 完美校园水电服务 |
| AI | 用户配置的 Base URL | OpenAI 兼容的聊天补全请求 |

### 5.2 学校认证与门户数据

| 编号 | 方法、主机与路径 | 用途/返回形式 | 鉴权及触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A01 | `GET CAS /cas-api/cas/loginCode` | 验证码 JSON | 兼容登录展开或刷新验证码 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L128) L128 |
| A02 | `POST CAS /cas-api/cas/loginByAccount` | 账号、密码、验证码换一次性 ticket，JSON | 用户主动提交兼容登录表单 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L133) L133 |
| A03 | `GET CAS /cas-api/sse/subscribe` | SSE 扫码事件流，接收 clientId、授权事件和 ticket | 用户进入扫码登录流程 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L259) L259 |
| A04 | `GET 门户 /smart-admin-api/user/login?loginCode={ticket}&appId=portalRemote` | ticket 换门户 TOKEN，JSON | 兼容登录成功后；ticket 为一次性票据 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L146) L146 |
| A05 | `GET 门户 /smart-admin-api/app/userInfo/addition` | 身份信息、会话验证，JSON | `TOKEN` 请求头；登录验证、启动/状态探活 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L159) L159 |
| A06 | `GET 门户 /smart-admin-api/app/zf/get_school_calendar?jobNumber={学号}` | 校历 JSON | `TOKEN`；同步校历或本机缺少校历时 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L164) L164 |
| A07 | `GET 门户 /smart-admin-api/app/zf/get_student_course?jobNumber={学号}&week={周次}&year={学年}&semester={学期}` | 门户课程快照，JSON | `TOKEN`；当前调用允许周次/学年/学期为空。已有正方整学期课表时不以该快照覆盖 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L173) L173；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1182) L1182 |
| A08 | `GET 门户 /smart-admin-api/app/ding/get_announcement_items?jobNumber={学号}` | 门户公告列表，JSON | `TOKEN`；有门户会话时加载/刷新公告 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L196) L196 |
| A09 | `GET 门户 /smart-admin-api/app/news/list?source=jiaowu` | 教务处公告列表 | 独立客户端，不附 `TOKEN` 或 Cookie；切源/刷新可达 | [SchoolNoticeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SchoolNoticeApi.kt#L14) L14；[PortalNews.kt](../app/src/main/java/com/menglimi/gdust/v2/data/PortalNews.kt#L38) L38 |
| A10 | `GET 门户 /smart-admin-api/app/news/list?source=home` | 官网新闻列表 | 当前实现附 `TOKEN`；切源/刷新可达 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1455) L1455 |

### 5.3 正方教务

| 编号 | 方法、主机与路径 | 用途/返回形式 | 鉴权及触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A11 | `POST 教务 /kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151` | 提交学期表单，返回含 `kbList` 的 JSON | 教务 Cookie；导入、同步、会话验证及开启后的四小时维护 | [TeachingSession.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingSession.kt#L21) L21；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1049) L1049 |
| A12 | `GET 教务 /cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default` | 读取成绩表单 HTML 中的 pkey 和学期选项 | 教务 Cookie；打开成绩页 | [TeachingGradeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingGradeApi.kt#L43) L43 |
| A13 | `POST 教务 /cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005` | 分页成绩 JSON；基线每页 15 条、最多 100 页 | 教务 Cookie 与表单 pkey；成绩查询 | [TeachingGradeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingGradeApi.kt#L48) L48 |
| A14 | `GET 教务 {已认证落地页path}` | 从 HTML 发现只读个人信息菜单；清除落地页 query/fragment 后读取 | 教务 Cookie；条件性自动/手动读取生日资料 | [TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt#L14) L14 |
| A15 | `GET 教务 /xsxxxggl/xsgrxxwh_cxXsgrxx.html?gnmkdm={菜单代码}` | 从个人资料 HTML 提取生日月日 | 教务 Cookie；只读，不提交个人资料修改 | [TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt#L22) L22 |

### 5.4 学习通

| 编号 | 方法、主机与路径 | 用途/返回形式 | 鉴权及触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A16 | `GET M2 /mooc2-ans/visit/interaction` | 首页身份探活与课程发现前置 HTML | 超星 Cookie；验证连接、加载课程/同步作业 | [ChxCourseApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxCourseApi.kt#L45) L45 |
| A17 | `POST M2 /mooc2-ans/visit/courselistdata` | 课程列表 HTML | 超星 Cookie；课程加载、作业同步 | [ChxCourseApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxCourseApi.kt#L77) L77 |
| A18 | `GET M2 /mooc2-ans/mycourse/stu-job-info?clazzPersonStr={班级ID_个人ID列表}` | 任务点进度 JSON | 超星 Cookie；课程概览 | [ChxCourseApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxCourseApi.kt#L92) L92 |
| A19 | `GET M1 /visit/stucoursemiddle?courseid={courseId}&clazzid={classId}&cpi={cpi}&ismooc2=1&v=2`，或课程提供的 `M2 /mooc2-ans/mycourse/stu?...` | 读取课程 HTML 中的作业入口和签名字段；允许经过校验的重定向 | 超星 Cookie；逐课同步，具体入口来自课程数据 | [ChxApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt#L24) L24、L93、L104 |
| A20 | `GET M1 {前缀}/mooc2/work/list{后缀}?courseId={courseId}&classId={classId}&cpi={cpi}&ut=s&t={t}&stuenc={enc}&enc={workEnc}` | 作业列表 HTML；前缀为空或 `/mooc-ans`，后缀为空或 `.html` | 超星 Cookie 与页面签名；用户手动作业同步 | [ChxApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt#L100) L100、L152 |
| A21 | `GET M1 {前缀}/mooc2/work/task?workId={workId}&answerId={answerId}&enc={enc}&{页面其余参数}` | 未交作业详情 HTML，用于提取绝对截止时间；前缀同上 | 超星 Cookie 与受校验的页面签名链接；同步时条件性读取 | [ChxApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt#L50) L50；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1252) L1252 |

### 5.5 完美校园电量与 AI

| 编号 | 方法、主机与路径 | 用途/返回形式 | 鉴权及触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A22 | `POST 电量 /smartWaterAndElectricityService/SWAEServlet`；表单 `method=getbindroom` | 获取绑定房间；表单内还有 `command=JBSWaterElecService`、`customercode` 和 JSON 字符串 `param` | account、customercode；绑定查询，无需学校门户登录 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L207) L207、L214 |
| A23 | 同上 `POST`；表单 `method=h5_getstuindexpage` | 获取余量和用量详情；同一 Servlet 按 method 分流，余额没有单独 endpoint | account、customercode、roomverify；前台刷新及满足条件的后台采集 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L227) L227；[PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt#L119) L119 |
| A24 | `POST {用户配置host}{拼接路径}`，最终为聊天补全 endpoint | AI 学习助手对话、公告总结，JSON | `Authorization: Bearer {key}`；配置完整且用户发送消息/请求总结 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L298) L298、L314 |

`319c15d` 的 AI 地址拼接规则：先执行 `base.trim().trimEnd('/')`；若以 `/chat/completions` 结尾则直接使用；若以 `/v1` 结尾则追加 `/chat/completions`；其他情况追加 `/v1/chat/completions`。这记录的是项目兼容协议，并未核验任何服务商当前支持情况。核查期间新增的 `LlmApi.kt` 与配置页连接验证改动不纳入该提交的结论。

### 5.6 网页入口与外部跳转

以下是用户操作或会话流程打开的网页，不能当作返回 JSON 的业务 API；网页内提交、子资源和动态重定向未逐项展开。

| 方法/入口 | 用途与条件 | 代码依据 |
| --- | --- | --- |
| `GET CAS /cas/?service={urlencode(门户回调)}` | 统一登录网页；门户回调为 `/smart-admin-api/user/ssoLogin/?appId=portalRemote`，另由学校附加认证参数 | [SchoolLoginFlow.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SchoolLoginFlow.kt#L170) L170 |
| `GET CAS /lyuapServer/login?service={urlencode(目标服务)}` | 教务/学习通 SSO；目标分别为教务 `/sso/lyiotlogin` 与 `http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy` | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt#L23) L23 |
| `GET https://webvpn.gdust.edu.cn/{VPN认证前缀}/cas/?service={WebVPN回调}` | 用户选择 VPN 或对应会话续租时的网页入口；固定前缀保留源码为准 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt#L47) L47 |
| `GET https://passport2.chaoxing.com/login?fid=420&refer={urlencode(https://i.chaoxing.com)}` | 用户选择学习通账号登录 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt#L67) L67 |
| `GET CAS /cas/logout` | 用户显式退出学校网页登录 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt#L38) L38；[ChxEntryScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxEntryScreen.kt#L307) L307 |
| `GET https://www.gdust.edu.cn/jwc/jiaowu/{日期}/{id}.html`、`/article/{目录编号}/{id}.html` 或公告提供的 URL | 用户点击后由外部浏览器打开公告原文，App 不显式转发门户 TOKEN | [PortalNews.kt](../app/src/main/java/com/menglimi/gdust/v2/data/PortalNews.kt#L49) L49；[NoticeScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/NoticeScreen.kt#L292) L292 |
| `GET http://172.16.1.232/index` | 五育服务 WebView 入口 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt#L212) L212 |
| `GET https://github.com/suiren0219/gdust-v2`、`GET https://www.gdust.edu.cn/` | 关于页外部链接 | [SettingSubPages.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/SettingSubPages.kt#L49) L49 |

### 5.7 独立应用、保留定义与未发现的调用

| 范围 | 核查结论 | 代码依据 |
| --- | --- | --- |
| 主 App 与独立模块 | 根工程只构建 `:app`；独立 `learning-app` 使用 `chaoxing-core`，`power-app` 使用 `power-core`。独立应用能力不自动算作主 App 功能 | [根 settings.gradle.kts](../settings.gradle.kts#L21) L21；[独立 settings.gradle.kts](../independent-apps/settings.gradle.kts#L19) L19 |
| 独立学习应用 | 同组学习通业务接口，另有条件性课程正文导航读取；账号网页登录入口为 `https://passport2.chaoxing.com/login?refer={学习通HOME}` | [LearningSession.kt](../independent-apps/learning-app/src/main/java/com/menglimi/study/learning/LearningSession.kt#L41) L41；[ChxCourseNavigation.kt](../chaoxing-core/src/main/kotlin/com/menglimi/chaoxing/ChxCourseNavigation.kt#L11) L11 |
| 独立电量应用 | `power-core.PowerApi` 使用同一 SWAEServlet 及 `getbindroom`、`h5_getstuindexpage` | [PowerApi.kt](../power-core/src/main/kotlin/com/menglimi/power/PowerApi.kt#L54) L54、L103 |
| 独立应用检查更新 | `GET https://api.github.com/repos/suiren0219/gdust-v2/releases?per_page=100`；无 Authorization；用户点击检查更新后发送 | [AppRelease.kt](../independent-apps/ksu-ui/src/main/java/com/menglimi/ksu/ui/AppRelease.kt#L79) L79；[AboutProjectCard.kt](../independent-apps/ksu-ui/src/main/java/com/menglimi/ksu/ui/AboutProjectCard.kt#L51) L51 |
| 旧教务原生流程 | 保留 `GET {base}/sso/lyiotlogin?ticket={ticket}&service={service}` 及旧 GET 课表探活实现；`openSession/loginAndFetch/probe` 未找到当前业务调用点，不计入现行请求清单 | [TeachingSsoClient.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingSsoClient.kt#L52) L52、L164 |
| 新版超星 SSO 定义 | `https://gdkj.mh.chaoxing.com/sso/login/3rd` 的 `mhUrl` 仅找到定义，未发现当前调用点 | [ChxSso.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxSso.kt#L29) L29 |
| debug 探测 | debug WebView 探测 Activity 可加载调试 URL，不计作正常发布业务 | [WebViewProbeActivity.kt](../app/src/debug/java/com/menglimi/gdust/v2/debug/WebViewProbeActivity.kt#L59) L59 |
| 主 App 其他网络能力 | 本轮未发现天气、版本更新检查、遥测、崩溃上报或校园壁纸服务的显式业务发送链路。仅是静态检索结论，不覆盖 WebView 内全部第三方子请求 | 以第 1 节基线和上述调用范围为限 |

编号用于清单定位，不代表独立 URL 数量：同一 Servlet 可承载多个业务 method，学习通入口也有多个受校验的路径变体。

## 6. 核查限制

- 本文的“已实现”是代码层面的结论，不是实时业务成功率、学校服务 SLA 或发布版本承诺。
- 未连接学校认证、门户、教务、学习通、完美校园或校园壁纸服务；未向 AI 服务发送数据。
- 未执行构建、单元测试、模拟器/真机验收；测试文件存在不等于本轮测试通过。
- 本次只新增此清单，不修改业务源码、不切换/合并分支、不提交或推送。
