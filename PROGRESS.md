# gdust-v2 — 进度卡

> 最后更新：2026-11-12（深夜）。下次开工先读这张卡。

## 远程备份（已解决）

- `origin` = `https://github.com/suiren0219/gdust-v2`，**PRIVATE**，48+ 提交已全部推上去
- 以后每次提交记得 `git push`（或让我推）
- 推送前审计过：历史里从未出现过 `*.jks` / `keystore.properties`；仓库 765KB
- `.gitignore` 已加 `*.har`（抓包带 Cookie/uid/token，禁止入库）
- gh CLI 位置：`D:\机器人插件\gh-cli\Program Files\GitHub CLI\gh.exe`（账号 suiren0219，keyring 登录）

## 研究线「教务 × 学习通互通」（本次新增）

- 分支 `feat/academic-chaoxing`，worktree `D:\projects\gdust\gdust-v2-academic`（已推 origin）
- **阶段 1 登录互通：实测完成**（2026-11-12 夜，medium_phone/API35 + 真实账号扫码）
  - 学习通：CAS 扫码 → fysso(HTTP) → fanya(HTTP) → passport2(HTTP) → 课程页；App 显示「已联动」
  - Cookies：`.chaoxing.com` 域 `UID/_uid/_d/fid/uname/p_auth_token`（7–30 天）+ 子域 `JSESSIONID`(session)
  - 教务：同一 CAS 会话为 `gkjx.gdust.edu.cn` **签发 ticket（免密双通成立）**；
    但该入口当前从测试网络 `ERR_CONNECTION_TIMED_OUT`（宿主机 curl 也超时）——环境问题，不是 App 缺陷
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

0. 研究线（feat/academic-chaoxing）：
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
