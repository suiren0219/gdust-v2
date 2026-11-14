# 多会话工作协调（2026-11-16）

> 本文件是三个并行 ZCode 会话的分工约定，避免同一工作区互相覆盖。
> 入库时机由持有未提交改动的会话决定（可随下次提交一并入库，或任务结束后删除）。

## 当前状态（写就时快照）

- `main` 已合并 UI 线（`eca71e4`，versionCode 15）；`feat/nexio-ui-170` 已推送并收口。
- **main 工作区存在一份活跃的未提交改动**（8 改 4 新），属于「主线功能会话」：
  - 修改：`App.kt`、`MainActivity.kt`、`Repository.kt`、`TeachingGradeApi.kt`、`ScheduleScreen.kt`、`ChxWebActivity.kt`、`PowerScreen.kt`、`GdustWidgets.kt`
  - 新增：`PowerHistoryScreen.kt`、`WidgetActions.kt`、`WidgetRefresh.kt`、`WidgetRefreshWorker.kt`
  - 对应用户反馈的五项：课表多余节次、电量坐标与历史日历视图、成绩格式解析、学习通关联排查、待办/小部件刷新去重
- 任何会话动手前必须先 `git status` 确认工作区归属，**绝不 stash / checkout / commit 他人的未提交改动**。

## 分工与禁区

| 会话 | 负责 | 禁区（他人持有改动，改动合入前不碰） |
|---|---|---|
| **主线功能会话**（main 工作区，正在跑） | 上述五项反馈的全部落地：课表节次、电量坐标+历史日历、成绩解析修复、学习通关联排查、小部件机制（WidgetRefresh/Actions） | 合入前不要把范围扩到「公告三来源」与「钉钉扫码」（nexio-ui 线刚交付，先在线验证再改） |
| **视觉会话**（原 gdust-app 标签） | 九款小部件的**视觉规范**：布局尺寸、文字对比度色值、玻璃底与图表反馈参数 | **不要再另写一份 GdustWidgets/WidgetRefresh 实现**——机制与落地统一由主线会话执行，视觉侧只输出规范（token 表或标注稿），避免双源分叉 |
| **nexio-ui 会话**（本会话） | 已交付领域（成绩/学习通课程/公告三来源/会话隔离/钉钉扫码）的**在线验收与问题收集**；跨线文档与合并计划 | 在主线会话提交前，禁碰：`Repository.kt`、`TeachingGradeApi.kt`、`ChxWebActivity.kt`、`widget/*`、`ScheduleScreen.kt`、`PowerScreen.kt`、`PowerHistoryScreen.kt` |

## 约定

1. main 分支上的直接提交需用户确认；功能开发尽量走短命分支后合并。
2. 视觉会话产出的规范落地时，由主线会话在同一份 `GdustWidgets.kt` 上执行，单一实现源。
3. nexio-ui 会话后续若需改动禁区文件，先确认主线会话已提交合入。
4. 用户反馈的问题优先在**持有相关文件改动**的会话里处理，不跨会话重复开工。
