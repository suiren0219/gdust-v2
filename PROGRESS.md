# gdust-v2 — 进度卡

> 最后更新：2026-11-13（双线分工定稿）。下次开工先读这张卡。

## 双线分工（2026-11-13 定稿）

两条任务线各管各的，开工先对号入座；**合并动作同一时刻只由一个会话执行**。

### 板块一：课表 + 电费（功能已就绪，只做 UI）—— 在主仓 worktree 执行
- 分支 `feat/nexio-ui-170`，**验收设备：真机 192.168.1.104:36125**
- A1 合并前验收：四个 tab 逐个点一遍（今日已实测，其余三页只做过单页渲染核对）
- A2 快进合并 `feat/nexio-ui-170` → main，合并后 `testDebugUnitTest` 全绿
- A3 release 包真机过时间线观感（1.5dp 红线粗细）
- 纪律：只动 `ui/` 与外观资源，不碰 `data/` 业务逻辑

### 板块二：学习通 × 教务互通（研究线）—— 本卡所在线
- 分支 `feat/academic-chaoxing`，**验收设备：emulator-5554（debug 包将迁至 `com.menglimi.gdust.v2.research`，见协调规则）**
- B0 **✅ 已落地（2026-11-13）**：applicationIdSuffix `.debug` → `.research`；已 `adb uninstall com.menglimi.gdust.v2.debug`、装上 `com.menglimi.gdust.v2.research`，POST_NOTIFICATIONS 与通知使用权（enabled_notification_listeners）均已重新授权；**重新扫码登录待学校网络恢复后做**（Cookie 不随包迁移）。**落地后不要再往模拟器装 `.debug` 包**
- B1 登录互通已收口（真实账号验证通过，见下文「研究线」一节）；遗留：教务入口校园网/WebVPN 复测、学校服务恢复后核对学习通首页「我学的课」
- B2 等用户抓包 `D:\ai\chaoxing-work.har` + `D:\ai\chaoxing-exam.har` → `ChxApi` Jsoup 解析（EXTENDING.md 3.2① 路线）
- B3 待办模型扩 `due` 字段（兼容旧数据），作业/考试写入待办（去重、可更新）
- B4 板块一合并 main 后，本线向 main 对齐（预计 AppRoot/Manifest/PROGRESS.md 冲突）
- 纪律：只动 `data/Chx*`、`TeachingSsoClient`、`service/` 与本线新页面，不改共享 UI 版式

### 协调规则
- **包名分工（2026-11-13 补，修模拟器互覆盖）**：UI 线独占 `com.menglimi.gdust.v2.debug`，研究线用 `com.menglimi.gdust.v2.research`（B0 落地）；两个包共存后互不覆盖、WorkManager 各自独立
- **设备分工**：UI 线验收优先真机 192.168.1.104:36125；研究线用 emulator-5554；自动化验收前仍要确认 `topResumedActivity` 是自己的包
- **合并顺序**：板块一先合 main，板块二随后对齐；合并动作同一时刻只由一个会话执行；B4 合并时 suffix 是否回 `.debug` 由合并会话一行改动定夺
- 用户侧唯一依赖：作业 HAR 已到位（阶段 2 代码完成）；还差**点击作业详情页的抓包**（绝对截止时间）与考试清单 HAR（清单见 `docs/EXTENDING.md` §3.4）

## 远程备份（已解决）

- `origin` = `https://github.com/suiren0219/gdust-v2`，**PRIVATE**，48+ 提交已全部推上去
- 以后每次提交记得 `git push`（或让我推）
- 推送前审计过：历史里从未出现过 `*.jks` / `keystore.properties`；仓库 765KB
- `.gitignore` 已加 `*.har`（抓包带 Cookie/uid/token，禁止入库）
- gh CLI 位置：`D:\机器人插件\gh-cli\Program Files\GitHub CLI\gh.exe`（账号 suiren0219，keyring 登录）

## 研究线「教务 × 学习通互通」（本次新增）

- 分支 `feat/academic-chaoxing`，worktree `D:\projects\gdust\gdust-v2-academic`（已推 origin）
- **阶段 1 登录互通：✅ 收口**（2026-11-12 夜，medium_phone/API35 + 真实账号扫码；2026-11-13 用户确认学号）
  - 学习通：CAS 扫码 → fysso(HTTP) → fanya(HTTP) → passport2(HTTP) → 学习通；App 显示「已联动」
  - **身份已确认**：SSO 跳转带回 `uname=2025XXXXXXXXXX`，用户确认是本人学号
  - Cookies：`.chaoxing.com` 域 `UID/_uid/_d/fid/uname/p_auth_token`（7–30 天）+ 子域 `JSESSIONID`(session)
  - 教务：同一 CAS 会话为 `gkjx.gdust.edu.cn` **签发 ticket（免密双通成立）**；
    但该入口从测试网络 `ERR_CONNECTION_TIMED_OUT`（宿主机 curl 同样超时）——环境问题，不是 App 缺陷
  - 退出：`clearChxSession()` 清本机存储 + 按域过期超星/CAS Cookie + CAS 登出页清 localStorage；
    实测退出后 Cookies 0 条、重开「学习通」回到 CAS 登录页（不自动登录）
  - 会话失效 vs 网络故障：`TeachingSsoClient.probe` / `ChxApi.probe` 均三态
    （true 有效 / false 明确被拒 / null 网络原因，不判死不清会话）；
    `chxSessionAlive` 探活=false 复查一次再清，防文案启发式误删
  - ⚠️ 遗留：学校服务器自测试网络间歇性不可达（cas/gkjx 443 均超时）；
    恢复后补两件事——①打开「学习通首页」核对「我学的课」与头像姓名 ②校园网/WebVPN 下复测教务网页
- **阶段 2 作业接入：代码完成（2026-11-13），端点/结构全部来自 HAR 实测，单测 6 例全绿**
  - 链路：`courselistdata`(POST,固定表单) → `stucoursemiddle` 跟随 302 到课程页 →
    隐藏域取 `enc/workEnc/examEnc/t/moocDomainName` → `mooc2/work/list?…&stuenc&enc` → 解析 `<li data="work/task?…">`
  - `Repository.chxSyncWorkTodos()`：待办 id=workId（与手动待办不冲突）；重复同步只更新；
    手动勾选保持；chaoxing 已完成→置 done；`Todo` 新增 `due`（列表页只有相对剩余时间，
    绝对截止在 work/task 详情页——待补一份点击详情的抓包后精确化）
  - 入口：学习通联动页「同步学习通作业到待办」；**端到端待登录态复测**（学校服务器恢复后）
- **两个必须记住的坑**
  1. **WebView 不能放 Compose `AndroidView`**：页面的 `100vh/100dvh` 会恒为 0，CAS（Tailwind `min-h-screen`）
     整页白屏；裸 Activity 正常。登录页已迁到 `ChxWebActivity` / `TeachingWebActivity`；
     最小复现探针在 debug 源集 `WebViewProbeActivity`（`--es mode compose-box`）
  2. **超星登录链走明文 HTTP**：`network_security_config` 需放行
     `fysso/fanya/passport/passport2/mooc1/mooc1-api.chaoxing.com`，否则后两跳被 cleartext 拦
- 已做的小工具：debug 包开 WebView 远程调试（CDP），`ChxWeb`/`TeachWeb` 日志转发 console/网络/SSL 错误

## 现在做到哪

课表周网格的**「当前时间线 + 今日/进行中/已上过」三态**已完成并提交：

```
0533195 课表周网格加当前时间线与三态卡片：红线按节次插值+左端圆点，已上过/上课中/未开始三态配色，替换原右下角黄点
```

> 备注：模拟器里旧的 release 包（`com.menglimi.gdust.v2`）会抢前台，干扰自动化；
> 已在测试机 `pm disable-user` 停用，验收时先确认 `topResumedActivity` 是 `.debug`。


验收方式（模拟器像素级核对，本次实测通过）：
- 线位：15:00 / 15:01 / 15:03 三个时刻截图，红线 y 与节次插值期望差 ≤1px
- 非本周不画线（第 2 周截图 0 条红线）；午休 12:5x 不画线（设计如此）
- 三态文字色实测：已上过 (81,81,83) 灰 / 上课中 (171,71,188) 课程色 / 未开始 (230,180,34) 课程色
- 上课中描边 = tint@0.9，底 = tint@0.30；单测 `testDebugUnitTest` 全绿

模拟器上还留了一份**假课表种子数据**（高等数学/大学英语/数据结构，周四 3-4/5-6/9-10 节）方便人肉看效果：
- 种子文件：`C:/Users/MECHREVO/AppData/Local/Temp/gdust_cache_seed.xml`（已在 `adb root` 下写入 release 包 `shared_prefs/gdust_cache.xml`）
- 清掉：`adb root` 后删 `/data/data/com.menglimi.gdust.v2/shared_prefs/gdust_cache.xml` 再 force-stop 重开（原文件只有 start_tab/ui_style/wallpaper_path/smart_hide_weekend 四项）
- 注意模拟器会**自己弹别的 App 抢前台**（钱迹 lite、com.haooz.chedule.gdust），自动化测量前必须 `dumpsys activity activities | grep topResumedActivity` 确认是 gdust

## 上一次提交（本次）

```
0533195 课表周网格加当前时间线与三态卡片
```

再往前两条值得一提：
- `5738b6d` 学习通联动：复用学校 CAS 单点，一个 WebView 通吃学习通与教务网页
- `d9c41e9` 修「设置项点了不刷新」根因（直读 SharedPreferences 不是 Composition 状态）

## 下一步

0. **B0【立即执行】debug 包改造**：applicationIdSuffix `.debug` → `.research`（与正式包/历史 .debug 数据彻底隔离）；
   装新包前 uninstall 旧 `com.menglimi.gdust.v2.debug`；换包后重新授权通知使用权（NotificationListener）+ POST_NOTIFICATIONS + 重新扫码登录。
   **落地前不再往模拟器装任何 .debug 包。**
   研究线（feat/academic-chaoxing）：
   > **进度更新 2026-11-13**：下列前 3 条已在 `38463d4` 落地（退出联动彻底化 / probe 三态化 / 探活=false 复查一次再清）；剩第 4 条教务入口复测，待校园网/WebVPN 环境。
   - 「退出联动」目前只清本机 Cookie，**不清 WebView 的 Cookie/CAS TGC**（要做彻底退出）
   - `TeachingSsoClient.probe()` 仍是 Boolean，网络故障与失效不可区分（要改三态）
   - `chxSessionAlive()` 探活=false 时直接清 Cookie，启发式误判会误删会话（收紧后再清）
   - 教务入口可达性：`gkjx.gdust.edu.cn` 公网超时，需校园网 / WebVPN 复测
1. **等用户给抓包**（清单已写进 `docs/EXTENDING.md` §3.4）：
   - `D:\ai\chaoxing-work.har` —— 作业清单（≥3 门课、含未交/已交/已过期各一条、点开一条详情）
   - `D:\ai\chaoxing-exam.har` —— 考试清单（Web 无入口则手机端 Reqable 抓）
   - 拿到后走 3.2 ① 路线：`ChxApi` 加列表解析（Jsoup）+ 写入待办
2. 真机（192.168.1.104:36125）过一遍新时间线观感，看 1.5dp 红线在真机密度下粗细是否合适
3. 待办模型目前只有 `text/done`，接作业时大概率要扩 `due`（截止时间）字段，注意兼容旧数据

## 坑

- `local.properties` 指向 `D:\android-sdk`，那个目录不能动
- `app/build` 已占 ~360M，构建前如果空间紧张可以先清
- 本会话模型看不了图：截图验收只能走「uiautomator 坐标 + PIL 像素分析」或丢给人看（云手机 `http://localhost:8001`，服务 WsScrcpyWeb 是停的，`net start` 需要管理员权限）
- 模拟器 clock 用 `date -s` 改过后**不会自动回同步**，测试完记得恢复（宿主 UTC 时间，设备时区是 GMT）
- 模拟器会自己弹别的 App 抢前台（钱迹 lite、com.haooz.chedule.gdust），自动化测量前先确认 `topResumedActivity` 是 gdust

## 相关

- 同线另两套实现：`../gdust-app`（12 提交）、`../gdust-app-alt-9commits`（9 提交，历史与本仓完全独立）
- 草稿：`../gdust-app-nexio`
