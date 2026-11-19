# CZeroX 界面规范

App 和课表、电量、待办三类九款桌面小部件使用同一套实底卡片语言。优先可读性，再保证页面间的一致性。

## 色彩与排版

| 用途 | 浅色 | 深色 |
| --- | --- | --- |
| 页面背景 | `#F7F7F7` | `#0E0E10` |
| 卡片 | `#FFFFFF` | `#1C1C1E` |
| 主文字 | `#000000` | `#F2F2F7` |
| 辅助文字 | `#666666` | 白色 62% |
| 交互强调 | `#3482FF` | `#7AB0FF` |
| 品牌露出 | `#106DFD` | `#4D8BFF` |
| 危险状态 | `#EE4444` | `#FF6B6B` |

- 页面边距 16dp，卡片间距 12dp，卡内边距 20dp。
- App 卡片圆角 26dp，小部件 24dp。课表密集网格采用较小圆角，保留字号、行高和对齐设置。
- 主标题 34sp / Medium，卡标题 17–18sp / Semibold，数字 32–40sp / Bold，辅助文字通常 13sp，小部件最低 11sp。
- 主文字与数字优先黑白；颜色只表达状态和交互。绿色状态卡独立承载内容，不再嵌套白卡。
- 所有卡面不透明，无玻璃采样、模糊、扫光、阴影、装饰性描边。分层依靠底色和留白。
- 数值为 0 时正常显示，缺失数据用占位或明确原因；未导入课程与真正无课分开表达。
- 不强制锁定系统字号；课表行高、时间列与日期行随字号增加。日列宽度不足时自动改用可选日期的日程卡，保留周切换和课程详情。

## 代码入口

- `ui/Design.kt`：`Ds` 色板与 Material 排版。
- `ui/Themes.kt`：Miuix 与 Material 主题统一，关闭 Miuix 玻璃能力。
- `ui/AppSurfaces.kt`：`AppUiTokens`、实底 `AppCard` 和按下 tint。
- `ui/screens/Components.kt`：页头、设置列表、分段选择、可滚动说明弹窗。
- `ui/nexio/NexioBottomBar.kt`：带文字标签的实底浮动导航。
- `widget/WidgetStyle.kt`：桌面实底背景、文字色与趋势图。
- `res/values/widget_colors.xml` 与 `res/values-night/widget_colors.xml`：系统小部件选择器日夜色。

旧备份的外观字段继续由数据层兼容；页面不再读取它们切换材质。`GlassCard` 和 `WidgetStyle.glassCard` 只保留内部调用名称，实际均为不透明实底。Miuix vendor 源码保持不变。

## 本地验证

构建与单测使用本机 Gradle：

```bash
D:/android-sdk/gradle-dist/gradle-9.7.1/bin/gradle.bat -p D:/projects/gdust/gdust-v2 :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
```

`CZeroContrastTest` 覆盖日夜模式的主文字、辅助文字、状态卡对比度与卡面不透明性。业务回归继续运行原有课表、会话、成绩、待办和小部件测试。

Debug 包提供 `CZeroPreviewActivity`，合成数据使用 `czero_preview_` 前缀偏好存储，与账号、课表、待办和电量真实缓存隔离。仅在 debug 清单注册，Release 不包含验收活动。可查看 `today/schedule/power/todo/history/widgets/settings/profile/about`；电量页使用未绑定态，不写假账号或发起绑定。

```bash
adb -s emulator-5554 shell am start -f 0x24000000 -n com.menglimi.gdust.v2.research/com.menglimi.gdust.v2.debug.CZeroPreviewActivity --es screen today
```

`widget_render` 使用真实 Glance `compose()` 生成 RemoteViews，供外观检查，不触发生产 PendingIntent：

```bash
adb -s emulator-5554 shell am start -f 0x24000000 -n com.menglimi.gdust.v2.research/com.menglimi.gdust.v2.debug.CZeroPreviewActivity --es screen widget_render --es kind power --es shape medium
```

`kind` 为 `schedule/power/todo`，`shape` 为 `wide/medium/tall`。共享模拟器验收前应先协调使用时段，并确认截图前台包名。
