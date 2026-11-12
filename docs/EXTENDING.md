# 扩展工作区间（学习通 / 教务系统）

> 本文档给「在广科课程表 v2 上继续扩展学习通与教务功能」用：说明工程在哪、扩展代码写在哪、需要什么抓包、以及构建验收怎么做。

---

## 一、工作区间（文件地址）

### 1.1 工程根目录

| 用途 | 路径 |
|---|---|
| **主工程（唯一代码库）** | `D:\ai-projects\gdust-v2` |
| 交付 APK（覆盖更新） | `D:\广科课程表-v2-release.apk` |
| 构建产物 | `D:\ai-projects\gdust-v2\app\build\outputs\apk\release\app-release.apk` |
| git 仓库 | 本地 `main`（远程已移除，**不开源**） |
| 文档目录 | `D:\ai-projects\gdust-v2\docs\` |
| 旧版参考（v1，Nexio 基底，只读参考） | `D:\ai-projects\gdust-app` |
| 组件源码参考（不进工程） | `D:\ai-projects\softglass_ref`、`D:\ai-projects\glasmio_ref` |

### 1.2 关键源码（扩展点）

```
app/src/main/java/com/menglimi/gdust/v2/
├── App.kt                      # Application：注册 Worker（改新 Worker 要在这挂)
├── MainActivity.kt             # 主题外层状态（tab/showSettings/loggedIn…）
├── data/
│   ├── GdustApi.kt             # ★ 所有 HTTP 协议（CAS/门户/教务/水电/LLM/SSE）
│   ├── Repository.kt           # ★ Store（全部本地存储）+ Repository 门面
│   ├── TeachingSsoClient.kt    # ticket → 正方教务会话（纯 OkHttp）
│   ├── PersistentCookieJar.kt  # 教务 Cookie 持久化（按 host）
│   ├── ZfScheduleParser.kt     # 正方 kbList 解析（zcd 真实周次）
│   └── SessionPolicy.kt        # 会话失效判定（只有明确拒绝才清登录）
├── ui/
│   ├── AppRoot.kt              # ★ 全局路由（Crossfade when 分支）+ 底栏
│   ├── Design.kt               # Ds 设计令牌 + GlassCard
│   ├── Wallpaper.kt            # 壁纸（模糊/压暗/降采样）
│   ├── schedule/               # 课表网格、翻周手势、课程详情面板
│   └── screens/                # 登录/今日/电费/待办/设置/通知/提醒/导入
│       ├── SettingsScreen.kt   # ★ 设置主页面（分组入口都在这里注册）
│       ├── SettingSubPages.kt  # 二级页骨架 SettingSubPage（复用！）
│       ├── NoticeScreen.kt     # 学校通知（公告）
│       └── ReminderScreen.kt   # 课程提醒（课前/次日/课中/超级岛/权限）
├── work/
│   ├── PowerWorker.kt          # 水电预警（15 分钟）
│   ├── ScheduleSyncWorker.kt   # 课表自动同步（12 小时）
│   └── LessonReminderWorker.kt # 课程提醒巡检（15 分钟）
└── widget/                     # 桌面小组件 ×3（Glance）
```

### 1.3 测试与资源

| 用途 | 路径 |
|---|---|
| 单元测试 | `app\src\test\java\com\menglimi\gdust\v2\data\`（`ZfScheduleParserTest`、`SessionPolicyTest`） |
| 抓包存档（学习通，已有） | `D:\ai\mooc2-ans.chaoxing.com.har` |
| 抓包存档（WebVPN，已有） | `D:\ai\webvpn.gdust.edu.cn.har` |
| 签名（**不进 git**） | `signing\gdust-v2-release.jks`、`signing\keystore.properties` |
| 模拟器 | `emulator-5554`（AVD `farm_api36`）；真机无线调试 `192.168.1.104:36125` |

---

## 二、分层约定（扩展必须遵守）

```
ui/screens/*  ──(只调)──▶  data/Repository  ──(只调)──▶  data/*Api / *Client
                              │
                              ▼
                         data/Store（加密/普通 prefs 都在这里）
```

- **UI 不直接发网络请求**，一律通过 `Repository` 的方法
- **新增存储字段写在 `Store`**（加密用 `secure`，普通用 `plain`），不要各页面自己开 prefs
- **新增页面 = 新文件 + AppRoot 注册路由 + SettingsScreen 注册入口**
- 新增 Worker 要在 `App.kt` 里按开关注册

---

## 三、学习通扩展（重点）

### 3.1 现状与缺口

| 项 | 状态 |
|---|---|
| 已有 HAR `D:\ai\mooc2-ans.chaoxing.com.har` | ✅ 85 请求，但**只有** `stu-job-info`（每班作业完成率：jobCount/jobFinishCount） |
| 作业列表（名称/截止时间） | ❌ 缺接口，需补 HAR |
| 考试列表 | ❌ 缺接口 |
| 学习通通知 | ❌ 缺接口 |

### 3.2 三条可行路线

**① WebView 登录 + Cookie 复用（推荐，与教务网页登录同一套模式）**

```
新建 data/ChxApi.kt：
  1) WebView 打开 https://i.chaoxing.com/base 登录
  2) CookieManager.getCookie("https://mooc2-ans.chaoxing.com")
  3) OkHttp 带 Cookie 拉课程列表 → 逐课拉作业/考试
  4) Jsoup 解析 HTML（作业列表是服务端渲染）→ 产出 Todo 条目
```

落地文件建议：
- `data/ChxApi.kt`（协议 + 解析）
- `data/ChxSession.kt`（cookie 持久化，仿 `PersistentCookieJar`）
- `ui/screens/ChxLoginScreen.kt`（WebView 登录页，可参考 `TermImportScreen`）
- 结果写入待办：`Store.saveTodos()` 追加（待办页已有 UI）

**② 通知监听（最快，不碰账号）**

- 新建 `service/ChxNotificationListener.kt : NotificationListenerService`
- 用户在系统设置授予「通知使用权」
- 学习通推送作业提醒时解析标题 → 写待办
- 需在 `AndroidManifest.xml` 注册 `<service android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">`

**③ 手机端 API 逆向（不建议）**：`mooc1-api.chaoxing.com` 有设备指纹/`_enc` 风控，易被判异常登录。

### 3.3 需要补的 HAR（给数据契约用）

抓法：
1. 浏览器登录学习通 → 某门课 → **作业列表页**
2. 点开一个作业详情（看截止时间来源）
3. F12 → Network → 右键 → *Save all as HAR with content*
4. 存到 `D:\ai\chaoxing-work.chaoxing.com.har`

需要确认的字段：作业名、截止时间、状态（未交/已交/已批阅）、课程名、作业 id。

---

## 四、教务系统可扩展接口（门户已存在，前端 index.js 清单）

以下接口在门户前端出现过，**尚未接入**，可直接试：

| 接口 | 用途 | 现状 |
|---|---|---|
| `app/zf/get_school_score` | 成绩查询 | 未接 |
| `app/zf/get_empty_classroom` | 空闲教室 | 未接 |
| `app/zf/get_site_info` | 站点信息 | 未接 |
| `app/zf/get_site_course` | 课程表（站点） | 未接 |
| `app/zf/is_open_subscription_school_score` | 成绩订阅开关 | 未接 |

接入方式：在 `GdustApi.kt` 加方法（用 `str()/num()` 安全取值）→ `Repository` 暴露 → 新页面。

注意：`get_student_course` 的 `week` 参数**实测无效**（只返回当前周），全学期必须走正方 `kbList`。

---

## 五、新增功能的落地清单（模板）

以「成绩查询」为例：

1. `GdustApi.kt` → `suspend fun scores(token, job): List<Score>`
2. `GdustApi.kt` 加数据类 `data class Score(...)`
3. `Repository.kt` → `suspend fun scores() = api.scores(store.portalToken, store.jobNumber)`
4. `ui/screens/ScoreScreen.kt` → 用 `SettingSubPage("成绩", "…", onBack) { … }`
5. `SettingsScreen.kt` → 在「数据管理」组加 `SettingOption(title="成绩查询", onClick = onScores)`
6. `AppRoot.kt` → `var showScores` + Crossfade 分支 + `BackHandler { showScores = false }`
7. 加单测（如解析逻辑）→ 构建 → 装模拟器验

---

## 六、构建与验收

```bash
# 编译
D:/android-sdk/gradle-dist/gradle-9.7.1/bin/gradle.bat -p D:/ai-projects/gdust-v2 compileReleaseKotlin --console=plain

# 单测 + 正式包
D:/android-sdk/gradle-dist/gradle-9.7.1/bin/gradle.bat -p D:/ai-projects/gdust-v2 testDebugUnitTest assembleRelease --console=plain

# 装模拟器（真机用 -s 192.168.1.104:36125）
D:/android-sdk/platform-tools/adb.exe -s emulator-5554 install -r D:/ai-projects/gdust-v2/app/build/outputs/apk/release/app-release.apk

# 复制交付
cp app/build/outputs/apk/release/app-release.apk "/d/广科课程表-v2-release.apk"
```

**版本标识**：导入页标题显示 `v2.0.0-alpha(N)`；改功能时把 `app/build.gradle.kts` 的 `versionCode` 递增，便于确认装的哪版。

---

## 七、红线（必须遵守）

1. **密码不落盘**：只存 token / ticket / cookie；`keystore.properties` 与 `*.jks` 不入 git
2. **不开源**：仓库已转私有，不要 push 到公开仓库
3. **不猜接口**：没有 HAR/实测前不要凭空写端点参数，宁可先问
4. **不伪造数据**：拿不到全学期就明说「未获取」，不回填假课程
5. **不留英文报错**：所有失败路径走中文可操作提示（参考 `localizeImportError`）
