# gdust-v2 — 进度卡

> 最后更新：2026-11-13（双线分工定稿）。下次开工先读这张卡。

## 双线分工（2026-11-13 定稿）

两条任务线各管各的，开工先对号入座；**合并动作同一时刻只由一个会话执行**。

### 板块一：课表 + 电费（功能已就绪，只做 UI）—— 本卡所在线
- 分支 `feat/nexio-ui-170`（主仓 worktree），**验收设备：真机 192.168.1.104:36125**
- A1 合并前验收：四个 tab 逐个点一遍（今日已实测，其余三页只做过单页渲染核对）
- A2 快进合并 `feat/nexio-ui-170` → main，合并后 `testDebugUnitTest` 全绿
- A3 release 包真机过时间线观感（1.5dp 红线粗细）
- 纪律：只动 `ui/` 与外观资源，不碰 `data/` 业务逻辑

### 板块二：学习通 × 教务互通（研究线）—— 在 `../gdust-v2-academic` 执行
- 分支 `feat/academic-chaoxing`，**验收设备：emulator-5554（debug 包将迁至 `com.menglimi.gdust.v2.research`，见协调规则）**
- B1 登录互通已收口（真实账号验证通过，见研究线卡）；遗留：教务入口校园网/WebVPN 复测、学校服务恢复后核对学习通首页「我学的课」
- B2 等用户抓包 `D:\ai\chaoxing-work.har` + `D:\ai\chaoxing-exam.har` → `ChxApi` Jsoup 解析（EXTENDING.md 3.2① 路线）
- B3 待办模型扩 `due` 字段（兼容旧数据），作业/考试写入待办（去重、可更新）
- B4 板块一合并 main 后，本线向 main 对齐（预计 AppRoot/Manifest/PROGRESS.md 冲突）
- 纪律：只动 `data/Chx*`、`TeachingSsoClient`、`service/` 与本线新页面，不改共享 UI 版式

### 协调规则
- **包名分工（2026-11-13 补，修模拟器互覆盖）**：模拟器上两个 debug 包错开 applicationId——**本线独占 `com.menglimi.gdust.v2.debug`**；研究线下一次提交起改用 `com.menglimi.gdust.v2.research`，落地前研究线不往模拟器装包。设备上现存 `.debug` v8 是研究线早期构建，本线安装若遇 downgrade 拒绝，直接 `adb uninstall com.menglimi.gdust.v2.debug` 再装（WorkManager 会自行重建）
- **设备分工**：本线验收优先真机 192.168.1.104:36125；自动化验收前仍要确认 `topResumedActivity` 是自己的包
- **合并顺序**：板块一先合 main，板块二随后对齐；合并动作同一时刻只由一个会话执行
- 用户侧唯一依赖：作业详情页抓包 + 考试清单 HAR（清单见 `docs/EXTENDING.md` §3.4）

## 现在做到哪：Nexio 1.7 全量页面移植已完成（分支待合并）

分支 `feat/nexio-ui-170`，提交 `4b62167`（基于 `857e1fd`）。四页（今日/课表/电费/待办）+ 设置 + 登录已全部换成 Nexio `28a99a3` 版式；底栏重写为独立玻璃胶囊 + 56dp 设置圆钮；主题统一 Miuix（SOFT=磨砂/LIQUID=折射两种材质保留）。42/42 单测通过，debug+release 构建通过。

**合并前建议**：在空场模拟器或真机把四个 tab 逐个点一遍（见下方坑——模拟器被研究线会话共用，批量点验被打断，今日 tab 已实测、其余三页只做了单页渲染核对）。合并命令：`git checkout main && git merge feat/nexio-ui-170`。

## 模拟器共享冲突（重要坑，本次实测踩中）

- **同一台 medium_phone 模拟器（emulator-5554）被本仓另一条「教务×学习通研究线」worktree 的会话共用**：它装的 debug 包（applicationId `com.menglimi.gdust.v2.debug`）来自含 `ChxWebActivity` 的分支（`e781091`/`7a5aa01`/`c0047e4`，这些提交不在 main 线上）
- 实测它会在你测试中途把它的 Activity 拉到前台（ATM 日志可见 uid 10149 反复 START ChxWebActivity），还会让 System UI 弹 ANR、adb 掉 offline
- 规约：**自动化 UI 验收前先 `dumpsys activity activities | grep topResumedActivity` 确认前台是自己刚 start 的那个包**；每次 `input tap` 前后都查；发现串台就停，别把别的会话的画面当成自己的验收结果
- 之前进度卡里写的「模拟器会自己弹钱迹/haooz」大概率也是同类共享冲突
- **→ 已定解决方案（2026-11-13，见上方「协调规则」）**：研究线 debug 包迁 `.research` 后缀后两包共存互不覆盖；落地前研究线不再往模拟器装 `.debug` 包；本线遇 downgrade 拒绝就先 `adb uninstall com.menglimi.gdust.v2.debug` 再装

## 解包/研究资产

- 精确版源码：`D:/projects/refs/NexioSchedule-28a99a3-source-nbhbV4`（=APK revision 28a99a3，**以此为准**）
- 旧参考检出：`D:/projects/refs/NexioSchedule`（c540e9a，仅组件底座对照）
- 反编译：`D:/projects/reverse/nexio-170-1113-session/`（input/nexio.apk、classes.txt、output2/）
- 移植映射：今日/课表/待办/设置/底栏各自的来源文件写在对应 .kt 文件头注释里

## 课表玻璃材质（2026-11-13 补）

- 用户反馈「过于扁平、右上角不明显」：课程卡改走 Nexio 玻璃材质路径（着色 12% + 底色 34% + blur Mirror 透壁纸），顶栏两个圆钮改玻璃+描边光，右上菜单从 WindowListPopup（弹层锚点错位）换成原地玻璃 Popup
- **大坑（实测两次 SIGSEGV）**：drawBackdrop 的采样节点**不能在被录制的层内部**——卡片在页面层里采样页面层 = GPU 自采样反馈回路，RenderThread 段错误（swiftshader/真机都有风险）。正确结构：**采样层只录壁纸 Box**，页面内容不录制；底栏在层外采样才安全
- 判据：崩溃特征 = RenderThread SIGSEGV SEGV_ACCERR、同一 fault addr 段基址、launch 即崩
- 模拟器共享冲突升级：对方会话在测试钱迹记账（com.xiaojinzi.tally），截图/点击随时被抢，交互验收只能抽空档做

## 超级岛通知（2026-11-13 补）

- 用户反馈无效。搜了官方文档（dev.mi.com pId=2131/2140/2146）+ HyperNotification 库源码（已克隆 D:/projects/refs/HyperNotification）：
  - 旧实现 JSON 只有 ticker+isShowNotification，缺 **business/aodTitle/param_island 三个必填字段**，且 protocol:1 只是 OS1 焦点通知，不会上岛 → 系统直接忽略
  - 新实现：手拼标准 V3 param_v2（enableFloat/ticker/aodTitle/business/baseInfo/hintInfo/param_island{islandProperty,islandTimeout,bigIslandArea{imageTextInfoLeft/Right{textInfo}}}}），字段名对齐 HyperNotification 的 @Serializable 模板
  - 想引 HyperNotification 库（com.xzakota.hyper.notification:focus-api）但它 **Central/JitPack 都没发布**（403/空），只能 vendor；嫌重就手拼了（Gson 拼 LinkedHashMap）
- **另一半原因在用户侧**：HyperOS 有焦点通知白名单；用户用 HyperCeiler 绕，但其截图显示 HyperCeiler 对 com.miui.securitycenter 已进安全模式（功能停用）→ 必须先解除安全模式并重新加白，通知发得再对也没用
- 设置 → 课程提醒 → 超级岛 加了诊断行：焦点协议 v0-3（Settings.System notification_focus_protocol）+ persist.sys.feature.island；v3 且白名单通过才会上岛
- 真机验证路径：修复 HyperCeiler → 设置里点「测试提醒」→ 看岛/

## 许可状态（2026-11-13 更新）

- 应作者与上游（Nexio 作者）的沟通结论，**项目先闭源自用**：LICENSE 换成闭源声明、README/关于页改回「闭源自用」
- 代码文件头**保留**「Ported from NexioSchedule 28a99a3」出处注释（SPDX AGPL 标识行已删）；NOTICE.md 记录上游许可与授权状态
- 若日后要对外分发：先拿上游书面豁免，或整体转回 AGPL-3.0 合规（NOTICE.md 里有现成说明）
- 仓库远程仍是 PRIVATE

## 远程备份（已解决）

- `origin` = `https://github.com/suiren0219/gdust-v2`，**PRIVATE**，48+ 提交已全部推上去
- 以后每次提交记得 `git push`（或让我推）
- 推送前审计过：历史里从未出现过 `*.jks` / `keystore.properties`；仓库 765KB
- `.gitignore` 已加 `*.har`（抓包带 Cookie/uid/token，禁止入库）
- gh CLI 位置：`D:\机器人插件\gh-cli\Program Files\GitHub CLI\gh.exe`（账号 suiren0219，keyring 登录）

## 现在做到哪

课表周网格的**「当前时间线 + 今日/进行中/已上过」三态**已完成并提交：

```
0533195 课表周网格加当前时间线与三态卡片：红线按节次插值+左端圆点，已上过/上课中/未开始三态配色，替换原右下角黄点
```

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

> 分工定稿（2026-11-13）：**本线只做第 2 条**（即 A3 真机观感验收）；第 1、3 条归板块二研究线，在 `../gdust-v2-academic` 执行。

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
