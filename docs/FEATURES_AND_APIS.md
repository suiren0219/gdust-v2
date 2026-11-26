# 广科课程表：功能、待完善项与接口清单

核查日期：2026-11-26。本文记录源码中的功能、未完成项、建议计划和显式业务请求，随每次更新同步维护。

## 1. 核查范围与状态口径

| 项目 | 本次依据 |
| --- | --- |
| 仓库 | `suiren0219/gdust-v2` |
| 源码基线 | `main` 的 `3a7f60e8967d745a741a80e251f115f0169fbad0`，简称 `3a7f60e`；2026-11-26 已 fetch 核对 |
| 构建配置 | `versionName=2.0.0-alpha.25`、`versionCode=25`、最低 API 33；配置值不代表 APK 已发布，见 [app/build.gradle.kts](../app/build.gradle.kts#L19) |
| 模块边界 | 根工程包含 `:app` 和 UI 库 `:nexio-ui`；`power-core`、`chaoxing-core`、`independent-apps` 属于独立工程边界，见 [settings.gradle.kts](../settings.gradle.kts#L20) |
| 支线边界 | 原清单基于开发分支 `319c15d`，本版已按主干重新核对。未合入主干的提交及未提交修改不计为主干已完成 |
| 核查方法 | 静态读取源码、调用关系和 Git 差异；未登录校园系统、调用业务接口或进行设备回归 |

**已实现**表示有实现和可达调用；**部分实现**表示已有主要功能但仍有明确缺口；**待验证**表示尚缺对应验证；**建议计划**表示本清单提出的建议，未承诺排期。代码实现、测试通过、设备验证和版本发布分别记录。

源码链接相对仓库根目录，行号对应上述基线。后续修改相关代码时，应同步检查链接和结论。

## 2. 已实现与部分实现功能

| 编号 | 功能 | 状态 | 当前实现与入口 | 边界与条件 | 代码依据 |
| --- | --- | --- | --- | --- | --- |
| F01 | 学校登录与服务状态 | 已实现 | 统一网页登录、兼容账号验证码、钉钉扫码、免登录预览；门户、教务、学习通分别显示连接状态，支持逐项验证和补连 | 预览或本机缓存不代表认证成功；状态卡进入时不自动探活，验证由按钮触发 | [LoginScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/LoginScreen.kt)；[ServiceStatusCard.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ServiceStatusCard.kt#L54)；[SettingsScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/SettingsScreen.kt#L490) |
| F02 | 整学期课表与校历 | 已实现 | 主导航课表；整学期导入、切周/选周、回本周、切学期、课程详情，保留实际周次、单双周和调课信息 | 依赖有效会话和导入数据；门户快照与正方整学期课表分开处理 | [ScheduleScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/schedule/ScheduleScreen.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| F03 | 本机课表自动同步 | 已实现 | 设置开启后，以四小时为目标周期维护学校会话并同步，复用电量任务唤醒 | Android 调度不保证精确到点；明确认证失效仍需重新登录 | [ScheduleSyncWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/ScheduleSyncWorker.kt)；[PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt)；[ScheduleSyncPolicy.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ScheduleSyncPolicy.kt) |
| F04 | 成绩与生日资料读取 | 已实现 | 成绩页按学年学期查询成绩、学分、绩点及课程详情；支持从教务个人资料读取生日月日并用于生日展示 | 需要有效教务会话；生日资料只读，不提交修改；真实数据未在本轮验证 | [GradesScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/GradesScreen.kt)；[TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt#L14)；[AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt#L307) |
| F05 | 今日概览 | 已实现 | 今日课程、当前/下一课、时间线、待办/逾期数量、公告未读、服务快捷入口及电量绑定状态 | 内容取决于已导入或同步的数据 | [TodayScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodayScreen.kt) |
| F06 | 宿舍电量查询与历史 | 已实现 | 绑定房间、刷新余量、近期用量、本机趋势、日历历史、可用天数估算；采样保留 30 天；绑定标识变化时清除旧历史及采集状态 | 当前通过清除防止旧历史沿用，尚不支持按宿舍分别保留历史，见 G04 | [PowerScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/PowerScreen.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L563) |
| F07 | 电量后台采集与预警 | 已实现 | 后台查询、缓存复用、低电量及异常掉电预警；同类预警冷却 12 小时 | 依赖绑定、配置和系统调度；查询预设缺省回退为 `BALANCED`，实际调度仍受绑定等条件约束 | [PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt)；[PowerQueryPolicy.kt](../app/src/main/java/com/menglimi/gdust/v2/data/PowerQueryPolicy.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| F08 | 手动待办管理 | 部分实现 | 新增文本、自动分类、完成/恢复、删除和清理；考试优先，按课程及截止时间整理 | 手动添加入口未传截止时间，见 G03 | [TodoScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoScreen.kt#L164)；[TodoModel.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TodoModel.kt) |
| F09 | 学习通课程与作业同步 | 已实现 | 课程列表、任务点统计、手动作业同步、截止时间解析、单课失败隔离、远端完成态及通知重复项合并 | 首页验证不保证各课程作业接口有效；部分失败区分认证问题与读取问题 | [ChxEntryScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxEntryScreen.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1602)；[ChxWorkParser.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxWorkParser.kt#L186) |
| F10 | 学习通通知转待办 | 已实现 | 通知监听、符合条件的消息提取及去重，配有说明和开关页 | 需要通知使用权和应用内开关，不获取全部历史通知 | [ChxNotificationScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ChxNotificationScreen.kt)；[ChxNotificationListener.kt](../app/src/main/java/com/menglimi/gdust/v2/service/ChxNotificationListener.kt) |
| F11 | 课程/待办提醒与勿扰 | 部分实现 | 课前、课中、次日课程、待办到期提醒，上课勿扰及超级岛设置 | 需要对应权限；超级岛兼容性未实测；待办目标绑定和统一落库重排见 G01/G02 | [ReminderScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/ReminderScreen.kt)；[LessonReminderAlarms.kt](../app/src/main/java/com/menglimi/gdust/v2/work/LessonReminderAlarms.kt)；[ClassQuietMode.kt](../app/src/main/java/com/menglimi/gdust/v2/work/ClassQuietMode.kt) |
| F12 | 桌面小部件 | 已实现 | 11 款：课表 4、电量 4、待办 3；预览、请求固定、跳转业务页；待办一键完成 | 课表/电量各为 4×2、2×2、1×2、1×4；通用刷新主要重读缓存 | [WidgetPinner.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetPinner.kt)；[WidgetActions.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetActions.kt)；[WidgetRefreshWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetRefreshWorker.kt) |
| F13 | 外观与桌面快捷方式 | 已实现 | 接入 NexioUI、跟随系统深浅色、课表显示参数、自定义桌面快捷方式名称和图片 | 快捷方式不改变安装包图标；残留壁纸存储字段不等于可用配置入口 | [Themes.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/Themes.kt#L20)；[DesktopShortcut.kt](../app/src/main/java/com/menglimi/gdust/v2/launcher/DesktopShortcut.kt)；[app/build.gradle.kts](../app/build.gradle.kts#L99) |
| F14 | JSON 备份与恢复 | 已实现 | 设置中导入/导出课表、校历、待办、电量采样、部分偏好及宿舍绑定 | 非全量偏好备份；不含学校登录凭证或 AI 配置，分享前需检查个人数据 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| F15 | AI 学习助手 | 已实现 | 主界面入口、多轮对话、快捷问题、清空及高校学习建议；默认附加可关闭的课表/未完成待办摘要 | 用户自配兼容服务；消息保留在当前应用界面状态中，未见磁盘历史持久化；保存配置不自动验证连接，摘要会发送到所配服务 | [AppRoot.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/AppRoot.kt#L763)；[AiChatScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/AiChatScreen.kt#L89)；[AiServiceSettingsScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/AiServiceSettingsScreen.kt#L127) |
| F16 | 公告与 AI 总结 | 已实现 | 门户、教务处、官网切源与刷新，本机已读、浏览器原文及 AI 总结 | 总结操作仍要求门户会话与 AI 配置；标已读不证明浏览器加载成功 | [NoticeScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/NoticeScreen.kt)；[GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L322) |

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
| G01 | 待办闹钟绑定具体条目 ID | 未实现的子能力 | 广播只携带 action，触发时重新选择最早截止的未完成项，存在在 B 的时点再次提醒 A 的风险 | [LessonReminderAlarms.kt](../app/src/main/java/com/menglimi/gdust/v2/work/LessonReminderAlarms.kt#L165)；[TodoReminder.kt](../app/src/main/java/com/menglimi/gdust/v2/work/TodoReminder.kt#L40) | P1 |
| G02 | 待办统一落库后即时重排 | 未实现的子能力 | `updateTodos` 只写 JSON，作业同步也经此入口；已有启动、小部件操作、提醒设置、广播和兜底重排，但未覆盖统一落库后的即时重排 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L200)；[MainActivity.kt](../app/src/main/java/com/menglimi/gdust/v2/MainActivity.kt#L66)；[WidgetActions.kt](../app/src/main/java/com/menglimi/gdust/v2/widget/WidgetActions.kt#L63) | P1 |
| G03 | 手动待办截止日期/时间输入与编辑 | 部分实现 | 模型有 `dueAt`，添加入口未传入，使用默认值 `0`；提醒说明超过该入口能力 | [TodoScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoScreen.kt#L164)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L216)；[TodoReminderScreen.kt](../app/src/main/java/com/menglimi/gdust/v2/ui/screens/TodoReminderScreen.kt) | P2 |
| G04 | 按宿舍分别保留电量历史 | 可选增强，尚未实现 | account/code/verify 变化会清除样本、Worker 缓存及预警状态，避免直接沿用旧历史；尚无按宿舍分别保存与切换历史的能力 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L563)；[换绑入口](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1969) | P3 |
| G05 | 门户失效后完整恢复/重登回归 | 待运行验证 | 已识别业务码 `116/117` 与 HTTP 401，且在普通 HTTP 错误处理前执行；已有 `500 + code=117` 测试用例，本轮未运行完整流程 | [SessionPolicy.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SessionPolicy.kt#L6)；[GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L110)；[SessionPolicyTest.kt](../app/src/test/java/com/menglimi/gdust/v2/data/SessionPolicyTest.kt#L19) | P2 |
| G06 | 支线能力与发布范围核对 | 版本范围说明 | 主干已有 #15 集成与 #16 NexioUI；开发分支 `319c15d` 与主干仍有不同实现。支线提交和未提交修改需经合并及验证后才计入主干 | 第 1 节源码基线；合并时核对对应提交差异 | 发布前核对 |

### 旧问题核对结论

| 旧记录 | 主干现状 | 代码依据 |
| --- | --- | --- |
| 单门学习通课程失败取消整批 | 已按课程隔离普通异常，成功结果继续合并，取消异常传播；失败区分认证和读取问题 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1563)、[逐课读取](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1602) |
| 任务点全完成后跳过作业 | 没有该预过滤，遍历全部课程读取作业并合并远端完成态 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1604) |
| 作业绝对截止时间未解析 | 已读取未交作业详情；绝对时间优先，相对时间兜底，均未知为 `0`；普通详情错误可回退，认证异常作为该课程失败处理 | [详情读取](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1613)、[时间选择](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1655) |
| 换绑宿舍未清除旧历史 | 主干已在绑定标识变化时清理历史及后台状态，不再列为直接串用缺陷 | [Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L563) |

## 4. 计划及建议推进顺序

[项目进度](../PROGRESS.md)和[开发指南](EXTENDING.md)规定，排期以 GitHub Issues/PR 为准。此次未读取在线排期，不据此编造负责人或完成时间。独立学习、电量应用及 Xposed 模块不列为计划接回主 App。

| 顺序 | 建议事项 | 对应条目 | 验收方向 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 提醒携带待办 ID，并处理完成、删除、改时与过期 | G01 | 相邻截止事项各自提醒对应条目，完成或删除后不再提醒 | 建议计划 |
| 2 | 新增、编辑、完成、删除及同步落库后统一重排提醒 | G02 | 无需等待兜底任务即可更新下一条提醒 | 建议计划 |
| 3 | 增加手动截止时间输入、编辑和清除 | G03 | 时间能正确保存并进入提醒链 | 建议计划 |
| 4 | 运行门户失效后的集成与设备回归 | G05 | 已识别错误后的恢复或重登提示明确 | 建议计划 |
| 5 | 如需保留多宿舍历史，设计分宿舍存储与迁移 | G04 | 换绑清理策略和历史保留策略明确，无法确认归属的旧样本不乱归类 | 可选建议 |
| 6 | 发布前核对支线差异并修订滞后说明 | G06、文档差异表 | 清单、README、设置说明与发布基线一致 | 建议计划 |

## 5. 当前调用的接口

以下“可达”指存在页面或后台任务到请求发送点的调用链，未进行真实接口验证。参数使用占位符。HTML、JSON、SSE 和网页入口分别注明。

### 5.1 主机

| 简称 | Base URL/主机 | 说明 |
| --- | --- | --- |
| CAS | `https://cas.gdust.edu.cn` | 学校统一认证 |
| 门户 | `https://portal.gdust.edu.cn` | 门户数据与公告 |
| 教务 | `http://172.16.254.1`、`http(s)://gkjx.gdust.edu.cn` 或保留转发前缀的 WebVPN base | 取决于当前教务会话，见 [TeachingEndpoints.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingEndpoints.kt#L8) |
| M1 | `https://mooc1.chaoxing.com` | 学习通课程及作业页面 |
| M2 | `https://mooc2-ans.chaoxing.com` | 学习通首页、课程列表及任务点 |
| 电量 | `https://xqh5.17wanxiao.com` | 完美校园水电服务 |
| AI | 用户配置的 Base URL | 项目使用聊天补全兼容协议 |

### 5.2 学校认证与门户

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A01 | `GET CAS /cas-api/cas/loginCode` | 验证码 JSON | 展开兼容登录或刷新验证码 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L129) |
| A02 | `POST CAS /cas-api/cas/loginByAccount` | 账号、密码及验证码换 ticket，JSON | 用户主动提交表单 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L134) |
| A03 | `GET CAS /cas-api/sse/subscribe` | 扫码 SSE 事件流 | 用户进入扫码流程 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L261) |
| A04 | `GET 门户 /smart-admin-api/user/login?loginCode={ticket}&appId=portalRemote` | 一次性 ticket 换 TOKEN，JSON | 兼容登录成功后 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L147) |
| A05 | `GET 门户 /smart-admin-api/app/userInfo/addition` | 身份信息与会话验证，JSON | `TOKEN` 头；登录验证、启动或状态探活 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L160) |
| A06 | `GET 门户 /smart-admin-api/app/zf/get_school_calendar?jobNumber={学号}` | 校历 JSON | `TOKEN`；同步或本机缺少校历时 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L165) |
| A07 | `GET 门户 /smart-admin-api/app/zf/get_student_course?jobNumber={学号}&week={周次}&year={学年}&semester={学期}` | 门户课程快照 JSON | `TOKEN`；当前允许周次/学年/学期为空；已有正方整学期课表时不以快照覆盖 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L174)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |
| A08 | `GET 门户 /smart-admin-api/app/ding/get_announcement_items?jobNumber={学号}` | 门户公告列表 JSON | `TOKEN`；有门户会话时加载或刷新 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L197) |
| A09 | `GET 门户 /smart-admin-api/app/news/list?source=jiaowu` | 教务处公告列表 | 独立客户端不附 TOKEN/Cookie；切源或刷新 | [SchoolNoticeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/SchoolNoticeApi.kt)；[PortalNews.kt](../app/src/main/java/com/menglimi/gdust/v2/data/PortalNews.kt) |
| A10 | `GET 门户 /smart-admin-api/app/news/list?source=home` | 官网新闻列表 | 当前实现附 TOKEN；切源或刷新 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L202)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt) |

### 5.3 正方教务

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A11 | `POST 教务 /kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151` | 学期课表 JSON，包含 `kbList` | 教务 Cookie；导入、同步、会话验证及启用后的四小时维护 | [TeachingSession.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingSession.kt) |
| A12 | `GET 教务 /cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default` | 成绩表单 HTML，读取 pkey 和学期选项 | 教务 Cookie；成绩查询 | [TeachingGradeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingGradeApi.kt#L43) |
| A13 | `POST 教务 /cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005` | 成绩分页 JSON；每页 15 条，最多 100 页 | 教务 Cookie 与 pkey；成绩查询 | [TeachingGradeApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingGradeApi.kt#L48) |
| A14 | `GET 教务 {已认证落地页path}` | 从 HTML 发现只读个人信息菜单，清除落地页 query/fragment | 教务 Cookie；条件性自动或手动读取生日 | [TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt#L14) |
| A15 | `GET 教务 /xsxxxggl/xsgrxxwh_cxXsgrxx.html?gnmkdm={菜单代码}` | 个人资料 HTML，提取生日月日 | 教务 Cookie；只读，不提交资料修改 | [TeachingBirthdayApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/TeachingBirthdayApi.kt#L22) |

### 5.4 学习通

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A16 | `GET M2 /mooc2-ans/visit/interaction` | 首页探活及课程发现前置 HTML | 超星 Cookie；验证连接、加载课程或同步作业 | [ChxCourseApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxCourseApi.kt#L45) |
| A17 | `POST M2 /mooc2-ans/visit/courselistdata` | 课程列表 HTML | 超星 Cookie；加载课程或同步作业 | [ChxCourseApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxCourseApi.kt#L77) |
| A18 | `GET M2 /mooc2-ans/mycourse/stu-job-info?clazzPersonStr={班级ID_个人ID列表}` | 任务点统计 JSON | 超星 Cookie；课程概览 | [ChxCourseApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxCourseApi.kt#L92) |
| A19 | `GET M1 /visit/stucoursemiddle?courseid={courseId}&clazzid={classId}&cpi={cpi}&ismooc2=1&v=2` 或课程提供的 `M2 /mooc2-ans/mycourse/stu?...` | 课程 HTML，读取签名字段，允许经校验的重定向 | 超星 Cookie；逐课同步 | [ChxApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt#L24) |
| A20 | `GET M1 {前缀}/mooc2/work/list?courseId={courseId}&classId={classId}&cpi={cpi}&ut=s&t={t}&stuenc={enc}&enc={workEnc}` | 作业列表 HTML；前缀为空或 `/mooc-ans`；由 `moocDomain` 拼接，不接受 `.html` 后缀 | 超星 Cookie 与页面签名；手动作业同步 | [允许路径](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt#L92)、[地址构造](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt#L124) |
| A21 | `GET M1 {前缀}/mooc2/work/task?workId={workId}&answerId={answerId}&enc={enc}&{页面其余参数}` | 未交作业详情 HTML，提取绝对截止时间；前缀同上 | 超星 Cookie 与经校验的签名链接；同步时条件性读取 | [ChxApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/ChxApi.kt#L38)；[Repository.kt](../app/src/main/java/com/menglimi/gdust/v2/data/Repository.kt#L1613) |

### 5.5 完美校园电量与 AI

| 编号 | 方法、主机与路径 | 用途/形式 | 鉴权与触发条件 | 代码依据 |
| --- | --- | --- | --- | --- |
| A22 | `POST 电量 /smartWaterAndElectricityService/SWAEServlet`，表单 `method=getbindroom` | 绑定房间；另含 `command=JBSWaterElecService`、`customercode` 和 JSON 字符串 `param` | account、customercode；绑定查询，无需门户登录 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L215) |
| A23 | 同一 Servlet `POST`，表单 `method=h5_getstuindexpage` | 余量及用量详情；余额无单独 endpoint | account、customercode、roomverify；前台刷新及满足条件的后台采集 | [GdustApi.kt](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L228)；[PowerWorker.kt](../app/src/main/java/com/menglimi/gdust/v2/work/PowerWorker.kt) |
| A24 | `POST {用户配置host}{拼接路径}` | AI 对话与公告总结，JSON | `Authorization: Bearer {key}`；配置完整且用户发送或请求总结 | [对话](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L291)、[总结](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L322)、[地址拼接](../app/src/main/java/com/menglimi/gdust/v2/data/GdustApi.kt#L333) |

AI 地址先执行 `base.trim().trimEnd('/')`；以 `/chat/completions` 结尾则直接使用，以 `/v1` 结尾则追加 `/chat/completions`，其他情况追加 `/v1/chat/completions`。这里记录项目的拼接行为，不代表已验证任意服务商的兼容性。

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
| 主 App 其他网络能力 | 本次静态检索未发现天气、版本更新检查、遥测、崩溃上报或校园壁纸服务的显式业务发送链路；不覆盖 WebView 全部第三方子请求 | 以第 1 节基线及上述调用范围为限 |

接口编号用于清单定位，不代表唯一 URL 数量；同一 Servlet 可承载多个业务 method，学习通还有受校验的路径变体。

## 6. 验证范围

- 本版依据代码和差异核查，不代表接口实时在线或所有设备兼容。
- 未连接学校认证、门户、教务、学习通、完美校园、校园壁纸或 AI 服务。
- 未运行构建、单元测试或设备验收；已有测试用例不等于本轮执行通过。
- 本次提交仅包含清单、文档入口和维护规则，未纳入并行开发的业务源码。

## 7. 同步维护与记录

每次更新都要核对本清单，并在同一提交或 PR 中同步维护。功能、接口、鉴权、默认值、权限、后台调度、存储或模块边界有变化时，修改相应条目、状态、条件及源码依据。无内容影响时，在维护记录中说明已核对及原因；同一天同批修改可合并记录。不能仅改日期而跳过核对。

规则见 [AGENTS.md](../AGENTS.md)和[开发指南](EXTENDING.md)，PR 使用[检查模板](../.github/pull_request_template.md)。这些是协作要求，清单内容仍需结合代码和验证结果维护。

| 日期 | 源码基线/变更范围 | 核对及维护结果 |
| --- | --- | --- |
| 2026-11-26 | `3a7f60e`；本次清单及维护规则提交 | 将原开发分支盘点对齐主干；补记 NexioUI、独立服务状态和生日资料读取；更正换绑历史清理与学习通列表路径；保留提醒缺口及验证边界；新增每次更新同步维护规则、README 入口和 PR 检查项，未改业务行为 |
