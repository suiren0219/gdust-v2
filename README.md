# 广科课程表 v2

广东科技学院专属安卓 App。**彻底重写**：不继承 Nexio 一行源码，仅复刻其视觉语言（周网格课表 / 玻璃卡 / 悬浮胶囊底栏 / 深浅色）。

## 架构原则

- **单模块纯 Compose**：无 KMP、无 SQLDelight、无 Miuix fork、无 pager 魔改、无 alpha 门控
- **本机直连**：CAS / 门户 / 完美校园全部 App 直连，不经过任何自建服务器
- **隐私**：密码不落盘；TOKEN / 水电账号 / LLM Key 存 EncryptedSharedPreferences
- **切页**：Crossfade 220ms 统一过渡，无硬切、无拆树重建
- **课表**：门户按「节」返回 → 数据层 `mergeLessons` 合并连堂 → 一张整卡渲染

## 功能

| 页 | 内容 |
|---|---|
| 今日 | 当天课程卡片、上课状态、最早一节课 |
| 课表 | 周网格、连堂整卡、翻周动画、当前节次指示、午休/晚休条、智能隐藏周末 |
| 电费 | 完美校园 SWAEServlet 绑定 + 剩余电量大字 + 15 分钟后台双预警（阈值/掉电速率、12h 冷却） |
| 待办 | 本地增删勾选持久化的待办清单 |
| 设置 | 账号、手动同步、LLM 自填配置、通知 AI 总结、预警阈值、退出登录 |
| 登录 | 学号+密码+验证码（自动换码）；钉钉一键登录（SSE） |

## 构建

```
cd D:/ai-projects/gdust-v2
JAVA_HOME="C:\Program Files\Java\jdk-21" D:/android-sdk/gradle-dist/gradle-9.7.1/bin/gradle.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（arm64 + x86_64 通用，纯 Compose 无 native 依赖）
